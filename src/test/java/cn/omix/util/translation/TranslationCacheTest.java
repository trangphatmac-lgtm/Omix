package cn.omix.util.translation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class TranslationCacheTest {
    @TempDir Path directory;
    private static String key(int value) { return "%064x".formatted(value); }
    @Test void persistsOnlyUiTemplatesAndLoadsAfterRestart() throws Exception {
        Path file = directory.resolve("cache.json"); long now = System.currentTimeMillis();
        try (var cache = new TranslationCache(file)) {
            cache.loaded().join(); cache.put(key(1), "private chat", false, now); cache.put(key(2), "ui template", true, now); cache.flush().join();
        }
        assertFalse(Files.readString(file).contains("private chat"));
        try (var cache = new TranslationCache(file)) {
            cache.loaded().join(); assertNull(cache.get(key(1), now)); assertEquals("ui template", cache.get(key(2), now));
            assertNull(cache.get(key(2), now + TranslationCache.TTL + 1));
        }
    }
    @Test void ignoresCorruptFilesAndEvictsLeastRecentlyUsedEntries() throws Exception {
        Path file = directory.resolve("cache.json"); Files.writeString(file, "{corrupt");
        try (var cache = new TranslationCache(file)) {
            cache.loaded().join();
            for (int i = 0; i < 5000; i++) cache.put(key(i), "value", true, 1000);
            cache.get(key(0), 1000); cache.put(key(5000), "last", true, 1000);
            assertEquals("value", cache.get(key(0), 1000)); assertNull(cache.get(key(1), 1000));
        }
    }
    @Test void boundsMemoryByBytesAsWellAsCountAndClearsChatOnly() {
        try (var cache = new TranslationCache(null)) {
            for (int i = 0; i < 1000; i++) cache.put(key(i), "中".repeat(10000), true, 1000);
            assertNull(cache.get(key(0), 1000)); assertNotNull(cache.get(key(999), 1000));
            cache.put(key(1001), "chat", false, 1000); cache.clearChat();
            assertNull(cache.get(key(1001), 1000)); assertNotNull(cache.get(key(999), 1000));
        }
    }
}
