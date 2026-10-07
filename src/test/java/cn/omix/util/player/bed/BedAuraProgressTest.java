package cn.omix.util.player.bed;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BedAuraProgressTest {
    @Test void keepsReferenceStopThresholdAndInterpolatesOnlyActualTicks() {
        var progress = new BedAuraProgress();
        var rate = BedAuraProgress.rate(.25f, 1, false, false, true);
        progress.advance(rate);
        assertEquals(0, progress.interpolated(0));
        assertEquals(.125f, progress.interpolated(.5f));
        assertEquals(.25f, progress.interpolated(1));
        for (int i = 0; i < 3; i++) progress.advance(rate);
        assertFalse(progress.readyToFinish(rate));
        progress.advance(rate);
        assertTrue(progress.readyToFinish(rate));
        assertEquals(1, progress.interpolated(100));
    }

    @Test void watchdogNormalizesAirborneProgressAgainstFiveAndCompensatesWater() {
        var rate = BedAuraProgress.rate(.02f, 2, true, true, false);
        assertEquals(1, rate.delta(), .00001);
        assertEquals(5, rate.goal());
        var progress = new BedAuraProgress();
        progress.advance(rate);
        assertEquals(.2f, progress.interpolated(1), .00001);
        assertFalse(progress.readyToFinish(rate));
        // .02f * 2 * 5 * 5 is just below one; retain the source's float threshold.
        for (int i = 0; i < 6; i++) progress.advance(rate);
        assertTrue(progress.readyToFinish(rate));
        assertEquals(.04f, BedAuraProgress.rate(.02f, 2, false, true, false).delta());
    }

    @Test void delaySkipsExactlyConfiguredTicksAndCleanupDoesNotEraseIt() {
        var progress = new BedAuraProgress();
        progress.advance(new BedAuraProgress.Rate(.5f, 1));
        progress.finish(2);
        assertEquals(0, progress.interpolated(1));
        assertTrue(progress.waiting());
        progress.resetProgress();
        assertTrue(progress.waiting());
        assertFalse(progress.waiting());
        progress.finish(4);
        progress.reset();
        assertFalse(progress.waiting());
    }

    @Test void resetCannotLeakOldDefenseProgressToNewBed() {
        var progress = new BedAuraProgress();
        progress.advance(new BedAuraProgress.Rate(4, 5));
        progress.resetProgress();
        assertEquals(0, progress.interpolated(.5f));
        progress.advance(new BedAuraProgress.Rate(.2f, 1));
        assertEquals(.1f, progress.interpolated(.5f));
    }

    @Test void invalidOrUnbreakableDeltasNeverFinish() {
        for (float delta : new float[]{-1, 0, Float.NaN, Float.POSITIVE_INFINITY}) {
            var rate = BedAuraProgress.rate(delta, 1, true, true, false);
            assertEquals(0, rate.delta());
            var progress = new BedAuraProgress();
            progress.advance(rate);
            assertFalse(progress.readyToFinish(rate));
            assertEquals(0, progress.interpolated(1));
        }
    }
}
