package cn.omix.script;

import cn.omix.Client;
import cn.omix.script.api.*;
import cn.omix.util.script.*;
import cn.omix.module.Module;
import com.google.gson.*;
import net.minecraft.client.MinecraftClient;
import java.io.*;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Coordinates manual compilation and transactional main-thread registration replacement. */
public final class ScriptManager implements AutoCloseable {
    private record Running(String hash, long generation, ScriptContext context, Object instance, ScriptCompiler.Compiled compiled, URLClassLoader loader) {}
    public static final class Job {
        public final String id = UUID.randomUUID().toString();
        public final String script, action, sourceHash;
        public final long generation;
        public volatile String state = "queued", error;
        public volatile String phase = "queued";
        public volatile long startedAt, finishedAt;
        public volatile List<ScriptCompiler.Problem> diagnostics = List.of();
        public volatile JsonElement value;
        private transient long worldEpoch;
        private transient Object player;
        private Job(String script, String action, String sourceHash, long generation) { this.script = script; this.action = action; this.sourceHash = sourceHash; this.generation = generation; }
    }
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Path root;
    private final ScriptFiles files;
    private final ScriptCompiler compiler;
    private final ScriptLog log = new ScriptLog();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> Thread.ofPlatform().daemon().name("Omix-Script-Compiler").unstarted(r));
    private final Map<String, Running> running = new ConcurrentHashMap<>();
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final Map<String, Long> attempts = new ConcurrentHashMap<>();
    private final AtomicLong generations = new AtomicLong(System.currentTimeMillis());
    private volatile boolean closed;
    private volatile long worldEpoch;
    private Registration worldListener;
    public ScriptManager(Path root) throws IOException { this.root = root; files = new ScriptFiles(root); compiler = new ScriptCompiler(root.resolve(".cache")); }
    public ScriptFiles files() { return files; }
    public ScriptLog log() { return log; }
    public void start() {
        worldListener = Client.instance.getEventManager().subscribe(this, cn.omix.event.impl.WorldEvent.class, 0, event -> worldEpoch++);
        worker.execute(() -> { try { ScriptDistribution.export(root); } catch (Exception error) { log.add("system", 0, "ERROR", error, 0); } });
        Path manifest = root.resolve(".loaded.json");
        if (Files.isRegularFile(manifest)) {
            try { for (JsonElement id : JsonParser.parseString(Files.readString(manifest)).getAsJsonArray()) queue(files.read(id.getAsString()), "load", true); }
            catch (Exception error) { log.add("system", 0, "ERROR", "Restore scripts: " + error, 0); }
        }
    }
    public Job submit(String id, String action) throws IOException {
        if (!Set.of("check", "load", "reload").contains(action)) throw new IllegalArgumentException("Action must be check, load or reload");
        ScriptFiles.Source source = files.read(id);
        return queue(source, action);
    }
    public Job evaluate(String source) throws IOException {
        if (source.length() > 32768) throw new IllegalArgumentException("Evaluation exceeds 32 KiB");
        return queue(new ScriptFiles.Source("@evaluation", "Object evaluate() throws Exception {\n" + source + "\n}", ScriptFiles.hash(source)), "evaluate");
    }
    private Job queue(ScriptFiles.Source source, String action) { return queue(source, action, false); }
    private synchronized Job queue(ScriptFiles.Source source, String action, boolean startup) {
        if (closed) throw new IllegalStateException("Script service stopped");
        if (!action.equals("check")) jobs.values().stream()
                .filter(job -> job.script.equals(source.id()) && !job.action.equals("check"))
                .forEach(job -> cancel(job.id));
        if (!startup && jobs.values().stream().filter(job -> Set.of("queued", "compiling", "applying").contains(job.state)).count() >= 8)
            throw new IllegalStateException("Compiler queue is full; wait for a job to finish");
        if (jobs.size() >= 64) jobs.values().stream().filter(job -> !Set.of("queued", "compiling", "applying").contains(job.state))
                .min(Comparator.comparingLong(job -> job.generation)).ifPresent(job -> jobs.remove(job.id));
        Job job = new Job(source.id(), action, source.hash(), generations.incrementAndGet()); jobs.put(job.id, job);
        if (action.equals("evaluate")) { job.worldEpoch = worldEpoch; job.player = mc.player; }
        if (!action.equals("check")) attempts.put(source.id(), job.generation);
        worker.execute(() -> compile(job, source)); return job;
    }
    private void compile(Job job, ScriptFiles.Source source) {
        synchronized (job) {
            if (closed || job.state.equals("cancelled")) return;
            job.startedAt = System.currentTimeMillis(); job.state = "compiling";
        }
        try {
            ScriptCompiler.Compiled compiled = compiler.compile(job.script, job.generation, source.text(), job.action.equals("evaluate") ? 1 : 0,
                    () -> closed || job.state.equals("cancelled"), phase -> job.phase = phase);
            job.diagnostics = compiled.diagnostics();
            synchronized (job) {
                if (closed || job.state.equals("cancelled")) { compiled.discard(); return; }
                if (job.action.equals("check")) { job.finishedAt = System.currentTimeMillis(); job.state = "checked"; compiled.discard(); return; }
                job.phase = "waiting_client";
            }
            mc.execute(() -> apply(job, compiled));
        } catch (Throwable error) { fail(job, error); ScriptFailures.rethrowFatal(error); }
    }
    private void apply(Job job, ScriptCompiler.Compiled compiled) {
        synchronized (job) {
            if (closed || job.state.equals("cancelled") || !Objects.equals(attempts.get(job.script), job.generation)) { job.player = null; job.finishedAt = System.currentTimeMillis(); job.state = "cancelled"; compiled.discard(); return; }
            job.phase = "applying"; job.state = "applying";
        }
        ScriptContext next = null; URLClassLoader loader = null; Object instance = null;
        Running old = running.get(job.script); Map<String, JsonObject> state = old == null ? Map.of() : snapshot(old.context);
        boolean detached = false;
        try {
            if (!job.action.equals("evaluate") && !files.read(job.script).hash().equals(job.sourceHash)) throw new IllegalStateException("Source changed during compilation; reload again.");
            loader = compiled.loader(ScriptApi.class.getClassLoader());
            compiled.verifyLinks(loader);
            Class<?> type = Class.forName(compiled.source().className(), false, loader);
            next = new ScriptContext(job.script, job.generation, root.resolve(".data").resolve(job.script), log, compiled.source());
            instance = type.getConstructor(ScriptContext.class).newInstance(next);
            if (job.action.equals("evaluate")) {
                if (job.worldEpoch != worldEpoch || job.player != mc.player) throw new IllegalStateException("World or player changed during evaluation compilation");
                job.player = null;
                next.prepared(); next.install();
                Object result = lifecycle(instance, "evaluate", true);
                job.value = new JsonPrimitive(String.valueOf(result)); job.finishedAt = System.currentTimeMillis(); job.state = "evaluated";
                next.close(); loader.close(); compiled.discard(); return;
            }
            lifecycle(instance, "onLoad", false); next.prepared(); next.validate(old == null ? null : old.context);
            if (old != null) {
                if (Client.instance.getConfigManager().getCurrentConfig() instanceof cn.omix.config.impl.ModuleConfig config) config.retainCurrentState();
                old.context.detach(); detached = true;
            }
            next.install();
            restore(next, state);
            next.committed();
            next.assertHealthy();
            Running current = new Running(job.sourceHash, job.generation, next, instance, compiled, loader);
            running.put(job.script, current);
            persist();
            if (old != null) dispose(old);
            job.finishedAt = System.currentTimeMillis(); job.state = "loaded"; log.add(job.script, job.generation, "INFO", "Loaded " + job.sourceHash.substring(0, 12), 0);
        } catch (Throwable error) { cn.omix.util.script.ScriptFailures.rethrowFatal(error);
            if (next != null) { next.error("load", error); next.close(); }
            if (loader != null) try { loader.close(); } catch (IOException ignored) { }
            if (old == null) running.remove(job.script);
            if (old != null && detached) {
                try { old.context.install(); restore(old.context, state); running.put(job.script, old); }
                catch (Throwable rollback) { ScriptFailures.rethrowFatal(rollback); running.remove(job.script); dispose(old); error.addSuppressed(rollback); }
            }
            compiled.discard();
            fail(job, error);
        } finally { if (!job.action.equals("evaluate")) im.webui.WebUiRuntime.getInstance().notifyModulesChanged(); }
    }
    private Map<String, JsonObject> snapshot(ScriptContext context) {
        Map<String, JsonObject> result = new HashMap<>();
        context.modules().forEach(handle -> result.put(handle.id(), ScriptState.capture(handle.nativeModule())));
        context.modes().forEach(handle -> result.put(handle.module().getId(), ScriptState.capture(handle.module())));
        return result;
    }
    private void restore(ScriptContext context, Map<String, JsonObject> state) {
        Set<Module> modules = new LinkedHashSet<>(); context.modules().forEach(handle -> modules.add(handle.nativeModule())); context.modes().forEach(handle -> modules.add(handle.module()));
        for (Module module : modules) {
            if (state.containsKey(module.getId())) ScriptState.restore(module, state.get(module.getId()));
            else if (Client.instance.getConfigManager().getCurrentConfig() instanceof cn.omix.config.impl.ModuleConfig config) config.applyRetained(module);
        }
    }
    private static Object lifecycle(Object instance, String name, boolean required) throws Exception {
        Method method;
        try { method = instance.getClass().getDeclaredMethod(name); }
        catch (NoSuchMethodException error) { if (required) throw error; return null; }
        method.setAccessible(true);
        try { return method.invoke(instance); }
        catch (InvocationTargetException error) {
            if (error.getCause() instanceof Error fatal) throw fatal;
            if (error.getCause() instanceof Exception cause) throw cause;
            throw error;
        }
    }
    private void dispose(Running value) {
        value.context.close();
        try { lifecycle(value.instance, "onUnload", false); }
        catch (Throwable error) { cn.omix.util.script.ScriptFailures.rethrowFatal(error); value.context.error("onUnload", error); }
        try { value.loader.close(); } catch (IOException error) { value.context.error("classLoader", error); }
        value.compiled.discard();
    }
    private void fail(Job job, Throwable error) {
        synchronized (job) {
            if (job.state.equals("cancelled")) return;
            job.player = null;
            job.error = error + " (phase: " + job.phase + ")";
            if (error instanceof ScriptCompiler.CompileFailure failure) job.diagnostics = failure.diagnostics;
            job.finishedAt = System.currentTimeMillis(); job.state = "failed";
            log.add(job.script, job.generation, "ERROR", job.error, 0);
        }
    }
    public void unload(String id) throws IOException {
        if (!mc.isOnThread()) throw new IllegalStateException("Unload must run on the client thread");
        id = ScriptFiles.id(id); attempts.remove(id);
        String scriptId = id;
        jobs.values().stream().filter(job -> job.script.equals(scriptId) && !job.action.equals("check")).forEach(job -> cancel(job.id));
        if (Client.instance.getConfigManager().getCurrentConfig() instanceof cn.omix.config.impl.ModuleConfig config) config.retainCurrentState();
        Running value = running.remove(id);
        try { if (value != null) dispose(value); persist(); }
        finally { im.webui.WebUiRuntime.getInstance().notifyModulesChanged(); }
    }
    public JsonObject status() throws IOException {
        JsonObject result = new JsonObject(); JsonArray array = new JsonArray();
        Set<String> ids = new TreeSet<>(files.list()); ids.addAll(running.keySet());
        for (String id : ids) {
            JsonObject item = new JsonObject(); item.addProperty("id", id);
            String disk = Files.exists(files.path(id)) ? files.read(id).hash() : "";
            Running value = running.get(id); item.addProperty("sourceHash", disk); item.addProperty("loaded", value != null);
            item.addProperty("runningHash", value == null ? "" : value.hash); item.addProperty("generation", value == null ? 0 : value.generation);
            item.addProperty("changed", value != null && !disk.equals(value.hash));
            JsonArray toolStates = new JsonArray();
            if (value != null) for (var tool : value.context.tools()) {
                JsonObject state = new JsonObject(); state.addProperty("name", tool.name());
                state.addProperty("available", tool.available()); state.addProperty("requiresWorld", tool.requiresWorld()); toolStates.add(state);
            }
            item.add("tools", toolStates);
            jobs.values().stream().filter(job -> job.script.equals(id)).max(Comparator.comparingLong(job -> job.generation)).ifPresent(job -> {
                item.addProperty("latestJobId", job.id); item.addProperty("latestJobState", job.state); item.addProperty("latestCompileHash", job.sourceHash);
            });
            array.add(item);
        }
        result.add("scripts", array); result.addProperty("directory", files.root().toString()); result.addProperty("apiVersion", ScriptApi.API_VERSION); return result;
    }
    public Job job(String id) { Job job = jobs.get(id); if (job == null) throw new IllegalArgumentException("Unknown or expired job: " + id); return job; }
    public void cancel(String id) {
        Job job = job(id);
        synchronized (job) {
            if (Set.of("queued", "compiling").contains(job.state)) { job.player = null; job.finishedAt = System.currentTimeMillis(); job.state = "cancelled"; attempts.remove(job.script, job.generation); }
        }
    }
    private void persist() throws IOException { ScriptFiles.atomicWrite(root.resolve(".loaded.json"), new Gson().toJson(running.keySet().stream().sorted().toList())); }
    @Override public void close() {
        closed = true; jobs.values().forEach(job -> cancel(job.id)); worker.shutdownNow();
        if (worldListener != null) worldListener.close();
        jobs.values().forEach(job -> job.player = null);
        // The load manifest describes user intent, so shutdown must not empty it.
        for (Running value : running.values()) dispose(value); running.clear();
    }
}
