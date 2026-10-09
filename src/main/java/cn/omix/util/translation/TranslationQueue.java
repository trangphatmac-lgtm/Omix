package cn.omix.util.translation;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Tick-driven, nonblocking scheduler; injectable transport and clock make races testable. */
public final class TranslationQueue {
    public record Context(String server, String provider, String model, String target, long runtime) {}
    public record Item(String id, String kind, String text) {}
    @FunctionalInterface public interface Backend { CompletableFuture<Map<String, String>> translate(Context context, List<Item> items); }
    private record Pending(String key, String kind, TranslationTemplate template) {}
    private final TranslationCache cache;
    private final Backend backend;
    private final LinkedHashMap<String, Pending> queued = new LinkedHashMap<>();
    private final Set<String> inFlight = new HashSet<>();
    private final Set<CompletableFuture<?>> requests = new HashSet<>();
    private final LinkedHashMap<String, Long> rejected = new LinkedHashMap<>();
    private final Map<TranslationTemplate, Map<String, String>> keys = new WeakHashMap<>();
    private final java.util.function.LongSupplier clock;
    private Context context;
    private long epoch, readyAt, retryAt, revision;
    private int failures;
    private String error;
    public TranslationQueue(TranslationCache cache, Backend backend) { this(cache, backend, System::currentTimeMillis); }
    TranslationQueue(TranslationCache cache, Backend backend, java.util.function.LongSupplier clock) {
        this.cache = cache; this.backend = backend; this.clock = clock;
    }
    public synchronized void configure(Context next) {
        if (Objects.equals(context, next)) return;
        reset(); context = next;
    }
    public synchronized void reset() {
        epoch++; context = null;
        var cancelling = List.copyOf(requests); requests.clear(); inFlight.clear(); queued.clear(); rejected.clear(); keys.clear();
        failures = 0; error = null; readyAt = retryAt = 0; revision++;
        cancelling.forEach(request -> request.cancel(true));
        cache.clearChat();
    }
    public synchronized String lookup(String kind, TranslationTemplate template, long now) {
        if (context == null || !template.translatable()) return null;
        String key = keys.computeIfAbsent(template, ignored -> new HashMap<>()).computeIfAbsent(kind, ignored -> key(context, kind, template.source()));
        String value = cache.get(key, now);
        if (value != null && template.accepts(value)) return value;
        if (queued.containsKey(key) || inFlight.contains(key) || queued.size() >= 256 || rejected.getOrDefault(key, 0L) > now) return null;
        if (queued.isEmpty()) readyAt = now + 200;
        queued.put(key, new Pending(key, kind, template));
        return null;
    }
    public synchronized void tick(long now) {
        if (context == null || queued.isEmpty() || now < readyAt || now < retryAt || requests.size() >= 2) return;
        List<Pending> batch = new ArrayList<>(); int chars = 0;
        var iterator = queued.values().iterator();
        while (iterator.hasNext() && batch.size() < 16) {
            Pending pending = iterator.next(); int size = pending.template.source().length();
            if (chars + size > 8000) break;
            iterator.remove(); batch.add(pending); chars += size; inFlight.add(pending.key);
        }
        if (batch.isEmpty()) return;
        long dispatchedEpoch = epoch;
        List<Item> items = batch.stream().map(pending -> new Item(pending.key, pending.kind, pending.template.source())).toList();
        CompletableFuture<Map<String, String>> request;
        try { request = backend.translate(context, items); }
        catch (Exception failure) { request = CompletableFuture.failedFuture(failure); }
        requests.add(request);
        var tracked = request;
        request.whenComplete((result, failure) -> complete(tracked, dispatchedEpoch, batch, result, failure, now));
    }
    private synchronized void complete(CompletableFuture<?> request, long dispatchedEpoch, List<Pending> batch,
                                       Map<String, String> result, Throwable failure, long dispatchedAt) {
        if (dispatchedEpoch != epoch) return;
        requests.remove(request); batch.forEach(pending -> inFlight.remove(pending.key));
        long now = Math.max(dispatchedAt, clock.getAsLong());
        boolean valid = failure == null && result != null && result.size() == batch.size()
                && batch.stream().allMatch(pending -> pending.template.accepts(result.get(pending.key)));
        if (valid) {
            batch.forEach(pending -> cache.put(pending.key, result.get(pending.key), !pending.kind.equals("chat"), now));
            failures = 0; error = null; retryAt = 0; revision++;
        } else {
            error = "Translation unavailable";
            retryAt = now + Math.min(60000L, 2000L << Math.min(5, failures++));
            batch.forEach(pending -> rejected.put(pending.key, retryAt));
            while (rejected.size() > 512) rejected.remove(rejected.keySet().iterator().next());
            // Retain bounded jobs (including chat, which is not drawn into the queue every frame).
            for (Pending pending : batch) if (queued.size() < 256) queued.putIfAbsent(pending.key, pending);
        }
    }
    public synchronized long revision() { return revision; }
    public synchronized String error() { return error; }
    public synchronized int queuedCount() { return queued.size(); }
    public static String key(Context context, String kind, String template) {
        try {
            byte[] json = new Gson().toJson(List.of(TranslationTemplate.VERSION, context.server, context.provider, context.model,
                    context.target, kind, template)).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
