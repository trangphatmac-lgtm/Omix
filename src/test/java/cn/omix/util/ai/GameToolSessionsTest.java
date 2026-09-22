package cn.omix.util.ai;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class GameToolSessionsTest {
    static final class Fixture implements GameToolSessions.Tools {
        final Queue<Runnable> queue = new ArrayDeque<>();
        final List<String> dispatched = new ArrayList<>();
        final List<CompletableFuture<JsonElement>> operations = new ArrayList<>();
        final AtomicLong epoch = new AtomicLong(1);
        final GameToolSessions sessions = new GameToolSessions(queue::add, epoch::get, this);
        int resets;
        @Override public CompletableFuture<JsonElement> execute(String name, JsonObject args) {
            dispatched.add(name);
            var future = new CompletableFuture<JsonElement>(); operations.add(future); return future;
        }
        @Override public void reset() { resets++; }
        void drain() { while (!queue.isEmpty()) queue.remove().run(); }
        CompletableFuture<JsonElement> call(String id, String agent, String name) {
            return sessions.submit(id, agent, epoch.get(), name, new JsonObject());
        }
    }
    @Test void serializesCallsAndRejectsAnotherAgentUntilRelease() {
        var f = new Fixture();
        var a = f.call("1", "a", "read"); var b = f.call("2", "a", "click");
        assertThrows(CompletionException.class, () -> f.call("3", "b", "read").join());
        f.drain(); assertEquals(List.of("read"), f.dispatched);
        f.operations.getFirst().complete(new JsonPrimitive("snapshot")); f.drain();
        assertEquals(List.of("read", "click"), f.dispatched);
        f.operations.get(1).complete(new JsonPrimitive("submitted"));
        assertEquals("snapshot", a.join().getAsString()); assertEquals("submitted", b.join().getAsString());
        f.sessions.release("a"); f.call("3", "b", "read"); f.drain();
        assertEquals(1, f.resets); assertEquals(3, f.dispatched.size());
    }
    @Test void repeatedIdsNeverRepeatAnActionAndRejectChangedArguments() {
        var f = new Fixture(); var first = f.call("1", "a", "click");
        var second = f.call("1", "a", "click"); f.drain();
        f.operations.getFirst().complete(new JsonPrimitive("submitted"));
        assertEquals(first.join(), second.join()); assertEquals(1, f.dispatched.size());
        assertThrows(CompletionException.class, () -> f.call("1", "a", "close").join());
        assertThrows(CompletionException.class, () -> f.call("1", "b", "click").join());
    }
    @Test void cancellationBeforeArrivalAndWhileQueuedCannotDispatch() {
        var f = new Fixture(); f.sessions.cancel("early");
        assertThrows(CompletionException.class, () -> f.call("early", "a", "click").join());
        var queued = f.call("queued", "a", "click"); f.sessions.cancel("queued"); f.drain();
        assertTrue(queued.isCompletedExceptionally()); assertTrue(f.dispatched.isEmpty());
    }
    @Test void cancellationReachesPendingContainerAndReleaseClearsSnapshots() {
        var f = new Fixture(); var pending = f.call("1", "a", "opencontainer"); f.drain();
        var queued = f.call("2", "a", "clickcontainerslot"); f.sessions.release("a"); f.drain();
        assertTrue(f.operations.getFirst().isCancelled());
        assertTrue(pending.isCompletedExceptionally()); assertTrue(queued.isCompletedExceptionally());
        assertEquals(1, f.dispatched.size()); assertEquals(1, f.resets);
    }
    @Test void worldIsCheckedAtExecutionTimeNotJustWhenRequestArrives() {
        var f = new Fixture(); var call = f.call("1", "a", "click");
        f.epoch.incrementAndGet(); f.drain();
        assertThrows(CompletionException.class, call::join); assertTrue(f.dispatched.isEmpty());
    }
    @Test void independentSourceOperationsDoNotAcquireOwnerAndLeaseCleanupCancelsThem() {
        var pending = new CompletableFuture<JsonElement>();
        var sessions = new GameToolSessions(Runnable::run, () -> 9, new GameToolSessions.Tools() {
            public CompletableFuture<JsonElement> execute(String name, JsonObject args) { return name.equals("source") ? pending : CompletableFuture.completedFuture(new JsonPrimitive("ok")); }
            public boolean independent(String name) { return name.equals("source"); }
            public boolean requiresWorld(String name) { return !name.equals("source"); }
            public void reset() { }
        });
        var source = sessions.submit("1", "reader", -1, "source", new JsonObject());
        var game = sessions.submit("2", "player", 9, "game", new JsonObject());
        sessions.forget("reader");
        assertTrue(source.isCompletedExceptionally()); assertTrue(pending.isCancelled());
        assertEquals("ok",game.join().getAsString()); sessions.close();
    }
    @Test void failuresDoNotBlockQueueAndClosingCancelsWork() {
        var f = new Fixture(); f.call("1", "a", "read"); var next = f.call("2", "a", "read");
        f.drain(); f.operations.getFirst().completeExceptionally(new IllegalStateException("offline")); f.drain();
        assertEquals(2, f.dispatched.size()); f.sessions.close(); f.drain();
        assertTrue(next.isCompletedExceptionally());
        assertThrows(CompletionException.class, () -> f.call("3", "a", "read").join());
    }
}
