package cn.omix.util.ai;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.MinecraftClient;
import me.ksyz.accountmanager.auth.SessionService;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public final class MinecraftGameBridge implements AutoCloseable {
    private final MinecraftClient client = MinecraftClient.getInstance();
    private final MinecraftCommandToolExecutor tools = new MinecraftCommandToolExecutor();
    private Object world;
    private Object player;
    private long generation;
    private final AiBridgeServer server;
    private final java.nio.file.Path descriptor;
    private final java.nio.channels.FileChannel lockChannel;
    private final java.nio.channels.FileLock instanceLock;
    private final String instanceId = java.util.UUID.randomUUID().toString();

    public MinecraftGameBridge() throws IOException {
        descriptor = client.runDirectory.toPath().resolve("Omix/development/bridge.json");
        java.nio.file.Files.createDirectories(descriptor.getParent());
        lockChannel = java.nio.channels.FileChannel.open(descriptor.resolveSibling("bridge.lock"), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE);
        java.nio.channels.FileLock acquired;
        try { acquired = lockChannel.tryLock(); }
        catch (java.nio.channels.OverlappingFileLockException error) { lockChannel.close(); throw new IOException("Another Omix development bridge owns this game directory", error); }
        if (acquired == null) { lockChannel.close(); throw new IOException("Another Omix development bridge owns this game directory"); }
        instanceLock = acquired;
        var sessions = new GameToolSessions(client::execute, this::epoch, new GameToolSessions.Tools() {
            @Override public CompletableFuture<JsonElement> execute(String name, JsonObject arguments) {
                if (name.startsWith("custom_")) return CompletableFuture.completedFuture(cn.omix.util.script.ScriptToolRegistry.INSTANCE.execute(name, arguments, client.world != null && client.player != null));
                if (name.startsWith("script_")) return cn.omix.util.script.ScriptTools.execute(name, arguments);
                CompletableFuture<String> execution = tools.execute(new AiToolCall("bridge", name, arguments.toString()));
                CompletableFuture<JsonElement> value = execution.thenApply(text -> {
                    try { return JsonParser.parseString(text); }
                    catch (Exception ignored) { return new JsonPrimitive(text); }
                });
                value.whenComplete((ignored, error) -> { if (value.isCancelled()) execution.cancel(false); });
                return value;
            }
            @Override public boolean requiresWorld(String name) { return name.startsWith("custom_") ? cn.omix.util.script.ScriptToolRegistry.INSTANCE.requiresWorld(name) : !name.startsWith("script_"); }
            @Override public boolean independent(String name, JsonObject args) {
                if (name.equals("script_action") && args.has("action") && args.get("action").getAsString().equals("check")) return true;
                return java.util.Set.of("script_status", "script_read", "script_write", "script_delete", "script_templates", "script_create", "script_job", "script_cancel", "script_logs", "script_api", "script_reference").contains(name);
            }
            @Override public void reset() { tools.reset(); }
        });
        try { server = new AiBridgeServer(sessions, this::snapshot, AiClientReference::promptContext); }
        catch (IOException error) { instanceLock.release(); lockChannel.close(); throw error; }
        try {
        JsonObject connection = new JsonObject();
        connection.addProperty("protocolVersion", 2); connection.addProperty("endpoint", server.endpoint());
        connection.addProperty("token", server.token()); connection.addProperty("instanceId", instanceId);
        cn.omix.util.script.ScriptFiles.atomicWrite(descriptor, connection.toString());
        try { java.nio.file.Files.setPosixFilePermissions(descriptor, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")); }
        catch (UnsupportedOperationException ignored) { /* Windows inherits the user's game directory ACL. */ }
        } catch (IOException error) { server.close(); instanceLock.release(); lockChannel.close(); throw error; }
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
            result.addProperty("protocolVersion", 2);
            result.addProperty("instanceId", instanceId);
            result.addProperty("worldEpoch", epoch());
            AiToolSnapshot snapshot = tools.snapshot();
            var definitions = snapshot.definitions().deepCopy();
            definitions.addAll(cn.omix.util.script.ScriptReference.tools());
            definitions.addAll(cn.omix.util.script.ScriptToolRegistry.INSTANCE.definitions());
            result.add("tools", definitions);
            result.addProperty("toolContext", snapshot.promptContext());
            // capture completes inline on the client thread.
            result.addProperty("gameContext", AiGameContext.capture(SessionService.current().getUsername()).join().promptContext());
            return result;
        });
    }

    public String endpoint() { return server.endpoint(); }
    public String token() { return server.token(); }
    @Override public void close() {
        server.close();
        try {
            if (java.nio.file.Files.exists(descriptor) && JsonParser.parseString(java.nio.file.Files.readString(descriptor)).getAsJsonObject().get("instanceId").getAsString().equals(instanceId))
                java.nio.file.Files.delete(descriptor);
        } catch (Exception ignored) { }
        try { instanceLock.release(); lockChannel.close(); } catch (IOException ignored) { }
    }
}
