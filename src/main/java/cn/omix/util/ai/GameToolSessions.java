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
        if (calls.size() >= 4096) return failed("Game call limit reached; restart the AI service.");
        if (owner != null && !owner.equals(agent)) return failed("Game tools are busy in another Agent turn.");
        owner = agent;
        Call call = new Call(key);
        calls.put(id, call);
        JsonObject detached = args.deepCopy();
        tail = tail.handle((ignored, error) -> null).thenComposeAsync(ignored -> {
            if (call.result.isDone()) return CompletableFuture.completedFuture(null);
            try {
                if (epoch.getAsLong() != generation) throw new IllegalStateException("World changed; refresh game context before calling tools.");
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
        if (!agent.equals(owner)) return;
        for (Call call : calls.values()) if (call.key.agent.equals(agent) && !call.result.isDone()) call.cancel();
        owner = null;
        tail = tail.handleAsync((ignored, error) -> { tools.reset(); return null; }, executor);
    }

    @Override public synchronized void close() {
        closed = true;
        if (owner != null) release(owner);
    }

    private static <T> CompletableFuture<T> failed(String message) {
        return CompletableFuture.failedFuture(new IllegalStateException(message));
    }
}
