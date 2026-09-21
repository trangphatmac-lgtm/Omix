package cn.omix.util.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MaceSmashTimingTest {
    @Test
    void descendingMaceCanAttackBeforeFullCooldown() {
        assertTrue(MaceSmashTiming.canBypassCooldown(true, true, false, -.08));
        assertTrue(MaceSmashTiming.canBypassCooldown(true, true, false, -1.5));
    }

    @Test
    void risingApexAndLandingDoNotConsumeTheSmashWindow() {
        assertFalse(MaceSmashTiming.canBypassCooldown(true, true, false, .08));
        assertFalse(MaceSmashTiming.canBypassCooldown(true, true, false, 0));
        assertFalse(MaceSmashTiming.canBypassCooldown(true, true, true, -.08));
    }

    @Test
    void requiresActualMaceAndVanillaSmashEligibility() {
        // Vanilla rejects fallDistance <= 1.5 and elytra gliding.
        assertFalse(MaceSmashTiming.canBypassCooldown(true, false, false, -.5));
        assertFalse(MaceSmashTiming.canBypassCooldown(false, true, false, -.5));
    }
}
