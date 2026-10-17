package cn.omix.util.world.anticheat;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class AntiCheatAlerts {
    private final Map<UUID, Map<String, Long>> flags = new HashMap<>();

    public boolean allow(UUID player, AntiCheatCheck check, long now, long interval) {
        return allow(player, check.name(), now, interval);
    }

    public boolean allow(UUID player, String check, long now, long interval) {
        var checks = flags.computeIfAbsent(player, ignored -> new HashMap<>());
        Long last = checks.get(check);
        if (last != null && interval > 0 && now - last <= interval) return false;
        checks.put(check, now);
        return true;
    }

    public void retain(Set<UUID> present) { flags.keySet().retainAll(present); }
    public void clear() { flags.clear(); }
}
