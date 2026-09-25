package cn.omix.util.sigma;

import cn.omix.util.IMinecraft;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Bounded single-worker profile lookups. A render callback only reads/cache-enqueues, never waits on HTTP. */
public final class SigmaOwnerNames implements IMinecraft {
    private static final Map<UUID, Entry> CACHE = new LinkedHashMap<>(128, .75f, true);
    private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(128), runnable -> { Thread thread = new Thread(runnable, "Omix-Jello-Owners"); thread.setDaemon(true); return thread; });
    private SigmaOwnerNames() {}
    public static String name(UUID uuid) {
        var online = mc.getNetworkHandler() == null ? null : mc.getNetworkHandler().getPlayerListEntry(uuid);
        if (online != null) { CACHE.put(uuid, new Entry(online.getProfile().name(), Long.MAX_VALUE)); trim(); return online.getProfile().name(); }
        Entry entry = CACHE.get(uuid); long now = System.nanoTime();
        if (entry != null && now < entry.retryAfter) return entry.name;
        Entry pending = new Entry(null, now + TimeUnit.MINUTES.toNanos(2)); CACHE.put(uuid, pending); trim();
        var service = mc.getApiServices().sessionService();
        try {
            WORKER.execute(() -> {
                String found = null;
                try { var profile = service.fetchProfile(uuid, false); if (profile != null) found = profile.profile().name(); }
                catch (RuntimeException ignored) { /* Offline owners remain hidden until a later retry, as in Jello. */ }
                String result = found;
                mc.execute(() -> { if (CACHE.get(uuid) == pending) CACHE.put(uuid, new Entry(result, result == null ? pending.retryAfter : Long.MAX_VALUE)); });
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) { CACHE.remove(uuid); }
        return null;
    }
    private static void trim() { while (CACHE.size() > 512) CACHE.remove(CACHE.keySet().iterator().next()); }
    public static void close() { WORKER.shutdownNow(); CACHE.clear(); }
    private record Entry(String name, long retryAfter) {}
}
