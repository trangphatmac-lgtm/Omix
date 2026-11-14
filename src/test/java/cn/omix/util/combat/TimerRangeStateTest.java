package cn.omix.util.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimerRangeStateTest {
    @Test
    void modeAMatchesEveryShortTargetSequenceFromSouthside() {
        for (int sequence = 0; sequence < 256; sequence++) {
            var state = new TimerRangeState();
            String originalState = "out";
            for (int tick = 0; tick < 8; tick++) {
                boolean hasTarget = (sequence & (1 << tick)) != 0;
                if (!hasTarget) {
                    originalState = originalState.equals("in") ? "slow" : "out";
                } else {
                    originalState = originalState.equals("out") ? "teleport" : "in";
                }
                float expected = switch (originalState) {
                    case "teleport" -> 4.0F;
                    case "slow" -> 0.2F;
                    default -> 1.0F;
                };
                assertEquals(expected, state.tickA(hasTarget, 4.0F, 0.2F));
            }
        }
    }

    @Test
    void modeBUsesInteractionCountAndStrictDistanceThreshold() {
        var state = new TimerRangeState();
        for (int packet = 0; packet < 14; packet++) {
            assertFalse(state.onInteraction(true, 4.0, 3.5F, 8, 15));
            for (int tick = 0; tick < 20; tick++) assertEquals(1.0F, state.tickB(8, 1.5F, 0.4F));
        }
        assertFalse(state.onInteraction(true, 3.5, 3.5F, 8, 15));
        assertTrue(state.onInteraction(true, 3.51, 3.5F, 8, 15));
        assertDefaultCycle(state);
    }

    @Test
    void modeBDefaultCycleStartsNormalThenSlowsThenBoosts() {
        var state = new TimerRangeState();
        assertTrue(state.onInteraction(true, 4.0, 3.5F, 8, 1));
        assertDefaultCycle(state);
    }

    @Test
    void modeBPreservesRemainingTickRatiosAcrossAllTickSettings() {
        for (int ticks = 1; ticks <= 30; ticks++) {
            var state = new TimerRangeState();
            assertTrue(state.onInteraction(true, 7.0, 6.0F, ticks, 1));
            for (int remaining = ticks; remaining > 0; remaining--) {
                float progress = remaining / (float) ticks;
                float expected = progress < 0.5D ? 20.0F : progress < 0.9D ? 0.05F : 1.0F;
                assertEquals(expected, state.tickB(ticks, 20.0F, 0.05F));
            }
            assertFalse(state.hasActiveCycle());
            assertEquals(1.0F, state.tickB(ticks, 20.0F, 0.05F));
        }
    }

    @Test
    void nonAttackPacketsOnlyCountWhenNoCycleIsActive() {
        var state = new TimerRangeState();
        assertTrue(state.onInteraction(false, 4.0, 3.5F, 8, 1));
        assertFalse(state.onInteraction(false, 4.0, 3.5F, 8, 1));
        assertDefaultCycle(state);
        assertFalse(state.onInteraction(true, 4.0, 3.5F, 8, 2));
        assertTrue(state.onInteraction(true, 4.0, 3.5F, 8, 2));
    }

    @Test
    void anAttackCanRestartAnActiveCycle() {
        var state = new TimerRangeState();
        assertTrue(state.onInteraction(true, 4.0, 3.5F, 8, 1));
        state.tickB(8, 1.5F, 0.4F);
        state.tickB(8, 1.5F, 0.4F);
        assertTrue(state.onInteraction(true, 4.0, 3.5F, 8, 1));
        assertDefaultCycle(state);
    }

    @Test
    void resettingSpeedAfterAnInteractionKeepsTheCycleCountdown() {
        var state = new TimerRangeState();
        assertTrue(state.onInteraction(true, 4.0, 3.5F, 8, 1));
        assertEquals(1.0F, state.tickB(8, 1.5F, 0.4F));
        assertFalse(state.onInteraction(true, 2.0, 3.5F, 8, 15));
        assertTrue(state.hasActiveCycle());
        assertEquals(0.4F, state.tickB(8, 1.5F, 0.4F));
    }

    @Test
    void resetClearsBothModesAndInteractionCount() {
        var state = new TimerRangeState();
        state.tickA(true, 4.0F, 0.2F);
        state.tickA(true, 4.0F, 0.2F);
        assertTrue(state.onInteraction(true, 4.0, 3.5F, 8, 1));
        assertFalse(state.onInteraction(true, 4.0, 3.5F, 8, 2));
        state.reset();
        assertFalse(state.hasActiveCycle());
        assertEquals(1.0F, state.tickB(8, 1.5F, 0.4F));
        assertFalse(state.onInteraction(true, 4.0, 3.5F, 8, 2));
        assertEquals(4.0F, state.tickA(true, 4.0F, 0.2F));
    }

    private void assertDefaultCycle(TimerRangeState state) {
        float[] speeds = {1.0F, 0.4F, 0.4F, 0.4F, 0.4F, 1.5F, 1.5F, 1.5F};
        for (float speed : speeds) assertEquals(speed, state.tickB(8, 1.5F, 0.4F));
        assertFalse(state.hasActiveCycle());
        assertEquals(1.0F, state.tickB(8, 1.5F, 0.4F));
    }
}
