package im.music;

import cn.omix.Client;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class MusicSidecarManager {
    public static final String TOKEN_HEADER = "X-Omix-Music-Token";
    private static final String RESOURCE = "/assets/omix/music/sidecar.zip";
    private static final String VERSION = "0.1.0";

    private final Path root;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private final Gson gson = new Gson();
    private volatile Process process;
    private volatile URI endpoint;
    private volatile String token;
    private volatile boolean stopping;
    private volatile Consumer<Throwable> exitListener = ignored -> {};

    public MusicSidecarManager(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public void setExitListener(Consumer<Throwable> exitListener) {
        this.exitListener = exitListener == null ? ignored -> {} : exitListener;
    }

    public synchronized URI start(Path nodeExecutable) throws Exception {
        if (isReady()) return endpoint;
        stop();
        stopping = false;
        Path directory = ensureExtracted();
        int port = reservePort();
        token = createToken();
        endpoint = URI.create("http://127.0.0.1:" + port);

        ProcessBuilder builder = new ProcessBuilder(
                nodeExecutable.toAbsolutePath().toString(),
                directory.resolve("index.js").toString()
        );
        builder.directory(directory.toFile());
        builder.redirectErrorStream(true);
        builder.environment().put("NODE_ENV", "production");
        builder.environment().put("OMIX_MUSIC_PORT", Integer.toString(port));
        builder.environment().put("OMIX_MUSIC_TOKEN", token);
        process = builder.start();
        Process started = process;
        Thread.ofVirtual().name("Omix-Music-Sidecar-Log").start(() -> readLogs(started));
        Thread.ofVirtual().name("Omix-Music-Sidecar-Wait").start(() -> watchExit(started));
        waitUntilHealthy(started);
        Client.logger.info("Omix Music sidecar ready on loopback port {}", port);
        return endpoint;
    }

    public boolean isReady() {
        Process current = process;
        return current != null && current.isAlive() && endpoint != null && token != null;
    }

    public URI getEndpoint() {
        if (!isReady()) throw new IllegalStateException("Music sidecar is not ready");
        return endpoint;
    }

    public String getToken() {
        if (!isReady()) throw new IllegalStateException("Music sidecar is not ready");
        return token;
    }

    public synchronized void stop() {
        stopping = true;
        Process current = process;
        process = null;
        endpoint = null;
        token = null;
        if (current == null || !current.isAlive()) return;
        current.destroy();
        try {
            if (!current.waitFor(3, TimeUnit.SECONDS)) {
                current.destroyForcibly();
                current.waitFor(2, TimeUnit.SECONDS);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            current.destroyForcibly();
        }
    }

    private Path ensureExtracted() throws IOException {
        Path directory = root.resolve("sidecar").resolve(VERSION).normalize();
        Path marker = directory.resolve(".complete");
        if (Files.isRegularFile(marker) && Files.isRegularFile(directory.resolve("index.js"))) {
            return directory;
        }
        if (Files.exists(directory)) deleteRecursively(directory);
        Files.createDirectories(directory);
        try (InputStream resource = MusicSidecarManager.class.getResourceAsStream(RESOURCE)) {
            if (resource == null) throw new IOException("Missing bundled music sidecar");
            try (ZipInputStream zip = new ZipInputStream(resource)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    Path target = directory.resolve(entry.getName()).normalize();
                    if (!target.startsWith(directory)) {
                        throw new IOException("Unsafe music sidecar archive entry");
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        Files.copy(zip, target);
                    }
                }
            }
        } catch (IOException exception) {
            deleteRecursively(directory);
            throw exception;
        }
        Files.writeString(marker, VERSION, StandardCharsets.UTF_8);
        return directory;
    }

    private void waitUntilHealthy(Process started) throws Exception {
        URI health = endpoint.resolve("/healthz");
        Throwable lastFailure = null;
        for (int attempt = 0; attempt < 80; attempt++) {
            if (!started.isAlive()) {
                throw new IOException("Music sidecar exited with code " + started.exitValue());
            }
            try {
                HttpRequest request = HttpRequest.newBuilder(health)
                        .timeout(Duration.ofSeconds(2))
                        .header(TOKEN_HEADER, token)
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
                );
                if (response.statusCode() == 200) {
                    JsonObject body = gson.fromJson(response.body(), JsonObject.class);
                    if (body != null && body.has("status")
                            && "ready".equals(body.get("status").getAsString())) {
                        return;
                    }
                }
            } catch (Exception exception) {
                lastFailure = exception;
            }
            Thread.sleep(250L);
        }
        stop();
        throw new IOException("Music sidecar health check timed out", lastFailure);
    }

    private void readLogs(Process target) {
        try (var reader = target.inputReader(StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String sanitized = line
                        .replaceAll("MUSIC_U=[^;\\s&]+", "MUSIC_U=<redacted>")
                        .replaceAll("__csrf=[^;\\s&]+", "__csrf=<redacted>");
                Client.logger.debug("[Music sidecar] {}", sanitized);
            }
        } catch (IOException ignored) {
        }
    }

    private void watchExit(Process target) {
        try {
            int exitCode = target.waitFor();
            if (!stopping && process == target) {
                exitListener.accept(new IOException("Music sidecar exited with code " + exitCode));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static int reservePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    private static String createToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static void deleteRecursively(Path target) throws IOException {
        if (!Files.exists(target)) return;
        try (var paths = Files.walk(target)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
