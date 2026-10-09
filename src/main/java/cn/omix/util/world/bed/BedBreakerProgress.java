package cn.omix.util.world.bed;

/** Amunix's damage threshold and four-tick block delay, independent of rendering. */
public final class BedBreakerProgress {
    public enum State { NONE, PREPARE, BREAKING, FINISHING }

    private float damage;
    private float previousDamage;
    private int delay;
    private State state = State.NONE;

    public static float threshold(double speedPercent) {
        return 1F - .3F * (float) (Math.max(0, Math.min(100, speedPercent)) / 100);
    }

    public void prepare() { state = State.PREPARE; }
    public void start() { state = State.BREAKING; }
    public State state() { return state; }

    public void advance(float delta, double speedPercent) {
        previousDamage = damage;
        if (delta > 0 && Float.isFinite(delta)) damage += delta;
        state = ready(speedPercent) ? State.FINISHING : State.BREAKING;
    }

    public boolean ready(double speedPercent) { return damage >= threshold(speedPercent); }
    public boolean willFinish(float delta, double speedPercent) { return damage + Math.max(0, delta) >= threshold(speedPercent); }

    public float progress(double speedPercent, float tickDelta) {
        float interpolated = previousDamage + (damage - previousDamage) * Math.max(0, Math.min(1, tickDelta));
        return Math.max(0, Math.min(1, interpolated / threshold(speedPercent)));
    }

    public boolean waiting(boolean ignoreDelay) {
        if (ignoreDelay) delay = 0;
        if (delay <= 0) return false;
        delay--;
        return true;
    }

    public void finish() { resetDamage(); delay = 4; }
    public void resetDamage() { damage = previousDamage = 0; state = State.NONE; }
    public void reset() { resetDamage(); delay = 0; }
}
