package cn.omix.util.ai;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.MinecraftClient;
import me.ksyz.accountmanager.auth.SessionService;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

final class MinecraftGameBridge implements AutoCloseable {
    private final MinecraftClient client = MinecraftClient.getInstance();
    private final MinecraftCommandToolExecutor tools = new MinecraftCommandToolExecutor();
    private Object world;
    private Object player;
    private long generation;
    private final AiBridgeServer server;

    MinecraftGameBridge() throws IOException {
        var sessions = new GameToolSessions(client::execute, this::epoch, new GameToolSessions.Tools() {
            @Override public CompletableFuture<JsonElement> execute(String name, JsonObject arguments) {
                CompletableFuture<String> execution = tools.execute(new AiToolCall("bridge", name, arguments.toString()));
                CompletableFuture<JsonElement> value = execution.thenApply(text -> {
                    try { return JsonParser.parseString(text); }
                    catch (Exception ignored) { return new JsonPrimitive(text); }
                });
                value.whenComplete((ignored, error) -> { if (value.isCancelled()) execution.cancel(false); });
                return value;
            }
            @Override public void reset() { tools.reset(); }
        });
        server = new AiBridgeServer(sessions, this::snapshot, AiClientReference::promptContext);
    }

    // Called exclusively on the game thread, including immediately before queued dispatch.
    private long epoch() {
        if (world != client.world || player != client.player) {
            world = client.world;
            player = client.player;
            generation++;
            tools.reset();
        }
        return generation;
    }

    private CompletableFuture<JsonObject> snapshot() {
        return client.submit(() -> {
            JsonObject result = new JsonObject();
            result.addProperty("protocolVersion", 1);
            result.addProperty("worldEpoch", epoch());
            AiToolSnapshot snapshot = tools.snapshot();
            result.add("tools", snapshot.definitions());
            result.addProperty("toolContext", snapshot.promptContext());
            // capture completes inline on the client thread.
            result.addProperty("gameContext", AiGameContext.capture(SessionService.current().getUsername()).join().promptContext());
            return result;
        });
    }

    String endpoint() { return server.endpoint(); }
    String token() { return server.token(); }
    @Override public void close() { server.close(); }
}
