package cn.omix.module.impl.combat;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CriticalsTimingTest {
    @Test
    void usesMaximumOfCooldownAndVerticalTime() {
        assertEquals(2F, CriticalsTiming.waitTicks(.16, 1F, 12.5F));
        assertEquals(5.625F, CriticalsTiming.waitTicks(.16, .5F, 12.5F), .000001F);
        assertEquals(0F, CriticalsTiming.waitTicks(-.08, 1F, 12.5F));
    }

    @Test
    void includesBudgetEqualityAndTruncatesPredictionHorizon() {
        CriticalsTiming timing = new CriticalsTiming();
        AtomicInteger horizon = new AtomicInteger(-1);
        assertTrue(timing.shouldDefer(false, 0, 0F, false, .16, 1F, 12.5F, 2F,
                ticks -> { horizon.set(ticks); return false; }));
        assertEquals(2, horizon.get());
        assertFalse(timing.shouldDefer(false, 0, 0F, false, .24, 1F, 12.5F, 2F,
                ticks -> { fail("Over-budget waits must not run prediction"); return false; }));
    }

    @Test
    void landingAllowsAttackAndNoLandingDefersIt() {
        CriticalsTiming timing = new CriticalsTiming();
        assertFalse(timing.shouldDefer(false, 0, 0F, false, .08, 1F, 12.5F, 2F, ticks -> true));
        assertTrue(timing.shouldDefer(false, 0, 0F, false, .08, 1F, 12.5F, 2F, ticks -> false));
    }

    @Test
    void exactNegativeThresholdStillWaitsWithZeroPredictionSteps() {
        CriticalsTiming timing = new CriticalsTiming();
        assertTrue(timing.shouldDefer(false, 0, 0F, false, -.08, 1F, 12.5F, 2F,
                ticks -> { assertEquals(0, ticks); return false; }));
        assertFalse(timing.shouldDefer(false, 0, 0F, false, -.081, 1F, 12.5F, 2F,
                ticks -> { fail("Already falling fast enough"); return false; }));
    }

    @Test
    void disallowedCritDoesNotPredictOrReplaceCache() {
        CriticalsTiming timing = new CriticalsTiming();
        assertFalse(timing.shouldDefer(true, 0, 9F, true, -.2, 1F, 12.5F, 2F,
                ticks -> { fail("Cannot crit"); return false; }));
        assertTrue(timing.shouldDefer(true, 1, 8F, false, 0, 1F, 12.5F, 2F, ticks -> false));
    }

    @Test
    void modernCacheAllowsEqualOrLowerDamageDuringHurtTime() {
        CriticalsTiming timing = new CriticalsTiming();
        assertFalse(timing.shouldDefer(true, 0, 7F, false, -.2, 1F, 12.5F, 2F, ticks -> false));
        assertFalse(timing.shouldDefer(true, 1, 6F, false, 0, 1F, 12.5F, 2F, ticks -> false));
        assertFalse(timing.shouldDefer(true, 1, 7F, false, 0, 1F, 12.5F, 2F, ticks -> false));
        assertTrue(timing.shouldDefer(true, 1, 8F, false, 0, 1F, 12.5F, 2F, ticks -> false));
        assertTrue(timing.shouldDefer(true, 0, 6F, false, 0, 1F, 12.5F, 2F, ticks -> false));
    }

    @Test
    void heypixelNeitherReadsNorWritesModernCache() {
        CriticalsTiming timing = new CriticalsTiming();
        assertFalse(timing.shouldDefer(true, 0, 7F, false, -.2, 1F, 12.5F, 2F, ticks -> false));
        assertTrue(timing.shouldDefer(false, 1, 6F, false, 0, 1F, 12.5F, 2F, ticks -> false));
        assertFalse(timing.shouldDefer(false, 0, 99F, false, -.2, 1F, 12.5F, 2F, ticks -> false));
        assertTrue(timing.shouldDefer(true, 1, 8F, false, 0, 1F, 12.5F, 2F, ticks -> false));
    }

    @Test
    void damageMultiplierUsesStrictVelocityThreshold() {
        assertEquals(7F, CriticalsTiming.estimateDamage(7F, 1F, false, -.08));
        assertEquals(10.5F, CriticalsTiming.estimateDamage(7F, 1F, false, -.081));
        assertEquals(7F, CriticalsTiming.estimateDamage(7F, 1F, true, -.081));
        assertEquals(2.8F, CriticalsTiming.estimateDamage(7F, .5F, false, 0), .000001F);
    }

    @Test
    void stuckFallDistanceAndVelocityAreAlternativeConditions() {
        assertFalse(CriticalsTiming.skipStuckFall(false, 0, -.08));
        assertTrue(CriticalsTiming.skipStuckFall(false, 10, -.079));
        assertFalse(CriticalsTiming.skipStuckFall(true, .001, .2));
        assertTrue(CriticalsTiming.skipStuckFall(true, 0, -.2));
    }
}
