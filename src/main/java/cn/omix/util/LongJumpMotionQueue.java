package cn.omix.util;

import java.util.ArrayDeque;
import java.util.Deque;

/** One response per use; network-thread capture and client-thread release share this lock. */
public final class LongJumpMotionQueue<T> {
    static final double VERTICAL_EPSILON = 0.08;
    private final int total;
    private final Deque<T> motions = new ArrayDeque<>();
    private int received;
    private boolean awaiting;
    private boolean flying;
    private boolean closed;
    private int lastReleaseTick = Integer.MIN_VALUE;

    public LongJumpMotionQueue(int total) {
        if (total < 1) throw new IllegalArgumentException("Expected at least one motion");
        this.total = total;
    }

    public synchronized boolean awaitMotion() {
        if (closed || flying || awaiting || received >= total) return false;
        awaiting = true;
        return true;
    }

    public synchronized boolean capture(T motion) {
        if (closed || !awaiting) return false;
        awaiting = false;
        motions.addLast(motion);
        received++;
        return true;
    }

    public synchronized boolean isComplete() {
        return received == total;
    }

    public synchronized T startFlight(int tick) {
        if (closed || flying || !isComplete()) return null;
        flying = true;
        lastReleaseTick = tick;
        return motions.pollFirst();
    }

    public synchronized T releaseAtApex(boolean onGround, double vy, int tick) {
        if (closed || !flying || onGround || !isAscendingApex(vy) || lastReleaseTick == tick) return null;
        lastReleaseTick = tick;
        return motions.pollFirst();
    }

    public synchronized void close() {
        closed = true;
        awaiting = false;
        motions.clear();
    }

    static boolean isAscendingApex(double vy) {
        return vy > 0.0 && vy <= VERTICAL_EPSILON;
    }

    public static boolean hasLanded(boolean onGround, double vy) {
        // Vanilla gravity can leave -0.0784 even after a ground collision.
        return onGround && Math.abs(vy) <= VERTICAL_EPSILON;
    }
}
