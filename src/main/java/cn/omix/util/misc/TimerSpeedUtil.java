package cn.omix.util.misc;

import lombok.experimental.UtilityClass;

@UtilityClass
public class TimerSpeedUtil {
    private float timerSpeed = 1.0F;

    public float getTimerSpeed() {
        return timerSpeed;
    }

    public void setTimerSpeed(float speed) {
        timerSpeed = Math.max(0.05F, speed);
    }

    public void reset() {
        timerSpeed = 1.0F;
    }
}
