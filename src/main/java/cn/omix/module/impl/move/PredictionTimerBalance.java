package cn.omix.module.impl.move;

/**
 * Banks the real-time saved by a fast timer and spends exactly that amount on
 * a slow timer later in the same jump.
 *
 * <p>For one game tick at timer speed {@code s}, the elapsed real time in
 * normal-tick units is {@code 1 / s}. The balance delta is consequently
 * {@code 1 - 1 / s}. A positive value was saved by accelerating; a negative
 * value was spent by slowing down.</p>
 */
final class PredictionTimerBalance {
    static final float BOOST_SPEED = 2.0F;
    private static final double EPSILON = 1.0E-7;

    private double balance;
    private int slowTicks;

    float boost(float lowSpeed, int maxSlowTicks) {
        double targetBalance = maxSlowTicks * debitFor(lowSpeed);
        double remaining = Math.max(0.0, targetBalance - balance);
        double credit = Math.min(creditFor(BOOST_SPEED), remaining);

        if (credit <= EPSILON) {
            return 1.0F;
        }

        balance += credit;
        return speedForCredit(credit);
    }

    float slow(float lowSpeed, int maxSlowTicks) {
        if (balance <= EPSILON || slowTicks >= maxSlowTicks) {
            balance = 0.0;
            return 1.0F;
        }

        double debit = Math.min(debitFor(lowSpeed), balance);
        if (debit <= EPSILON) {
            balance = 0.0;
            return 1.0F;
        }

        balance -= debit;
        slowTicks++;
        if (balance <= EPSILON) {
            balance = 0.0;
        }
        return speedForDebit(debit);
    }

    void reset() {
        balance = 0.0;
        slowTicks = 0;
    }

    double getBalance() {
        return balance;
    }

    int getSlowTicks() {
        return slowTicks;
    }

    static double balanceDelta(float speed) {
        return 1.0 - 1.0 / speed;
    }

    static boolean isNormalSpeed(float speed) {
        return Math.abs(speed - 1.0F) <= EPSILON;
    }

    private static double creditFor(float speed) {
        return Math.max(0.0, balanceDelta(speed));
    }

    private static double debitFor(float speed) {
        return Math.max(0.0, -balanceDelta(speed));
    }

    private static float speedForCredit(double credit) {
        return (float) (1.0 / (1.0 - credit));
    }

    private static float speedForDebit(double debit) {
        return (float) (1.0 / (1.0 + debit));
    }
}
