package cn.omix.util.misc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimerSpeedUtilTest {
    @BeforeEach
    @AfterEach
    void resetTimer() {
        TimerSpeedUtil.clearTimerOverride();
        TimerSpeedUtil.reset();
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
