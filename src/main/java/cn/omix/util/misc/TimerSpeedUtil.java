package cn.omix.util.misc;

import lombok.experimental.UtilityClass;

import java.util.function.Supplier;

@UtilityClass
public class TimerSpeedUtil {
    private float timerSpeed = 1.0F;
    private Supplier<Float> timerOverride;

    public float getTimerSpeed() {
        return timerOverride == null ? timerSpeed : Math.max(0.01F, timerOverride.get());
    }

    public void setTimerSpeed(float speed) {
        timerSpeed = Math.max(0.01F, speed);
    }

    // Read on every render tick so even 0.01x does not delay setting changes.
    public void setTimerOverride(Supplier<Float> speed) {
        timerOverride = speed;
    }

    public void clearTimerOverride() {
        timerOverride = null;
    }

    public void reset() {
        // Other modules reset their shared speed without cancelling Timer's override.
        timerSpeed = 1.0F;
    }
}
