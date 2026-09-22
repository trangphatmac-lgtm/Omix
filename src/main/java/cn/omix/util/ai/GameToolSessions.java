package cn.omix.util.ai;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;

/** Serial game ownership, cancellation and replay protection; no Minecraft dependency. */
final class GameToolSessions implements AutoCloseable {
    interface Tools {
        CompletableFuture<JsonElement> execute(String name, JsonObject arguments);
        void reset();
        default boolean independent(String name) { return false; }
        default boolean independent(String name, JsonObject args) { return independent(name); }
        default boolean requiresWorld(String name) { return true; }
    }
    private record Key(String agent, String name, String arguments, long epoch) {}
    private static final class Call {
        final Key key;
        final CompletableFuture<JsonElement> result = new CompletableFuture<>();
        volatile CompletableFuture<JsonElement> running;
        Call(Key key) { this.key = key; }
        void cancel() {
            result.cancel(false);
            var execution = running;
            if (execution != null) execution.cancel(false);
        }
    }
    private final Executor executor;
    private final LongSupplier epoch;
    private final Tools tools;
    private final LinkedHashMap<String, Call> calls = new LinkedHashMap<>();
    private CompletableFuture<?> tail = CompletableFuture.completedFuture(null);
    private final java.util.Set<String> cancelled = new java.util.HashSet<>();
    private String owner;
    private boolean closed;

    GameToolSessions(Executor executor, LongSupplier epoch, Tools tools) {
        this.executor = executor;
        this.epoch = epoch;
        this.tools = tools;
    }

    synchronized CompletableFuture<JsonElement> submit(String id, String agent, long generation,
                                                       String name, JsonObject args) {
        if (cancelled.contains(id)) return failed("Game call cancelled before dispatch.");
        Key key = new Key(agent, name, args.toString(), generation);
        Call old = calls.get(id);
        if (old != null) {
            if (!old.key.equals(key)) return failed("Call ID was already used for another request.");
            return old.result.copy();
        }
        if (closed) return failed("Game bridge is stopped.");
        // Never evict execution identities and accidentally repeat a submitted action.
        if (calls.values().stream().filter(call -> call.key.agent.equals(agent)).count() >= 2048 || calls.size() >= 16384) return failed("Game call limit reached; release this Agent session.");
        if (!tools.independent(name, args) && owner != null && !owner.equals(agent)) return failed("Game tools are busy in another Agent turn.");
        if (!tools.independent(name, args)) owner = agent;
        Call call = new Call(key);
        calls.put(id, call);
        JsonObject detached = args.deepCopy();
        tail = tail.handle((ignored, error) -> null).thenComposeAsync(ignored -> {
            if (call.result.isDone()) return CompletableFuture.completedFuture(null);
            try {
                if (tools.requiresWorld(name) && epoch.getAsLong() != generation) throw new IllegalStateException("World changed; refresh game context before calling tools.");
                call.running = tools.execute(name, detached);
                if (call.result.isCancelled()) call.running.cancel(false);
                return call.running.handle((value, error) -> {
                    if (error == null) call.result.complete(value);
                    else call.result.completeExceptionally(error);
                    call.running = null;
                    return null;
                });
            } catch (Exception error) {
                call.result.completeExceptionally(error);
                return CompletableFuture.completedFuture(null);
            }
        }, executor);
        // Keep replay tombstones, but bound retained large world snapshots.
        if (calls.size() > 256) {
            int discard = calls.size() - 256;
            for (Call previous : calls.values()) {
                if (discard-- <= 0) break;
                if (previous.result.isDone() && !previous.result.isCompletedExceptionally())
                    previous.result.obtrudeException(new IllegalStateException("Call already completed; cached result expired. It was not repeated."));
            }
        }
        return call.result.copy();
    }

    synchronized void cancel(String id) {
        Call call = calls.get(id);
        if (call != null) call.cancel();
        else if (cancelled.size() < 4096) cancelled.add(id);
        else close();
    }

    synchronized void release(String agent) {
        for (Call call : calls.values()) if (call.key.agent.equals(agent) && !call.result.isDone()) call.cancel();
        if (!agent.equals(owner)) return;
        owner = null;
        tail = tail.handleAsync((ignored, error) -> { tools.reset(); return null; }, executor);
    }

    synchronized void forget(String agent) {
        release(agent);
        calls.entrySet().removeIf(entry -> entry.getValue().key.agent.equals(agent) && entry.getValue().result.isDone());
    }

    @Override public synchronized void close() {
        closed = true;
        if (owner != null) release(owner);
        for (Call call : calls.values()) if (!call.result.isDone()) call.cancel();
    }

    private static <T> CompletableFuture<T> failed(String message) {
        return CompletableFuture.failedFuture(new IllegalStateException(message));
    }
}
