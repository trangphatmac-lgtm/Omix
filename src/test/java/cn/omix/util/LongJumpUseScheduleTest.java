package cn.omix.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LongJumpUseScheduleTest {
    private static final LongJumpAim AIM = new LongJumpAim(180, 80);

    @Test
    void useAnglesRemainBitExactThroughTheTickIncludingAnEarlyVelocityResponse() {
        var schedule = new LongJumpUseSchedule();
        var sent = new LongJumpAim(359.93872F, 79.953125F);
        assertTrue(schedule.beginUse(false, sent));
        schedule.requestNextUse();
        var smoothed = new LongJumpAim(Math.nextUp(sent.yaw()), Math.nextDown(sent.pitch()));
        assertFalse(schedule.beginUse(false, smoothed));
        assertEquals(Float.floatToIntBits(sent.yaw()), Float.floatToIntBits(schedule.getAim().yaw()));
        assertEquals(Float.floatToIntBits(sent.pitch()), Float.floatToIntBits(schedule.getAim().pitch()));
        schedule.beginTick();
        assertNull(schedule.getAim());
        assertTrue(schedule.beginUse(false, smoothed));
        assertEquals(smoothed, schedule.getAim());
    }

    @Test
    void cooldownDoesNotLockRotationAndClosingDoesNotChangeAnAlreadyUsedAim() {
        var schedule = new LongJumpUseSchedule();
        assertFalse(schedule.beginUse(true, AIM));
        assertNull(schedule.getAim());
        assertTrue(schedule.beginUse(false, AIM));
        schedule.close();
        assertEquals(AIM, schedule.getAim());
        assertFalse(schedule.beginUse(false, new LongJumpAim(0, 0)));
    }

    @Test
    void responseInTheUseTickCannotCauseAnotherUseUntilNextTick() {
        var schedule = new LongJumpUseSchedule();
        schedule.beginTick();
        assertTrue(schedule.beginUse(false, AIM));
        schedule.requestNextUse();
        assertTrue(schedule.isPending());
        assertFalse(schedule.beginUse(false, AIM));
        assertTrue(schedule.isUsedThisTick(), "Scaffold must still defer after the response");
        schedule.beginTick();
        assertFalse(schedule.isUsedThisTick());
        assertTrue(schedule.beginUse(false, AIM));
    }

    @Test
    void cooldownDefersUseWithoutConsumingTheRequestOrBlockingScaffold() {
        var schedule = new LongJumpUseSchedule();
        for (int tick = 0; tick < 10; tick++) {
            schedule.beginTick();
            assertFalse(schedule.beginUse(true, AIM));
            assertTrue(schedule.isPending());
            assertFalse(schedule.isUsedThisTick());
        }
        schedule.beginTick();
        assertTrue(schedule.beginUse(false, AIM));
        assertFalse(schedule.isPending());
    }

    @Test
    void missingVelocityDoesNotResendEveryTick() {
        var schedule = new LongJumpUseSchedule();
        assertTrue(schedule.beginUse(false, AIM));
        for (int tick = 0; tick < 5; tick++) {
            schedule.beginTick();
            assertFalse(schedule.beginUse(false, AIM));
            assertFalse(schedule.isPending());
        }
        schedule.requestNextUse();
        assertTrue(schedule.beginUse(false, AIM));
    }

    @Test
    void closingDiscardsPendingUseAndLateResponsesCannotRestartIt() {
        var schedule = new LongJumpUseSchedule();
        schedule.close();
        schedule.requestNextUse();
        schedule.beginTick();
        assertFalse(schedule.isPending());
        assertFalse(schedule.beginUse(false, AIM));
    }

    @Test
    void collectingMotionsAndSchedulingUsesDoNotAdvanceEachOtherPrematurely() {
        var schedule = new LongJumpUseSchedule();
        var motions = new LongJumpMotionQueue<String>(2);
        assertTrue(schedule.beginUse(false, AIM));
        assertTrue(motions.awaitMotion());
        assertTrue(motions.capture("first"));
        schedule.requestNextUse();
        assertFalse(schedule.beginUse(false, AIM));
        assertFalse(motions.capture("unsolicited duplicate"));
        assertFalse(motions.isComplete());
        schedule.beginTick();
        assertTrue(schedule.beginUse(false, AIM));
        assertTrue(motions.awaitMotion());
        assertTrue(motions.capture("second"));
        assertEquals("first", motions.startFlight(1));
        assertEquals("second", motions.releaseAtApex(false, 0.01, 2));
    }
}
