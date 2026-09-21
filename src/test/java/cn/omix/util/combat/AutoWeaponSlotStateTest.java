package cn.omix.util.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AutoWeaponSlotStateTest {
    private final AutoWeaponSlotState state = new AutoWeaponSlotState();

    @Test
    void restoresExactlyAtDeadline() {
        state.select(4, 1, 100, 20);
        assertTrue(state.owns(1));
        assertEquals(-1, state.expire(1, 119));
        assertEquals(4, state.expire(1, 120));
        assertEquals(-1, state.restore(1));
    }

    @Test
    void repeatedAttacksExtendDeadlineWithoutLosingOriginalSlot() {
        state.select(4, 1, 100, 20);
        state.select(1, 1, 110, 20);
        assertEquals(-1, state.expire(1, 120));
        state.select(1, 2, 125, 20);
        assertEquals(-1, state.expire(2, 144));
        assertEquals(4, state.expire(2, 145));
    }

    @Test
    void manualOrOtherModuleSelectionCancelsRestoration() {
        state.select(4, 1, 100, 20);
        assertEquals(-1, state.expire(3, 120));
        assertFalse(state.owns(1));
        state.select(3, 2, 121, 20);
        assertEquals(3, state.restore(2));
    }

    @Test
    void noSwitchDoesNotClaimSlotAndWorldResetDiscardsOldState() {
        state.select(1, 1, 100, 20);
        assertFalse(state.owns(1));
        assertEquals(-1, state.restore(1));
        state.select(4, 1, 100, 20);
        state.clear();
        assertEquals(-1, state.restore(1));
    }

    @Test
    void disableOnlyRestoresTheSlotStillOwned() {
        state.select(4, 1, 100, 20);
        assertEquals(-1, state.restore(2));
        state.select(2, 1, 100, 20);
        assertEquals(2, state.restore(1));
    }
}
