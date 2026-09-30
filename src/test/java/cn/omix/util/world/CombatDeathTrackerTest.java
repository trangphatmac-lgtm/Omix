package cn.omix.util.world;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CombatDeathTrackerTest {
    private final CombatDeathTracker<Object> tracker = new CombatDeathTracker<>();
    private final Object victim = new Object();

    @Test
    void deathStatusAndZeroHealthOnlyProduceOneMessage() {
        tracker.hit(victim, "victim", 0);
        tracker.death(victim, 100); // Status 3, even when health still reads 20.
        tracker.death(victim, 110); // Metadata / tick observes zero health.
        assertEquals(List.of("victim"), tracker.drainDeaths());
        tracker.death(victim, 120);
        assertTrue(tracker.drainDeaths().isEmpty());
    }

    @Test
    void deathFollowedByRemovalBeforeNextTickStillSends() {
        tracker.hit(victim, "victim", 0);
        tracker.death(victim, 10);
        tracker.removed(victim);
        assertEquals(List.of("victim"), tracker.drainDeaths());
    }

    @Test
    void disconnectOrLeavingRangeDoesNotCountAsDeath() {
        tracker.hit(victim, "victim", 0);
        tracker.removed(victim);
        tracker.death(victim, 10);
        assertTrue(tracker.drainDeaths().isEmpty());
    }

    @Test
    void unrelatedDeathDoesNotSend() {
        tracker.death(victim, 0);
        assertTrue(tracker.drainDeaths().isEmpty());
    }

    @Test
    void staleAttackExpiresButRepeatedHitsRefreshTheWindow() {
        tracker.hit(victim, "victim", 0);
        tracker.death(victim, CombatDeathTracker.HIT_WINDOW_MS + 1);
        assertTrue(tracker.drainDeaths().isEmpty());
        tracker.hit(victim, "victim", 20_000);
        tracker.hit(victim, "victim", 30_000);
        tracker.death(victim, 44_000);
        assertEquals(List.of("victim"), tracker.drainDeaths());
    }

    @Test
    void lateDamageDoesNotRearmAnAlreadyConfirmedDeath() {
        tracker.hit(victim, "victim", 0);
        tracker.death(victim, 10);
        tracker.hit(victim, "victim", 20);
        tracker.death(victim, 30);
        assertEquals(List.of("victim"), tracker.drainDeaths());
    }

    @Test
    void worldChangeOrToggleDiscardsBothHitsAndQueuedMessages() {
        Object other = new Object();
        tracker.hit(victim, "victim", 0);
        tracker.hit(other, "other", 0);
        tracker.death(victim, 10);
        tracker.clear();
        tracker.death(other, 20);
        assertTrue(tracker.drainDeaths().isEmpty());
        assertTrue(tracker.targets(20).isEmpty());
    }

    @Test
    void respawnedEntitiesWithEqualNetworkIdsDoNotShareRecords() {
        // Minecraft entity equality may use an entity ID that a server can reuse.
        Object firstLife = new String("same-id");
        Object secondLife = new String("same-id");
        tracker.hit(firstLife, "victim", 0);
        tracker.death(secondLife, 10);
        assertTrue(tracker.drainDeaths().isEmpty());
        tracker.death(firstLife, 20);
        tracker.hit(secondLife, "victim", 30);
        tracker.death(secondLife, 40);
        assertEquals(List.of("victim", "victim"), tracker.drainDeaths());
    }

    @Test
    void multipleVictimsAreQueuedInDeathOrder() {
        Object other = new Object();
        tracker.hit(victim, "first", 0);
        tracker.hit(other, "second", 0);
        tracker.death(other, 10);
        tracker.death(victim, 20);
        assertEquals(List.of("second", "first"), tracker.drainDeaths());
    }
}
