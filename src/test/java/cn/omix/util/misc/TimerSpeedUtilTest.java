package cn.omix.util.misc;

import cn.omix.util.LongJumpAim;
import cn.omix.util.LongJumpUseSchedule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimerSpeedUtilTest {
    private final Object longJump = new Object();
    private final Object other = new Object();

    @BeforeEach
    @AfterEach
    void resetTimer() {
        TimerSpeedUtil.clearTemporaryOverride(longJump);
        TimerSpeedUtil.clearTemporaryOverride(other);
        TimerSpeedUtil.clearTimerOverride();
        TimerSpeedUtil.reset();
    }

    @Test
    void longJumpTemporarilyOverridesTimerWithoutLosingLiveChanges() {
        float[] speed = {2.0F};
        TimerSpeedUtil.setTimerOverride(() -> speed[0]);
        TimerSpeedUtil.setTemporaryOverride(longJump, 0.02F);
        TimerSpeedUtil.reset();
        assertEquals(0.02F, TimerSpeedUtil.getTimerSpeed());
        speed[0] = 3.0F;
        assertEquals(0.02F, TimerSpeedUtil.getTimerSpeed());
        TimerSpeedUtil.clearTemporaryOverride(longJump);
        assertEquals(3.0F, TimerSpeedUtil.getTimerSpeed());
    }

    @Test
    void multiContinuationNeverChangesTheSimulationTimer() {
        var schedule = new LongJumpUseSchedule();
        var aim = new LongJumpAim(180, 80);
        TimerSpeedUtil.setTimerOverride(() -> 0.01F);
        schedule.beginUse(false, aim);
        TimerSpeedUtil.setTemporaryOverride(longJump, 0.02F);
        schedule.endTick();
        for (int shot = 0; shot < 3; shot++) {
            schedule.requestNextUse();
            assertEquals(0.02F, TimerSpeedUtil.getTimerSpeed());
            schedule.setCooldown(0, 10);
            assertEquals(false, schedule.beginContinuation(499_999_999L));
            assertEquals(0.02F, TimerSpeedUtil.getTimerSpeed());
            assertEquals(true, schedule.beginContinuation(500_000_000L));
            assertEquals(0.02F, TimerSpeedUtil.getTimerSpeed());
        }
        TimerSpeedUtil.clearTemporaryOverride(longJump);
        assertEquals(0.01F, TimerSpeedUtil.getTimerSpeed());
    }

    @Test
    void removingAnOldOwnerCannotCancelAnotherOverride() {
        TimerSpeedUtil.setTemporaryOverride(longJump, 0.02F);
        TimerSpeedUtil.setTemporaryOverride(other, 0.5F);
        TimerSpeedUtil.clearTemporaryOverride(longJump);
        assertEquals(0.5F, TimerSpeedUtil.getTimerSpeed());
        TimerSpeedUtil.clearTemporaryOverride(other);
        assertEquals(1.0F, TimerSpeedUtil.getTimerSpeed());
    }

    @Test
    void togglingTimerWhileLongJumpWaitsDoesNotCancelTheSlowdown() {
        TimerSpeedUtil.setTemporaryOverride(longJump, 0.02F);
        TimerSpeedUtil.setTimerOverride(() -> 5.0F);
        TimerSpeedUtil.clearTimerOverride();
        TimerSpeedUtil.setTimerSpeed(1.5F);
        assertEquals(0.02F, TimerSpeedUtil.getTimerSpeed());
        TimerSpeedUtil.clearTemporaryOverride(longJump);
        assertEquals(1.5F, TimerSpeedUtil.getTimerSpeed());
    }

    @Test
    void supportsMinimumAndMaximumRequestedSpeeds() {
        TimerSpeedUtil.setTimerSpeed(0.01F);
        assertEquals(0.01F, TimerSpeedUtil.getTimerSpeed());
        TimerSpeedUtil.setTimerSpeed(5.0F);
        assertEquals(5.0F, TimerSpeedUtil.getTimerSpeed());
        TimerSpeedUtil.setTimerSpeed(0.0F);
        assertEquals(0.01F, TimerSpeedUtil.getTimerSpeed());
    }

    @Test
    void overrideReadsChangesWithoutWaitingForAGameTick() {
        float[] speed = {0.01F};
        TimerSpeedUtil.setTimerOverride(() -> speed[0]);
        assertEquals(0.01F, TimerSpeedUtil.getTimerSpeed());
        speed[0] = 5.0F;
        assertEquals(5.0F, TimerSpeedUtil.getTimerSpeed());
    }

    @Test
    void otherModulesCannotCancelOverrideAndRegainControlWhenCleared() {
        TimerSpeedUtil.setTimerOverride(() -> 0.01F);
        TimerSpeedUtil.setTimerSpeed(2.0F);
        assertEquals(0.01F, TimerSpeedUtil.getTimerSpeed());
        TimerSpeedUtil.clearTimerOverride();
        assertEquals(2.0F, TimerSpeedUtil.getTimerSpeed());

        TimerSpeedUtil.setTimerOverride(() -> 5.0F);
        TimerSpeedUtil.reset();
        assertEquals(5.0F, TimerSpeedUtil.getTimerSpeed());
        TimerSpeedUtil.clearTimerOverride();
        assertEquals(1.0F, TimerSpeedUtil.getTimerSpeed());
    }
}
