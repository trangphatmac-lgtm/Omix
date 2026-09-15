package cn.omix.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LongJumpMotionQueueTest {
    @Test
    void multiCollectsExactlyOneResponsePerUseThenReleasesInArrivalOrder() {
        var queue = new LongJumpMotionQueue<String>(3);
        assertFalse(queue.capture("before use"));
        for (int i = 1; i <= 3; i++) {
            assertTrue(queue.awaitMotion());
            assertFalse(queue.awaitMotion());
            assertTrue(queue.capture("motion " + i));
            assertFalse(queue.capture("duplicate response"));
            if (i < 3) {
                assertFalse(queue.isComplete());
                assertNull(queue.startFlight(10));
                assertNull(queue.releaseAtApex(false, 0.01, 10));
            }
        }
        assertTrue(queue.isComplete());
        assertFalse(queue.awaitMotion());
        assertEquals("motion 1", queue.startFlight(10));
        assertNull(queue.startFlight(10));
        assertNull(queue.releaseAtApex(false, 0.01, 10), "Do not consume two motions in the first tick");
        assertNull(queue.releaseAtApex(false, 0.3, 11));
        assertNull(queue.releaseAtApex(false, -0.01, 12));
        assertEquals("motion 2", queue.releaseAtApex(false, 0.075, 13));
        assertNull(queue.releaseAtApex(false, 0.075, 13));
        assertEquals("motion 3", queue.releaseAtApex(false, 0.005, 25));
        assertNull(queue.releaseAtApex(false, 0.005, 26));
    }

    @Test
    void singleAndMultiTimesOneFinishOnTheFirstResponse() {
        var queue = new LongJumpMotionQueue<String>(1);
        assertTrue(queue.awaitMotion());
        assertTrue(queue.capture("only motion"));
        assertTrue(queue.isComplete());
        assertEquals("only motion", queue.startFlight(5));
        assertFalse(queue.awaitMotion());
        assertNull(queue.releaseAtApex(false, 0.01, 8));
    }

    @Test
    void manualDisableOrWorldChangeInvalidatesPendingCallbacksAndDropsTheQueue() {
        var queue = new LongJumpMotionQueue<String>(2);
        queue.awaitMotion();
        queue.capture("old world");
        queue.awaitMotion();
        queue.close();
        assertFalse(queue.capture("late response"));
        assertFalse(queue.awaitMotion());
        assertNull(queue.startFlight(1));
        assertNull(queue.releaseAtApex(false, 0.01, 2));
    }

    @Test
    void closingDuringFlightDropsRemainingMotions() {
        var queue = new LongJumpMotionQueue<String>(2);
        queue.awaitMotion();
        queue.capture("first");
        queue.awaitMotion();
        queue.capture("second");
        assertEquals("first", queue.startFlight(1));
        queue.close();
        assertNull(queue.releaseAtApex(false, 0.01, 2));
    }

    @Test
    void apexRequiresPositiveVelocityAndGroundNeverReleasesAMotion() {
        assertTrue(LongJumpMotionQueue.isAscendingApex(0.08));
        assertTrue(LongJumpMotionQueue.isAscendingApex(0.00001));
        for (double vy : new double[]{0, -0.00001, 0.08001, Double.NaN}) {
            assertFalse(LongJumpMotionQueue.isAscendingApex(vy));
        }
        var queue = new LongJumpMotionQueue<String>(2);
        queue.awaitMotion();
        queue.capture("first");
        queue.awaitMotion();
        queue.capture("second");
        queue.startFlight(1);
        assertNull(queue.releaseAtApex(true, 0.01, 2));
        assertEquals("second", queue.releaseAtApex(false, 0.01, 2));
    }

    @Test
    void landingAcceptsVanillaGroundGravityButNotAnActiveJump() {
        assertTrue(LongJumpMotionQueue.hasLanded(true, 0));
        assertTrue(LongJumpMotionQueue.hasLanded(true, -0.0784));
        assertFalse(LongJumpMotionQueue.hasLanded(false, 0));
        assertFalse(LongJumpMotionQueue.hasLanded(true, 0.42));
        assertFalse(LongJumpMotionQueue.hasLanded(true, -0.4));
    }
}
