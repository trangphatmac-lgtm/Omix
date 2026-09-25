package cn.omix.util.combat.projectile;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProjectileSlotStateTest {
    @Test void deferredReleaseWaitsUntilFlushAndKeepsTheFirstOriginalSlot() {
        var slots = new ProjectileSlotState();
        slots.select(1, 4);
        slots.select(4, 5);
        slots.deferRelease();
        assertTrue(slots.owns(5));
        assertEquals(1, slots.flush(5));
        assertFalse(slots.owns(5));
        assertEquals(-1, slots.flush(5));
    }

    @Test void renewalCancelsDeferredRelease() {
        var slots = new ProjectileSlotState();
        slots.select(0, 3);
        slots.deferRelease();
        slots.select(3, 3);
        assertEquals(-1, slots.flush(3));
        assertEquals(0, slots.release(3));
    }

    @Test void manualOrOtherModuleSelectionIsNeverOverwritten() {
        var slots = new ProjectileSlotState();
        slots.select(0, 3);
        slots.deferRelease();
        assertEquals(-1, slots.flush(7));
        slots.select(7, 3);
        slots.observe(2);
        assertEquals(-1, slots.release(2));
    }
}
