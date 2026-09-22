package cn.omix.util.ai;

import cn.omix.util.node.NodePlatform;
import cn.omix.util.node.NodeRuntimeManager;
import im.webui.backend.BrowserPreparationProgress;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

public final class HarnessRuntime implements AutoCloseable {
    public enum State { STOPPED, PREPARING, STARTING, READY, FAILED }
    private static final Pattern READY = Pattern.compile("dsh web: (http://127\\.0\\.0\\.1:[0-9]+/\\?token=[^\\s]+)");
    private final Path root;
    private final Path workspace;
    private final NodeRuntimeManager node;
    private volatile State state = State.STOPPED;
    private volatile BrowserPreparationProgress progress = BrowserPreparationProgress.IDLE;
    private volatile Throwable failure;
    private volatile URI url;
    private CompletableFuture<URI> startup;
    private volatile long generation;
    private Process process;
    private MinecraftGameBridge bridge;
    private FileChannel ownershipChannel;
    private FileLock ownership;

    public HarnessRuntime(Path root, Path existingNodeCache, Path workspace) {
        this.root = root.toAbsolutePath().normalize();
        this.workspace = workspace.toAbsolutePath().normalize();
        node = new NodeRuntimeManager(existingNodeCache);
    }
    public State getState() { return state; }
    public Throwable getFailure() { return failure; }
    public BrowserPreparationProgress getProgress() { return progress; }
    public URI getUrl() {
        if (state != State.READY || url == null) throw new IllegalStateException("Harness is not ready");
        return url;
    }

    public synchronized CompletableFuture<URI> startAsync() {
        if (state == State.READY) return CompletableFuture.completedFuture(url);
        if (startup != null && !startup.isDone()) return startup;
        long attempt = ++generation;
        state = State.PREPARING;
        failure = null;
        startup = new CompletableFuture<>();
        CompletableFuture<URI> result = startup;
        Thread.ofVirtual().name("Omix-Harness-Start").start(() -> start(attempt, result));
        return result;
    }

    private void start(long attempt, CompletableFuture<URI> result) {
        try {
            Files.createDirectories(root);
            Files.createDirectories(workspace);
            synchronized (this) {
                checkAttempt(attempt);
                ownershipChannel = FileChannel.open(root.resolve("runtime.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                ownership = ownershipChannel.tryLock();
                if (ownership == null) throw new IOException("Another Omix process owns this AI data directory.");
            }
            Path executable = node.prepare(value -> { if (generation == attempt) progress = value; });
            progress = BrowserPreparationProgress.indeterminate("Extracting bundled DeepSeek Harness");
            Path runtime = HarnessBundle.prepare(root.resolve("runtime"), NodePlatform.current().id());
            Path home = root.resolve("home");
            Files.createDirectories(home);
            CompletableFuture<URI> ready = new CompletableFuture<>();
            Process launched;
            synchronized (this) {
                checkAttempt(attempt);
                bridge = cn.omix.Client.instance.getGameBridge();
                if (bridge == null) throw new IOException("Omix development bridge is unavailable");
                ProcessBuilder builder = new ProcessBuilder(executable.toString(), runtime.resolve("launch.mjs").toString());
                builder.directory(home.toFile());
                builder.redirectErrorStream(true);
                builder.environment().put("DSH_HOME", home.toString());
                builder.environment().put("OMIX_AI_WORKSPACE", workspace.toString());
                builder.environment().put("OMIX_AI_BRIDGE", bridge.endpoint());
                builder.environment().put("OMIX_AI_TOKEN", bridge.token());
                builder.environment().put("NODE_ENV", "production");
                process = launched = builder.start();
                state = State.STARTING;
                progress = BrowserPreparationProgress.indeterminate("Starting DeepSeek Harness");
            }
            Thread.ofVirtual().name("Omix-Harness-Output").start(() -> readOutput(launched, ready));
            URI endpoint = ready.get(90, TimeUnit.SECONDS);
            synchronized (this) {
                checkAttempt(attempt);
                if (!launched.isAlive()) throw new IOException("Harness exited during startup");
                url = endpoint;
                state = State.READY;
                progress = BrowserPreparationProgress.determinate("DeepSeek Harness ready", 1);
                result.complete(endpoint);
            }
            launched.onExit().thenRun(() -> fail(attempt, new IOException("Harness exited; use .ai restart to retry.")));
        } catch (Throwable error) {
            fail(attempt, error);
            result.completeExceptionally(error);
        }
    }

    private void readOutput(Process child, CompletableFuture<URI> ready) {
        HarnessStartupLog log = new HarnessStartupLog();
        try (var reader = new BufferedReader(new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null;) {
                log.append(line);
                var matcher = READY.matcher(line);
                if (matcher.find()) ready.complete(URI.create(matcher.group(1)));
            }
            ready.completeExceptionally(new IOException(log.summary() + "\nDetails: Omix/ai/harness.log"));
        } catch (IOException error) {
            log.append("IOException: " + error.getMessage());
            ready.completeExceptionally(error);
        } finally {
            try { Files.writeString(root.resolve("harness.log"), log.content(), StandardCharsets.UTF_8); }
            catch (IOException ignored) { }
        }
    }

    static String redact(String text) {
        return HarnessStartupLog.redact(text);
    }
    private synchronized void checkAttempt(long attempt) {
        if (attempt != generation) throw new CancellationException("Harness startup cancelled");
    }
    private synchronized void fail(long attempt, Throwable error) {
        if (attempt != generation) return;
        while (error.getCause() != null) error = error.getCause();
        cleanup();
        failure = new IOException(redact(error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
        state = State.FAILED;
        progress = BrowserPreparationProgress.indeterminate("Harness failed; .ai restart to retry");
    }

    public synchronized CompletableFuture<URI> restartAsync() {
        close();
        return startAsync();
    }
    @Override public synchronized void close() {
        generation++;
        if (startup != null) startup.completeExceptionally(new CancellationException("Harness stopped"));
        startup = null;
        cleanup();
        state = State.STOPPED;
        progress = BrowserPreparationProgress.IDLE;
    }
    private void cleanup() {
        bridge = null;
        if (process != null) {
            var descendants = process.descendants().toList();
            descendants.reversed().forEach(ProcessHandle::destroy);
            process.destroy();
            try { if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroyForcibly(); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); process.destroyForcibly(); }
            descendants.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
            process = null;
        }
        url = null;
        try { if (ownership != null) ownership.close(); } catch (IOException ignored) { }
        try { if (ownershipChannel != null) ownershipChannel.close(); } catch (IOException ignored) { }
        ownership = null;
        ownershipChannel = null;
    }
}
