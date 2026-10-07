package cn.omix.util.opai.island;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IslandTelemetryTest {
    @Test void scaffoldUsesTickDistanceAndResetsAcrossSessionsAndMissingTicks() {
        var tracker = new ScaffoldBpsTracker();
        Object player = new Object(), world = new Object();
        tracker.sample(player, world, 1, 100, 100, 1);
        assertEquals(0, tracker.blocksPerSecond(player, world));
        tracker.sample(player, world, 2, 100.3, 100.4, 2);
        assertEquals(20, tracker.blocksPerSecond(player, world), .0001);
        for (int frame = 0; frame < 144; frame++) {
            tracker.sample(player, world, 2, 100.3, 100.4, 2);
            assertEquals(20, tracker.blocksPerSecond(player, world), .0001);
        }
        tracker.sample(player, world, 4, 200, 200, 1);
        assertEquals(0, tracker.blocksPerSecond(player, world));
        tracker.sample(player, new Object(), 5, 1000, 1000, 1);
        assertEquals(0, tracker.blocksPerSecond(player, world));
        tracker.sample(player, world, 6, Double.NaN, 0, 1);
        assertEquals(0, tracker.blocksPerSecond(player, world));
    }

    @Test void latencyKeepsVanillaProbesAndExpiresSamplesWithoutReordering() {
        var latency = new DynamicIslandLatency();
        long first = latency.request(100);
        assertNotEquals(0, first);
        assertEquals(0, latency.request(200));
        assertFalse(latency.receive(123, 300));
        assertEquals(70, latency.latency(300, 70));
        long second = latency.request(600);
        assertTrue(latency.receive(second, 650));
        assertEquals(50, latency.latency(650, 70));
        assertTrue(latency.receive(first, 700));
        assertEquals(50, latency.latency(700, 70));
        assertEquals(70, latency.latency(4000, 70));
        latency.reset();
        assertEquals(-1, latency.latency(4100, -1));
    }
}
