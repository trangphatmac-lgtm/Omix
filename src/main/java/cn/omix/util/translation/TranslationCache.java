package cn.omix.util.translation;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Bounded memory cache with asynchronous, atomic persistence of UI templates only. */
public final class TranslationCache implements AutoCloseable {
    static final int MAX_ENTRIES = 5000, MAX_BYTES = 10 * 1024 * 1024;
    static final long TTL = Duration.ofDays(30).toMillis();
    private record Entry(String value, long created, boolean persistent, int bytes) {}
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>(32, .75f, true);
    private final Path file;
    private final ScheduledExecutorService io;
    private int bytes;
    private long revision, savedRevision;

    public TranslationCache(Path file) {
        this.file = file;
        io = Executors.newSingleThreadScheduledExecutor(r -> Thread.ofPlatform().daemon().name("Omix-Translation-Cache").unstarted(r));
        if (file != null) { io.execute(this::load); io.scheduleWithFixedDelay(this::save, 5, 5, TimeUnit.SECONDS); }
    }
    public synchronized String get(String key, long now) {
        Entry entry = entries.get(key);
        if (entry == null) return null;
        if (now - entry.created > TTL || entry.created > now) { remove(key); return null; }
        return entry.value;
    }
    public synchronized void put(String key, String value, boolean persistent, long now) {
        if (value.length() > 16000) return;
        remove(key);
        int size = key.getBytes(StandardCharsets.UTF_8).length + value.getBytes(StandardCharsets.UTF_8).length + 128;
        entries.put(key, new Entry(value, now, persistent, size)); bytes += size;
        if (persistent) revision++;
        while (entries.size() > MAX_ENTRIES || bytes > MAX_BYTES) remove(entries.keySet().iterator().next());
    }
    public synchronized void clearChat() {
        var iterator = entries.values().iterator();
        while (iterator.hasNext()) { var entry = iterator.next(); if (!entry.persistent) { bytes -= entry.bytes; iterator.remove(); } }
    }
    private void remove(String key) {
        Entry previous = entries.remove(key);
        if (previous != null) { bytes -= previous.bytes; if (previous.persistent) revision++; }
    }
    private void load() {
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > MAX_BYTES) return;
            JsonObject data = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if (data.get("version").getAsInt() != TranslationTemplate.VERSION) return;
            JsonArray list = data.getAsJsonArray("entries");
            if (list.size() > MAX_ENTRIES) return;
            // Validate the entire file before accepting any entry.
            var loaded = new LinkedHashMap<String, Entry>(); long now = System.currentTimeMillis();
            for (JsonElement element : list) {
                JsonObject item = element.getAsJsonObject();
                String key = item.get("key").getAsString(), value = item.get("value").getAsString();
                long created = item.get("created").getAsLong();
                if (!key.matches("[0-9a-f]{64}") || value.length() > 16000) return;
                if (now - created <= TTL && created <= now) loaded.put(key, new Entry(value, created, true, 0));
            }
            synchronized (this) {
                loaded.forEach((key, entry) -> { if (!entries.containsKey(key)) put(key, entry.value, true, entry.created); });
            }
        } catch (Exception ignored) { /* Corrupt or old caches are expendable; game startup must continue. */ }
    }
    private void save() {
        if (file == null) return;
        JsonObject data = new JsonObject(); JsonArray list = new JsonArray(); long snapshot;
        synchronized (this) {
            if (revision == savedRevision) return;
            snapshot = revision;
            data.addProperty("version", TranslationTemplate.VERSION);
            long now = System.currentTimeMillis();
            for (var item : entries.entrySet()) {
                Entry entry = item.getValue();
                if (!entry.persistent || now - entry.created > TTL) continue;
                JsonObject json = new JsonObject(); json.addProperty("key", item.getKey());
                json.addProperty("value", entry.value); json.addProperty("created", entry.created); list.add(json);
            }
        }
        data.add("entries", list);
        try {
            byte[] encoded = data.toString().getBytes(StandardCharsets.UTF_8);
            // JSON escaping can be larger than the in-memory estimate. Trim oldest persisted rows.
            int encodedSize = encoded.length;
            while (encodedSize > MAX_BYTES && !list.isEmpty())
                encodedSize -= list.remove(0).toString().getBytes(StandardCharsets.UTF_8).length + 1;
            if (encodedSize != encoded.length) encoded = data.toString().getBytes(StandardCharsets.UTF_8);
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(temporary, encoded);
            try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException error) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
            synchronized (this) { savedRevision = snapshot; }
        } catch (Exception ignored) { /* Retry the dirty snapshot later. */ }
    }
    @Override public void close() {
        io.execute(this::save); io.shutdown();
        try { io.awaitTermination(3, TimeUnit.SECONDS); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
    }
    CompletableFuture<Void> flush() { return CompletableFuture.runAsync(this::save, io); }
    CompletableFuture<Void> loaded() { return CompletableFuture.runAsync(() -> {}, io); }
}
