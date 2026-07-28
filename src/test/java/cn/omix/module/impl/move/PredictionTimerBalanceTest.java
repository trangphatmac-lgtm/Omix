package cn.omix.module.impl.move;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PredictionTimerBalanceTest {
    private static final double TOLERANCE = 1.0E-6;

    @Test
    void firstAscentStaysAtNormalSpeedUntilBalanceHasBeenEarned() {
        PredictionTimerBalance timer = new PredictionTimerBalance();

        for (int i = 0; i < 5; i++) {
            assertEquals(1.0F, timer.boost(), TOLERANCE);
        }

        assertEquals(0.0, timer.getBalance(), TOLERANCE);
    }

    @Test
    void fallingBalancePaysForTheNextAscentWithoutOverdraft() {
        PredictionTimerBalance timer = new PredictionTimerBalance();

        for (int i = 0; i < 6; i++) {
            timer.boost();
        }
        for (int i = 0; i < 6; i++) {
            assertEquals(0.75F, timer.slow(0.75F, 6), TOLERANCE);
        }
        assertEquals(2.0, timer.getBalance(), TOLERANCE);

        timer.prepareNextJump();
        for (int i = 0; i < 6; i++) {
            float speed = timer.boost();
            assertEquals(1.0F + 2.0F / 6.0F, speed, TOLERANCE);
        }

        assertEquals(0.0, timer.getBalance(), TOLERANCE);
    }

    @Test
    void distributesTheRecordedBalanceAcrossObservedAscentTicks() {
        PredictionTimerBalance timer = new PredictionTimerBalance();
        timer.boost();
        timer.slow(0.75F, 6);
        timer.prepareNextJump();

        float boost = timer.boost();

        assertEquals(1.0F + 1.0F / 3.0F, boost, TOLERANCE);
        assertEquals(0.0, timer.getBalance(), TOLERANCE);
        assertEquals(1.0F, timer.boost(), TOLERANCE);
    }

    @Test
    void halfSpeedRunsForEveryConfiguredLowTimerTick() {
        PredictionTimerBalance timer = new PredictionTimerBalance();

        for (int i = 0; i < 5; i++) {
            timer.boost();
        }
        for (int i = 0; i < 6; i++) {
            assertEquals(0.5F, timer.slow(0.5F, 6), TOLERANCE);
        }

        assertEquals(6.0, timer.getBalance(), TOLERANCE);
        assertEquals(6, timer.getSlowTicks());

        timer.prepareNextJump();
        for (int i = 0; i < 5; i++) {
            assertEquals(2.2F, timer.boost(), TOLERANCE);
        }

        assertEquals(0.0, timer.getBalance(), TOLERANCE);
    }

    @Test
    void halfSpeedBalanceDoesNotGrowAcrossRepeatedJumps() {
        PredictionTimerBalance timer = new PredictionTimerBalance();

        for (int jump = 0; jump < 20; jump++) {
            for (int i = 0; i < 5; i++) {
                timer.boost();
            }
            assertEquals(0.0, timer.getBalance(), TOLERANCE);

            for (int i = 0; i < 6; i++) {
                assertEquals(0.5F, timer.slow(0.5F, 6), TOLERANCE);
            }
            assertEquals(6.0, timer.getBalance(), TOLERANCE);
            assertEquals(6, timer.getSlowTicks());

            timer.prepareNextJump();
        }
    }

    @Test
    void neverExceedsTheConfiguredLowTimerTickCountPerJump() {
        PredictionTimerBalance timer = new PredictionTimerBalance();

        for (int i = 0; i < 20; i++) {
            timer.boost();
        }
        for (int i = 0; i < 20; i++) {
            timer.slow(0.75F, 3);
        }

        assertEquals(3, timer.getSlowTicks());

        timer.prepareNextJump();
        assertEquals(0, timer.getSlowTicks());
    }
}
