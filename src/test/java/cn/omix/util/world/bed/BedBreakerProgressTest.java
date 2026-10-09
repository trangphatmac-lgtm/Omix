package cn.omix.util.world.bed;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BedBreakerProgressTest {
    @Test void speedReducesThresholdInsteadOfMultiplyingDamage() {
        var progress = new BedBreakerProgress();
        progress.start();
        progress.advance(.7F, 0);
        assertFalse(progress.ready(0));
        assertTrue(progress.ready(100));
        assertEquals(.85F, BedBreakerProgress.threshold(50), .00001F);
        assertEquals(1, BedBreakerProgress.threshold(-30));
        assertEquals(.7F, BedBreakerProgress.threshold(200), .00001F);
    }

    @Test void snapPlansFinalRotationBeforeCrossingThreshold() {
        var progress = new BedBreakerProgress();
        progress.advance(.6F, 100);
        assertFalse(progress.ready(100));
        assertTrue(progress.willFinish(.11F, 100));
        assertFalse(progress.willFinish(.05F, 100));
        progress.advance(.11F, 100);
        assertEquals(BedBreakerProgress.State.FINISHING, progress.state());
    }

    @Test void finishedBlockWaitsFourFullTicksUnlessDelayIsIgnored() {
        var progress = new BedBreakerProgress();
        progress.finish();
        for (int tick = 0; tick < 4; tick++) assertTrue(progress.waiting(false));
        assertFalse(progress.waiting(false));
        progress.finish();
        assertFalse(progress.waiting(true));
        assertFalse(progress.waiting(false));
    }

    @Test void hudInterpolatesNormalizedDamageAndResetClearsIt() {
        var progress = new BedBreakerProgress();
        progress.prepare();
        assertEquals(BedBreakerProgress.State.PREPARE, progress.state());
        progress.advance(.35F, 100);
        assertEquals(.25F, progress.progress(100, .5F), .00001F);
        assertEquals(.5F, progress.progress(100, 1), .00001F);
        progress.advance(2, 100);
        assertEquals(1, progress.progress(100, 1));
        progress.reset();
        assertEquals(0, progress.progress(100, 1));
        assertEquals(BedBreakerProgress.State.NONE, progress.state());
        assertFalse(progress.waiting(false));
    }

    @Test void invalidDeltasDoNotPoisonProgress() {
        var progress = new BedBreakerProgress();
        progress.advance(Float.NaN, 0);
        progress.advance(Float.POSITIVE_INFINITY, 0);
        progress.advance(-1, 0);
        assertEquals(0, progress.progress(0, 1));
    }
}
