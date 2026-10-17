package cn.omix.util.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Main-thread combat records. Keys identify entity instances, not reusable network IDs. */
public final class CombatDeathTracker<T> {
    public static final long HIT_WINDOW_MS = 15_000L;
    private final Map<T, Hit> hits = new IdentityHashMap<>();
    private final ArrayDeque<Death<T>> deaths = new ArrayDeque<>();

    private record Death<T>(T entity, String name) {}

    public void hit(T entity, String name, long now) {
        expire(now);
        Hit previous = hits.get(entity);
        // A late damage/attack notification cannot re-arm the same dead entity.
        if (previous == null || !previous.confirmed) hits.put(entity, new Hit(name, now));
    }

    public void death(T entity, long now) {
        expire(now);
        Hit hit = hits.get(entity);
        if (hit == null || hit.confirmed) return;
        hit.confirmed = true;
        deaths.addLast(new Death<>(entity, hit.name));
    }

    public List<T> targets(long now) {
        expire(now);
        return new ArrayList<>(hits.keySet());
    }

    public void removed(T entity) {
        // Removal is not evidence of death; an already confirmed message survives removal.
        hits.remove(entity);
    }

    public List<String> drainDeaths() {
        return drainDeaths(entity -> true);
    }

    /** Filter using the original victim, even after removal. Rejected deaths are consumed too. */
    public List<String> drainDeaths(Predicate<? super T> eligible) {
        List<String> result = new ArrayList<>();
        while (!deaths.isEmpty()) {
            Death<T> death = deaths.removeFirst();
            if (eligible.test(death.entity())) result.add(death.name());
        }
        return result;
    }

    public void clear() {
        hits.clear();
        deaths.clear();
    }

    private void expire(long now) {
        hits.values().removeIf(hit -> now - hit.at > HIT_WINDOW_MS);
    }

    private static final class Hit {
        private final String name;
        private final long at;
        private boolean confirmed;

        private Hit(String name, long at) {
            this.name = name;
            this.at = at;
        }
    }
}
