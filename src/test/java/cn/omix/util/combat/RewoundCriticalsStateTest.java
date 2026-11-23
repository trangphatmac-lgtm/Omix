package cn.omix.util.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RewoundCriticalsStateTest {
    @Test
    void alternatesGroundOffsetAndTracksReportedFall() {
        RewoundCriticalsState state = new RewoundCriticalsState();

        var first = state.apply(64, true, false, 64, true, false);
        assertEquals(64.0301, first.y(), 1.0E-8);
        assertFalse(first.onGround());
        assertFalse(state.hasServerFallDistance());

        var second = state.apply(first.y(), false, false, 64, true, false);
        assertEquals(64, second.y());
        assertFalse(second.onGround());
        assertFalse(state.hasServerFallDistance());

        var third = state.apply(64, false, false, 64, true, false);
        assertEquals(64, third.y());
        assertFalse(third.onGround());
        assertTrue(state.hasServerFallDistance());
    }

    @Test
    void predictsLandingAfterThreeBlocks() {
        RewoundCriticalsState state = new RewoundCriticalsState();
        state.apply(70, false, false, 70, false, false);
        var landing = state.apply(66.9, false, false, 66.9, true, false);
        assertEquals(66.9301, landing.y(), 1.0E-8);
        assertTrue(landing.onGround());
        assertTrue(state.hasServerFallDistance());
        state.apply(66.9, true, false, 66.9, true, false);
        assertFalse(state.hasServerFallDistance());
    }

    @Test
    void jumpAndBlockBreakingSuspendSpoofingForThatTick() {
        RewoundCriticalsState state = new RewoundCriticalsState();
        state.jumped();
        assertEquals(new RewoundCriticalsState.Motion(64, true),
                state.apply(64, true, false, 64, true, false));
        state.beginTick();
        assertEquals(new RewoundCriticalsState.Motion(64, true),
                state.apply(64, true, false, 64, true, true));
        assertFalse(state.apply(64, true, false, 64, true, false).onGround());
    }

    @Test
    void fluidAndResetClearFallHistory() {
        RewoundCriticalsState state = new RewoundCriticalsState();
        state.apply(70, false, false, 70, false, false);
        state.apply(69, false, false, 69, false, false);
        assertTrue(state.hasServerFallDistance());
        state.apply(68, false, true, 68, false, false);
        assertFalse(state.hasServerFallDistance());
        state.reset();
        state.apply(60, false, false, 60, false, false);
        assertFalse(state.hasServerFallDistance());
    }
}
