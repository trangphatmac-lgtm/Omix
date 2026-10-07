package cn.omix.util.player.bed;

/** Samsara's mining threshold and Watchdog arithmetic, independent of rendering. */
public final class BedAuraProgress {
    public record Rate(float delta, float goal) {}

    private float previous;
    private float current;
    private float goal = 1;
    private int delay;

    public static Rate rate(float vanillaDelta, float speed, boolean watchdog, boolean submerged, boolean onGround) {
        float delta = vanillaDelta * speed;
        if (watchdog && submerged) delta *= 5;
        if (watchdog && !onGround) delta *= 5;
        return new Rate(Float.isFinite(delta) ? Math.max(0, delta) : 0, watchdog && !onGround ? 5 : 1);
    }

    public boolean waiting() {
        if (delay <= 0) return false;
        delay--;
        return true;
    }

    public boolean readyToFinish(Rate rate) {
        return rate.delta() > 0 && current >= rate.goal() + rate.delta();
    }

    public void advance(Rate rate) {
        previous = current;
        current += rate.delta();
        goal = rate.goal();
    }

    public float interpolated(float tickDelta) {
        return Math.clamp((previous + (current - previous) * Math.clamp(tickDelta, 0, 1)) / goal, 0, 1);
    }

    public void finish(int delayTicks) {
        resetProgress();
        delay = Math.max(0, delayTicks);
    }

    public void resetProgress() {
        previous = current = 0;
        goal = 1;
    }

    public void reset() {
        resetProgress();
        delay = 0;
    }
}
