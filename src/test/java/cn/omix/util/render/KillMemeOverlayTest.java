package cn.omix.util.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KillMemeOverlayTest {
    @Test
    void holdsThenBlinksAndExpiresAtItsOwnDeadline() {
        var overlay = new KillMemeOverlay();
        long start = 12_000_000_000L;
        overlay.spawn(10, 65, -20, start, 3);
        var marker = overlay.active(start).getFirst();
        assertEquals(10, marker.x());
        assertEquals(65, marker.y());
        assertEquals(-20, marker.z());
        assertEquals(0, marker.alpha(start - 1));
        assertEquals(255, marker.alpha(start));
        assertEquals(255, marker.alpha(start + 499_999_999L));
        assertEquals(255, marker.alpha(start + 500_000_000L));
        assertEquals(0, marker.alpha(start + 750_000_000L));
        assertEquals(255, marker.alpha(start + 1_000_000_000L));
        assertFalse(marker.expired(start + 2_999_999_999L));
        assertEquals(0, marker.alpha(start + 3_000_000_000L));
        assertTrue(overlay.active(start + 3_000_000_000L).isEmpty());
    }

    @Test
    void keepsIndependentDeathPositionsAndLifetimesAndClearsAll() {
        var overlay = new KillMemeOverlay();
        overlay.spawn(1, 2, 3, 0, 3);
        overlay.spawn(4, 5, 6, 1_000_000_000L, 4);
        assertEquals(2, overlay.active(2_000_000_000L).size());
        var remaining = overlay.active(3_000_000_000L);
        assertEquals(1, remaining.size());
        assertEquals(4, remaining.getFirst().x());
        overlay.clear();
        assertTrue(overlay.active(3_000_000_001L).isEmpty());
    }

    @Test
    void rapidKillsEvictOldestMarkerInsteadOfGrowingWithoutLimit() {
        var overlay = new KillMemeOverlay();
        for (int i = 0; i < 40; i++) overlay.spawn(i, 0, 0, i, 3);
        var markers = overlay.active(40);
        assertEquals(16, markers.size());
        assertEquals(24, markers.getFirst().x());
        assertEquals(39, markers.getLast().x());
    }

    @Test
    void ringAndArrowUseFrontFacingGuiQuadsWithinDeclaredBounds() {
        float[] mesh = KillMemeOverlay.mesh();
        assertEquals(0, mesh.length % 8);
        for (int i = 0; i < mesh.length; i += 8) {
            double area = 0;
            for (int j = 0; j < 4; j++) {
                int p = i + j * 2, next = i + ((j + 1) % 4) * 2;
                assertTrue(Float.isFinite(mesh[p]) && Float.isFinite(mesh[p + 1]));
                assertTrue(mesh[p] >= -94 && mesh[p] <= 172);
                assertTrue(mesh[p + 1] >= -180 && mesh[p + 1] <= 94);
                area += mesh[p] * mesh[next + 1] - mesh[next] * mesh[p + 1];
            }
            assertTrue(area < 0, "Every quad must face the GUI camera: " + i / 8);
        }
        // The first 96 quads form a hollow ring, not a disk covering the death position.
        for (int i = 0; i < 96 * 8; i += 2) {
            double radius = Math.hypot(mesh[i], mesh[i + 1]);
            assertTrue(radius >= 88.99 && radius <= 92.01);
        }
    }
}
