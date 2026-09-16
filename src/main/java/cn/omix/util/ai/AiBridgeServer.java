package cn.omix.util.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Node-only loopback bridge. Deliberately exposes neither browser CORS nor model credentials. */
final class AiBridgeServer implements AutoCloseable {
    private final String token = Base64.getUrlEncoder().withoutPadding().encodeToString(new SecureRandom().generateSeed(32));
    private final HttpServer server;
    private final ExecutorService requests = Executors.newVirtualThreadPerTaskExecutor();
    private final GameToolSessions sessions;
    private final Supplier<CompletableFuture<JsonObject>> snapshot;
    private final Supplier<String> reference;

    AiBridgeServer(GameToolSessions sessions, Supplier<CompletableFuture<JsonObject>> snapshot,
                   Supplier<String> reference) throws IOException {
        this.sessions = sessions;
        this.snapshot = snapshot;
        this.reference = reference;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 32);
        server.setExecutor(requests);
        server.createContext("/", this::handle);
        server.start();
    }

    String endpoint() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
    String token() { return token; }

    private void handle(HttpExchange exchange) throws IOException {
        String callId = null;
        try (exchange) {
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            String host = exchange.getRequestHeaders().getFirst("Host");
            if (!MessageDigest.isEqual(("Bearer " + token).getBytes(StandardCharsets.UTF_8),
                    (authorization == null ? "" : authorization).getBytes(StandardCharsets.UTF_8))
                    || !(("127.0.0.1:" + server.getAddress().getPort()).equals(host))
                    || exchange.getRequestHeaders().containsKey("Origin")) {
                send(exchange, 403, error("Forbidden"));
                return;
            }
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            JsonObject reply = new JsonObject();
            try {
                if (method.equals("GET") && path.equals("/v1/snapshot")) {
                    reply = snapshot.get().get(10, TimeUnit.SECONDS);
                } else if (method.equals("GET") && path.equals("/v1/reference")) {
                    reply.addProperty("text", reference.get());
                } else if (method.equals("POST") && path.equals("/v1/calls")) {
                    byte[] bytes = exchange.getRequestBody().readNBytes(65537);
                    if (bytes.length > 65536) throw new IllegalArgumentException("Request exceeds 64 KiB.");
                    JsonObject body = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
                    callId = identifier(body, "id");
                    String agent = identifier(body, "agentId");
                    String name = identifier(body, "name");
                    long epoch = body.get("worldEpoch").getAsBigDecimal().longValueExact();
                    var value = sessions.submit(callId, agent, epoch, name, body.getAsJsonObject("arguments"))
                            .get(30, TimeUnit.SECONDS);
                    reply.addProperty("ok", true);
                    reply.add("value", value);
                } else if (method.equals("DELETE") && path.startsWith("/v1/calls/")) {
                    sessions.cancel(path.substring("/v1/calls/".length()));
                    reply.addProperty("ok", true);
                } else if (method.equals("POST") && path.matches("/v1/agents/[^/]+/release")) {
                    sessions.release(path.split("/")[3]);
                    reply.addProperty("ok", true);
                } else {
                    send(exchange, 404, error("Unknown bridge route."));
                    return;
                }
            } catch (Exception exception) {
                if (callId != null && (exception instanceof TimeoutException || exception instanceof InterruptedException)) sessions.cancel(callId);
                Throwable cause = exception;
                while (cause.getCause() != null) cause = cause.getCause();
                reply = error(cause instanceof CancellationException ? "Game call cancelled; already submitted actions are not undone."
                        : cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage());
            }
            try { send(exchange, 200, reply); }
            catch (IOException exception) { if (callId != null) sessions.cancel(callId); }
        }
    }

    private static String identifier(JsonObject body, String field) {
        var value = body.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || !value.getAsString().matches("[A-Za-z0-9_.:-]{1,200}"))
            throw new IllegalArgumentException("Invalid " + field);
        return value.getAsString();
    }
    private static JsonObject error(String message) {
        JsonObject result = new JsonObject();
        result.addProperty("ok", false);
        result.addProperty("error", message);
        return result;
    }
    private static void send(HttpExchange exchange, int status, JsonObject body) throws IOException {
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
    @Override public void close() {
        sessions.close();
        server.stop(0);
        requests.shutdownNow();
    }
}
