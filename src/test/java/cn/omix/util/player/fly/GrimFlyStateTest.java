package cn.omix.util.player.fly;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrimFlyStateTest {
    private final GrimFlyState state = new GrimFlyState();

    @Test
    void savedLegacyNamesMigrateToEnglish() {
        assertEquals("Flat", GrimFlyState.migrateMode("1平"));
        assertEquals("Free", GrimFlyState.migrateMode("2随意"));
        assertEquals("Always", GrimFlyState.migrateMode("3一直"));
        for (String mode : new String[]{"Flat", "Free", "Always", "unknown"}) {
            assertEquals(mode, GrimFlyState.migrateMode(mode));
        }
    }

    private boolean motion(String mode, double y, double velocityY, boolean jump, boolean sneak) {
        return state.onMotion(mode, false, y, velocityY, jump, sneak);
    }

    @Test
    void alwaysModeSendsEverySecondMotionAndSneakTemporarilySuppressesIt() {
        assertFalse(motion("Always", 64, 0, false, false));
        assertTrue(motion("Always", 64, 0, false, false));
        assertFalse(motion("Always", 64, 0, false, false));
        assertFalse(motion("Always", 64, 0, false, true));
        assertFalse(motion("Always", 64, 0, false, false));
        assertTrue(motion("Always", 64, 0, false, false));
    }

    @Test
    void flatModeLatchesAtReferenceFractionAndSneakClearsLatch() {
        assertFalse(motion("Flat", 64.2494, 0, false, false));
        assertFalse(motion("Flat", 64.2494, 0, false, false));
        assertFalse(motion("Flat", 64.24919, 0, false, false));
        assertTrue(motion("Flat", 65, 0, false, false));
        assertFalse(motion("Flat", 65, 0, false, true));
        assertFalse(motion("Flat", 65, 0, false, false));
    }

    @Test
    void freeModeNeedsAHeightBeforeItCanHover() {
        assertFalse(motion("Free", 64, 0, false, false));
        assertFalse(motion("Free", 64, 0, false, false));
        state.captureCorrectionHeight(64);
        assertFalse(motion("Free", 64, 0, false, false));
        assertTrue(motion("Free", 64, 0, false, false));
        assertFalse(motion("Free", 65, 0, false, false));
        assertFalse(motion("Free", 65, 0, false, false));
    }

    @Test
    void correctionOnlySuppliesTheFirstHeight() {
        state.captureCorrectionHeight(-20);
        state.captureCorrectionHeight(64);
        assertFalse(motion("Free", 0, 0, false, false));
        assertFalse(motion("Free", 0, 0, false, false));
        assertFalse(motion("Free", -20, 0, false, false));
        assertTrue(motion("Free", -20, 0, false, false));
    }

    @Test
    void releasingJumpSelectsCurrentHeightIncludingBelowZero() {
        assertFalse(motion("Free", -20, 0.42, true, false));
        assertTrue(motion("Free", -19, 0.2, true, false));
        assertFalse(motion("Free", -18, 0.1, false, false));
        assertTrue(motion("Free", -18, 0, false, false));
        assertFalse(motion("Free", -17, 0, false, false));
        assertFalse(motion("Free", -17, 0, false, false));
    }

    @Test
    void releasingSneakSelectsLowerHeightEvenWhenJumpIsHeld() {
        state.captureCorrectionHeight(64);
        assertFalse(motion("Free", 63, -0.2, true, true));
        assertFalse(motion("Free", 62, -0.2, true, true));
        assertFalse(motion("Free", 62, 0, false, false));
        assertTrue(motion("Free", 62, 0, false, false));
        assertFalse(motion("Free", 63, 0, false, false));
        assertFalse(motion("Free", 63, 0, false, false));
    }

    @Test
    void maxVelocityWaitsForApexAfterAValidImpulse() {
        assertFalse(state.onMotion("Free", true, 64, 0.4, true, false));
        assertTrue(state.onMotion("Free", true, 65, 0.3, true, false));
        assertFalse(state.onMotion("Free", true, 66, 0.1, false, false));
        assertFalse(state.onMotion("Free", true, 66.5, 0.05, false, false));
        assertFalse(state.onMotion("Free", true, 66.4, -0.1, false, false));
        assertTrue(state.onMotion("Free", true, 66.5, 0, false, false));
        assertFalse(state.onMotion("Free", true, 67, 0, false, false));
        assertFalse(state.onMotion("Free", true, 67, 0, false, false));
    }

    @Test
    void maxVelocityUsesAbsoluteThresholdAndRejectsWeakImpulse() {
        assertFalse(state.onMotion("Free", true, 64, 0.399, true, false));
        assertTrue(state.onMotion("Free", true, 65, 0.2, true, false));
        assertFalse(state.onMotion("Free", true, 66, 0, false, false));
        assertFalse(state.onMotion("Free", true, 65, -0.1, false, false));
        assertFalse(state.onMotion("Free", true, 64, -0.4, true, false));
        assertTrue(state.onMotion("Free", true, 63, -0.2, false, false));
    }

    @Test
    void releasingSneakCancelsApexWait() {
        state.onMotion("Free", true, 64, 0.42, true, false);
        state.onMotion("Free", true, 65, 0.3, false, true);
        assertFalse(state.onMotion("Free", true, 64, 0.2, false, false));
        assertTrue(state.onMotion("Free", true, 64, 0.1, false, false));
    }

    @Test
    void resetClearsHeightLatchKeyEdgesAndCadence() {
        state.captureCorrectionHeight(64);
        motion("Free", 64, 0.42, true, true);
        state.reset();
        assertFalse(motion("Free", 64, 0, false, false));
        assertFalse(motion("Free", 64, 0, false, false));
        assertFalse(motion("Always", 64, 0, false, false));
        assertTrue(motion("Always", 64, 0, false, false));
        state.reset();
        assertFalse(motion("Flat", 64.24919, 0, false, false));
        assertTrue(motion("Flat", 65, 0, false, false));
        state.reset();
        assertFalse(motion("Flat", 65, 0, false, false));
        assertFalse(motion("Flat", 65, 0, false, false));
    }
}
