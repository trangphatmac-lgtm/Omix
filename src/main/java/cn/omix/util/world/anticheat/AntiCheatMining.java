package cn.omix.util.world.anticheat;

import java.util.HashMap;
import java.util.Map;

/** Short-lived break evidence, keyed by both actor and block (never by packet identity). */
public final class AntiCheatMining<K> {
    public record Evidence<K>(int entityId, K position, int progress, long at) {}
    private final Map<Integer, Evidence<K>> players = new HashMap<>();
    private final Map<K, Evidence<K>> completed = new HashMap<>();

    public void animation(int entityId, K position, int progress, long now) {
        expire(now);
        if (progress >= 0 && progress <= 9) {
            if (players.size() >= 1024) players.clear();
            players.put(entityId, new Evidence<>(entityId, position, progress, now));
            completed.remove(position);
        } else {
            Evidence<K> previous = players.remove(entityId);
            if (previous != null && previous.position.equals(position)) {
                if (completed.size() >= 1024) completed.clear();
                completed.put(position, new Evidence<>(entityId, position, previous.progress, now));
            }
        }
    }

    public Evidence<K> consume(K position, long now) {
        expire(now);
        return completed.remove(position);
    }

    public void expire(long now) {
        players.values().removeIf(value -> now - value.at > 2000);
        completed.values().removeIf(value -> now - value.at > 1000);
    }

    public void clear() { players.clear(); completed.clear(); }
}
