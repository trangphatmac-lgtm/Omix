package cn.omix.util.world.anticheat;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.*;

class AntiCheatSuspicionTest {
    private static final IntPredicate ALL = level -> true;
    private static final IntPredicate NONE = level -> false;

    @Test void sourceWeightExamplesAndUnlistedLegacyChecks() {
        assertEquals(25, AntiCheatSuspicion.increment("Karhu MultiAura"));
        assertEquals(25, AntiCheatSuspicion.increment("Grim MultiAuraB"));
        assertEquals(20, AntiCheatSuspicion.increment("Vulcan Scaffold"));
        assertEquals(15, AntiCheatSuspicion.increment("NCP HitboxV"));
        assertEquals(12, AntiCheatSuspicion.increment("Grim NegativeTimer"));
        assertEquals(10, AntiCheatSuspicion.increment("Verus VelocityDir"));
        assertEquals(8, AntiCheatSuspicion.increment("Vulcan RotationSpeed"));
        assertEquals(6, AntiCheatSuspicion.increment("Intave Rhythm"));
        assertEquals(5, AntiCheatSuspicion.increment("Grim BedSnap"));
        for (var check : AntiCheatCheck.values()) assertEquals(8, AntiCheatSuspicion.increment(check.displayName));
    }

    @Test void mixedFlagsAccumulateWithoutDecayAndClampAt100() {
        var scores = new AntiCheatSuspicion();
        UUID player = UUID.randomUUID();
        assertEquals(0, scores.score(player));
        scores.flag(player, "Vulcan Scaffold", NONE);
        scores.flag(player, "Grim Aim", NONE);
        assertEquals(26, scores.score(player));
        assertTrue(scores.ranking(Set.of(), 15).isEmpty());
        assertEquals(26, scores.score(player));
        for (int i = 0; i < 100; i++) scores.flag(player, "Grim BedSnap", NONE);
        assertEquals(100, scores.score(player));
        assertEquals(100, scores.ranking(Set.of(player), 15).getFirst().score());
    }

    @Test void thresholdsFireOnceAtCrossingEvenWhenScoreSkipsTheExactValue() {
        var scores = new AntiCheatSuspicion();
        UUID player = UUID.randomUUID();
        List<Integer> alarms = new ArrayList<>();
        for (int i = 0; i < 12; i++) alarms.addAll(scores.flag(player, "NCP Fly", ALL).alarms());
        assertEquals(List.of(50, 80, 100), alarms);
        scores.clear();
        for (int i = 0; i < 6; i++) scores.flag(player, "Grim Aim", ALL); // 36
        assertEquals(List.of(50), scores.flag(player, "Karhu MultiAura", ALL).alarms()); // 61
        assertEquals(List.of(80), scores.flag(player, "Karhu MultiAura", ALL).alarms()); // 86
        assertEquals(List.of(100), scores.flag(player, "Karhu MultiAura", ALL).alarms()); // capped
    }

    @Test void disabledAlarmsCatchUpOnNextFlagButNeverReplayBelowHighestAlarm() {
        var scores = new AntiCheatSuspicion();
        UUID player = UUID.randomUUID();
        for (int i = 0; i < 4; i++) assertTrue(scores.flag(player, "Karhu MultiAura", NONE).alarms().isEmpty());
        assertEquals(List.of(50, 80, 100), scores.flag(player, "Grim Aim", ALL).alarms());
        assertTrue(scores.flag(player, "Grim Aim", ALL).alarms().isEmpty());
        scores.clear();
        for (int i = 0; i < 4; i++) scores.flag(player, "Karhu MultiAura", level -> level == 80);
        assertEquals(List.of(100), scores.flag(player, "Grim Aim", ALL).alarms());
    }

    @Test void uuidScoresSurviveVisibilityAndSameConnectionButResetWithSession() {
        var scores = new AntiCheatSuspicion();
        Object connection = new Object();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        scores.synchronizeSession(connection);
        for (int i = 0; i < 2; i++) scores.flag(first, "Karhu MultiAura", ALL);
        assertEquals(0, scores.score(second));
        assertTrue(scores.ranking(List.of(second), 15).isEmpty());
        scores.synchronizeSession(connection);
        assertEquals(50, scores.score(first));
        assertTrue(scores.flag(first, "Grim Aim", ALL).alarms().isEmpty());
        scores.synchronizeSession(new Object());
        assertEquals(0, scores.score(first));
        scores.flag(first, "Karhu MultiAura", ALL);
        assertEquals(List.of(50), scores.flag(first, "Karhu MultiAura", ALL).alarms());
        scores.synchronizeSession(null);
        assertEquals(0, scores.score(first));
        scores.flag(first, "NCP Fly", ALL);
        scores.clear();
        assertEquals(0, scores.score(first));
    }

    @Test void rankingIsFilteredDescendingStableAndLimitedTo15() {
        var scores = new AntiCheatSuspicion();
        List<UUID> visible = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            UUID player = new UUID(0, i);
            visible.add(player);
            for (int j = 0; j <= i; j++) scores.flag(player, "Grim BedSnap", NONE);
        }
        visible.add(new UUID(0, 19)); // Duplicate world entries must not duplicate a row.
        visible.add(UUID.randomUUID()); // Zero scores stay hidden.
        var ranked = scores.ranking(visible, 15);
        assertEquals(15, ranked.size());
        assertEquals(new UUID(0, 19), ranked.getFirst().player());
        assertEquals(30, ranked.getLast().score());
        Collections.reverse(visible);
        assertEquals(ranked, scores.ranking(visible, 15));
        UUID tied = new UUID(0, 100);
        for (int i = 0; i < 4; i++) scores.flag(tied, "Karhu MultiAura", NONE);
        assertEquals(new UUID(0, 19), scores.ranking(List.of(tied, new UUID(0, 19)), 1).getFirst().player());
        assertTrue(scores.ranking(visible, 0).isEmpty());
    }

    @Test void displayColorsMatchScriptBandsRatherThanAlarmLevels() {
        assertEquals(0xFFAAAAAA, AntiCheatSuspicion.color(19));
        assertEquals(0xFFFFFF55, AntiCheatSuspicion.color(20));
        assertEquals(0xFFFFFF55, AntiCheatSuspicion.color(39));
        assertEquals(0xFFFFAA00, AntiCheatSuspicion.color(40));
        assertEquals(0xFFFFAA00, AntiCheatSuspicion.color(69));
        assertEquals(0xFFFF5555, AntiCheatSuspicion.color(70));
        assertEquals(0xFFFF5555, AntiCheatSuspicion.color(99));
        assertEquals(0xFFFF55FF, AntiCheatSuspicion.color(100));
    }

    @Test void longConnectionsHaveBoundedScoreHistory() {
        var scores = new AntiCheatSuspicion();
        for (int i = 0; i <= 2048; i++) scores.flag(new UUID(0, i), "Grim Aim", NONE);
        assertEquals(0, scores.score(new UUID(0, 0)));
        assertEquals(6, scores.score(new UUID(0, 1)));
        assertEquals(6, scores.score(new UUID(0, 2048)));
    }
}
