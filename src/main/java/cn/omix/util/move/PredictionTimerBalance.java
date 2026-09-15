package cn.omix.util.move;

/**
 * Records all TB earned during the configured falling phase, then distributes
 * that balance evenly over the observed number of ascent ticks on the next
 * jump.
 */
public final class PredictionTimerBalance {
    private static final double EPSILON = 1.0E-7;

    private double balance;
    private int ascentTicks;
    private int expectedAscentTicks;
    private int slowTicks;
    private boolean falling;
    private boolean ascentPlanPrepared;
    private double spendPerAscentTick;

    public float boost() {
        if (falling) {
            prepareNextJump();
        }

        if (!ascentPlanPrepared) {
            spendPerAscentTick = expectedAscentTicks > 0
                    ? balance / expectedAscentTicks
                    : 0.0;
            ascentPlanPrepared = true;
        }
        ascentTicks++;

        double spend = Math.min(spendPerAscentTick, balance);
        if (spend <= EPSILON) {
            balance = 0.0;
            return 1.0F;
        }

        balance -= spend;
        if (balance <= EPSILON) {
            balance = 0.0;
        }
        return (float) (1.0 + 1.0);
    }

    public float slow(float lowSpeed, int maxSlowTicks) {
        if (!falling) {
            if (ascentTicks > 0) {
                expectedAscentTicks = ascentTicks;
            }
            falling = true;
        }

        if (slowTicks >= maxSlowTicks) {
            return 1.0F;
        }

        double earned = debitFor(lowSpeed);
        if (earned <= EPSILON) {
            return 1.0F;
        }

        balance += earned;
        slowTicks++;
        return lowSpeed;
    }

    public void prepareNextJump() {
        ascentTicks = 0;
        slowTicks = 0;
        falling = false;
        ascentPlanPrepared = false;
        spendPerAscentTick = 0.0;
    }

    public void reset() {
        balance = 0.0;
        expectedAscentTicks = 0;
        prepareNextJump();
    }

    double getBalance() {
        return balance;
    }

    int getSlowTicks() {
        return slowTicks;
    }

    public static boolean isNormalSpeed(float speed) {
        return Math.abs(speed - 1.0F) <= EPSILON;
    }

    private static double debitFor(float speed) {
        return Math.max(0.0, 1.0 / speed - 1.0);
    }
}
