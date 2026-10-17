package cn.omix.util.world.anticheat;

import java.util.*;
import java.util.function.IntPredicate;

/** HypixelACV2's cumulative flag weights; the percentage is a score, not a probability. */
public final class AntiCheatSuspicion {
    private static final int MAX_PLAYERS = 2048;
    private final Map<UUID, State> players = new LinkedHashMap<>();
    private Object session;

    private static final class State {
        int score, alarmLevel;
    }

    public record Update(int score, List<Integer> alarms) {}
    public record Row(UUID player, int score) {}

    /** Keep scores across respawns/world changes on the same network connection. */
    public void synchronizeSession(Object connection) {
        if (session != connection) {
            clear();
            session = connection;
        }
    }

    /** Call only for a flag that has passed its detector's threshold and cooldown. */
    public Update flag(UUID player, String check, IntPredicate alarmEnabled) {
        if (!players.containsKey(player) && players.size() >= MAX_PLAYERS)
            players.remove(players.keySet().iterator().next());
        State state = players.computeIfAbsent(player, ignored -> new State());
        state.score = Math.min(100, state.score + increment(check));
        List<Integer> alarms = new ArrayList<>(3);
        for (int level : new int[]{50, 80, 100}) {
            if (state.alarmLevel < level && state.score >= level && alarmEnabled.test(level)) {
                state.alarmLevel = level;
                alarms.add(level);
            }
        }
        return new Update(state.score, List.copyOf(alarms));
    }

    public int score(UUID player) {
        State state = players.get(player);
        return state == null ? 0 : state.score;
    }

    /** Filtering the visible list never deletes the accumulated scores. */
    public List<Row> ranking(Collection<UUID> visible, int limit) {
        return visible.stream().distinct().map(id -> new Row(id, score(id)))
                .filter(row -> row.score() > 0)
                .sorted(Comparator.comparingInt(Row::score).reversed().thenComparing(Row::player))
                .limit(Math.max(0, limit)).toList();
    }

    public void clear() {
        players.clear();
        session = null;
    }

    /** Exact source weights. Unlisted checks (including the original OpenB4 checks) gain 8. */
    public static int increment(String check) {
        return switch (check) {
            case "Vulcan Scaffold" -> 20;
            case "NCP Scaffold", "NCP KillAura", "Vulcan KillAura", "Hit Miss Ratio",
                    "Grim Reach", "AAC Reach", "Verus Reach", "Grim Hitbox", "NCP HitboxV" -> 15;
            case "Karhu MultiAura", "Grim MultiAuraB" -> 25;
            case "NCP Fly", "Grim Speed", "Vulcan Speed", "Matrix Acceleration",
                    "Grim Velocity", "Verus Velocity", "Pika Velocity", "NCP Velocity", "Verus VelocityDir",
                    "NCP AutoClicker", "Karhu AutoClicker", "Matrix AutoClicker", "Vulcan Criticals",
                    "NCP NoSlow", "Verus NoSlow", "NCP Hover", "NCP MoveConsistency", "NCP FastEat", "Grim AirSpeed" -> 10;
            case "Grim NoFall", "Grim GroundSpoof", "Grim Timer", "Grim NegativeTimer",
                    "Vulcan Glide", "Grim Elude", "NCP Regen" -> 12;
            case "Grim Rotation", "Vulcan Rotation", "Grim Aim", "Vulcan Aim", "Karhu SmoothAim",
                    "Grim AimGCD", "Intave GCD", "Vulcan DivisorY", "Vulcan AimFlick", "Karhu PitchLock",
                    "Matrix SilentAim", "Matrix Angle", "Grim AimAccel", "NCP AimSnap", "Karhu AimTracking",
                    "Grim SimAim", "Intave Heuristic", "Intave Rhythm", "Grim SimHitbox", "Grim Backtrack", "NCP FakeLag" -> 6;
            case "NCP BedNuker", "Karhu BedNuker", "Grim BedSnap" -> 5;
            default -> 8;
        };
    }

    /** ARGB colors from the original CheatUI's 20/40/70/100 bands. */
    public static int color(int score) {
        return score >= 100 ? 0xFFFF55FF : score >= 70 ? 0xFFFF5555 : score >= 40 ? 0xFFFFAA00
                : score >= 20 ? 0xFFFFFF55 : 0xFFAAAAAA;
    }
}
