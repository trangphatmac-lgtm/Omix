package cn.omix.module.impl.player.chest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChestScreenStateTest {
    @Test
    void immediateEscapeStaysDeferredUntilBothInputAndSprintAreSynchronized() {
        ChestScreenState state = new ChestScreenState();
        state.open(7);
        assertTrue(state.deferClose(7));
        assertFalse(state.shouldClose(7));
        state.completePlayerTick(7, false, true);
        assertFalse(state.shouldClose(7));
        state.completePlayerTick(7, true, false);
        assertFalse(state.shouldClose(7));
        state.completePlayerTick(7, true, true);
        assertTrue(state.shouldClose(7));
        assertFalse(state.deferClose(7));
    }

    @Test
    void instantStealerMustWaitForFirstNeutralPlayerTickEvenWithZeroDelays() {
        ChestScreenState state = new ChestScreenState();
        state.open(8);
        assertFalse(state.ready(8));
        state.completePlayerTick(8, true, true);
        assertTrue(state.ready(8));
        assertFalse(state.shouldClose(8)); // No manual close was requested.
    }

    @Test
    void openingAnotherContainerDropsOldDeferredCloseAndSynchronization() {
        ChestScreenState state = new ChestScreenState();
        state.open(7);
        state.deferClose(7);
        state.completePlayerTick(7, true, true);
        state.open(8);
        assertFalse(state.ready(8));
        state.completePlayerTick(7, true, true);
        assertFalse(state.ready(8));
        state.completePlayerTick(8, true, true);
        assertTrue(state.ready(8));
        assertFalse(state.shouldClose(8));
        assertFalse(state.shouldClose(7));
    }

    @Test
    void reusedSyncIdStillRequiresANewNeutralTick() {
        ChestScreenState state = new ChestScreenState();
        state.open(7);
        state.completePlayerTick(7, true, true);
        state.open(7);
        assertFalse(state.ready(7));
    }

    @Test
    void repeatedEscapeDoesNotLoseThePendingRequest() {
        ChestScreenState state = new ChestScreenState();
        state.open(7);
        assertTrue(state.deferClose(7));
        assertTrue(state.deferClose(7));
        state.completePlayerTick(7, true, true);
        assertTrue(state.shouldClose(7));
        state.reset();
        assertFalse(state.shouldClose(7));
    }

    @Test
    void newMovingInputRevokesReadiness() {
        ChestScreenState state = new ChestScreenState();
        state.open(7);
        state.completePlayerTick(7, true, true);
        assertTrue(state.ready(7));
        state.completePlayerTick(7, false, true);
        assertFalse(state.ready(7));
        assertTrue(state.deferClose(7));
    }

    @Test
    void disconnectDoesNotCloseAFutureScreen() {
        ChestScreenState state = new ChestScreenState();
        state.open(7);
        state.deferClose(7);
        state.reset();
        assertFalse(state.active());
        assertFalse(state.deferClose(7));
        state.completePlayerTick(7, true, true);
        assertFalse(state.shouldClose(7));
        state.open(7);
        state.completePlayerTick(7, true, true);
        assertFalse(state.shouldClose(7));
    }
}
