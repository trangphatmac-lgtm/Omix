package cn.omix.util.player.velocity;

/** Coordinates the receive thread and client ticks without depending on Minecraft classes. */
public final class GrimFullState {
    private static final int WAIT_TICKS = 180;
    private static final long REFRESH_NANOS = 60_000_000_000L;

    private Object player;
    private int waitStartedAtAge;
    private int lastAge;
    private long lastRefreshNanos;

    public synchronized void reset() {
        player = null;
        waitStartedAtAge = 0;
        lastAge = 0;
        lastRefreshNanos = 0;
    }

    /** Returns whether ping replies (and optionally velocity) should currently pass through. */
    public synchronized boolean update(Object currentPlayer, int age, long nowNanos, boolean setback) {
        if (currentPlayer == null) {
            reset();
            return true;
        }
        if (player != currentPlayer || age < lastAge) {
            player = currentPlayer;
            waitStartedAtAge = age;
            lastRefreshNanos = nowNanos;
        }
        lastAge = age;

        // Use wall time for the periodic refresh, even when game ticks are slowed down.
        if (nowNanos - lastRefreshNanos >= REFRESH_NANOS) {
            waitStartedAtAge = age;
            lastRefreshNanos = nowNanos;
        }
        // A setback restarts the full wait but does not postpone the periodic refresh.
        if (setback) {
            waitStartedAtAge = age;
        }
        return (long) age - waitStartedAtAge < WAIT_TICKS;
    }

    /** A source-matched velocity exemption never changes the ping or wait timers. */
    public synchronized Decision decide(Object currentPlayer, int age, long nowNanos, boolean setback,
                                        boolean exemptVelocity, boolean allowVelocityDuringWait) {
        boolean waiting = update(currentPlayer, age, nowNanos, setback);
        if (currentPlayer == null) return new Decision(false, false);
        return new Decision(!waiting, !exemptVelocity && (!waiting || !allowVelocityDuringWait));
    }

    public record Decision(boolean cancelPing, boolean cancelVelocity) {}
}
