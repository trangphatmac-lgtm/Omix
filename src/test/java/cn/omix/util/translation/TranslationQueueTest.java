package cn.omix.util.translation;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class TranslationQueueTest {
    private static final TranslationQueue.Context CONTEXT = new TranslationQueue.Context("server", "provider", "model", "zh-Hans", 1);
    private static TranslationTemplate template(String value) { return TranslationTemplate.of(List.of(value), List.of()); }
    @Test void batchesDeduplicatesAndReusesChangingNumbersWithoutBlocking() {
        try (var cache = new TranslationCache(null)) {
            AtomicLong clock = new AtomicLong(1000); var reply = new CompletableFuture<Map<String, String>>();
            List<List<TranslationQueue.Item>> sent = new ArrayList<>();
            var queue = new TranslationQueue(cache, (context, items) -> { sent.add(items); return reply; }, clock::get);
            queue.configure(CONTEXT);
            for (int i = 0; i < 100; i++) assertNull(queue.lookup("scoreboard", template("Kills: " + i), clock.get()));
            assertEquals(1, queue.queuedCount());
            queue.tick(1199); assertTrue(sent.isEmpty());
            queue.tick(1200); assertEquals(1, sent.size());
            queue.lookup("scoreboard", template("Kills: 101"), 1200); assertEquals(0, queue.queuedCount());
            var item = sent.getFirst().getFirst();
            reply.complete(Map.of(item.id(), item.text().replace("Kills", "击杀")));
            var current = template("Kills: 102");
            assertEquals(List.of("击杀: 102"), current.restore(queue.lookup("scoreboard", current, 1201)));
        }
    }
    @Test void limitsConcurrencyCapacityAndBatchCharacters() {
        try (var cache = new TranslationCache(null)) {
            List<List<TranslationQueue.Item>> sent = new ArrayList<>();
            var queue = new TranslationQueue(cache, (context, items) -> { sent.add(items); return new CompletableFuture<>(); });
            queue.configure(CONTEXT);
            for (int i = 0; i < 400; i++) queue.lookup("chat", template("Message " + (char)(0x4e00 + i)), 1000);
            assertEquals(256, queue.queuedCount());
            for (int i = 0; i < 10; i++) queue.tick(2000);
            assertEquals(2, sent.size());
            assertTrue(sent.stream().allMatch(batch -> batch.size() == 16 && batch.stream().mapToInt(item -> item.text().length()).sum() <= 8000));
        }
    }
    @Test void resetsCancelAndDiscardLateRepliesEvenIfTransportIgnoresCancellation() {
        try (var cache = new TranslationCache(null)) {
            var late = new CompletableFuture<Map<String, String>>() { @Override public boolean cancel(boolean interrupt) { return false; } };
            List<TranslationQueue.Item> sent = new ArrayList<>();
            var queue = new TranslationQueue(cache, (context, items) -> { sent.addAll(items); return late; });
            queue.configure(CONTEXT); var template = template("Hello"); queue.lookup("chat", template, 0); queue.tick(200);
            queue.reset(); var item = sent.getFirst(); late.complete(Map.of(item.id(), item.text().replace("Hello", "你好")));
            queue.configure(CONTEXT);
            assertNull(queue.lookup("chat", template, 300));
            assertEquals(1, queue.queuedCount());
        }
    }
    @Test void invalidResultsBackOffAndNeverEnterCache() {
        try (var cache = new TranslationCache(null)) {
            AtomicLong clock = new AtomicLong(1000); int[] calls = {0};
            var queue = new TranslationQueue(cache, (context, items) -> { calls[0]++; return CompletableFuture.completedFuture(Map.of(items.getFirst().id(), "missing markers")); }, clock::get);
            queue.configure(CONTEXT); var template = template("Hello"); queue.lookup("chat", template, 1000); queue.tick(1200);
            assertNotNull(queue.error()); assertNull(queue.lookup("chat", template, 1200));
            queue.tick(3000); assertEquals(1, calls[0]);
            clock.set(3300); queue.tick(3300); assertEquals(2, calls[0]);
        }
    }
    @Test void cacheIdentityIncludesServerRouteLanguageKindAndVersionButNotRuntime() {
        String key = TranslationQueue.key(CONTEXT, "chat", "hello");
        assertNotEquals(key, TranslationQueue.key(CONTEXT, "scoreboard", "hello"));
        assertNotEquals(key, TranslationQueue.key(new TranslationQueue.Context("other", "provider", "model", "zh-Hans", 1), "chat", "hello"));
        assertNotEquals(key, TranslationQueue.key(new TranslationQueue.Context("server", "provider", "model2", "zh-Hans", 1), "chat", "hello"));
        assertNotEquals(key, TranslationQueue.key(new TranslationQueue.Context("server", "provider", "model", "ja", 1), "chat", "hello"));
        assertEquals(key, TranslationQueue.key(new TranslationQueue.Context("server", "provider", "model", "zh-Hans", 99), "chat", "hello"));
    }
}
