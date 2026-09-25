package cn.omix.util.sigma;

/** Reversible, time-based Jello animation. No allocation or dependence on frame rate. */
public final class SigmaAnimation {
    private float value;
    private long previous;

    public SigmaAnimation() {}
    public SigmaAnimation(float value) { this.value = Math.clamp(value, 0, 1); }

    public float update(boolean forward, long nowNanos, int durationMillis) {
        if (previous == 0) previous = nowNanos;
        float step = Math.max(0, nowNanos - previous) / (Math.max(1, durationMillis) * 1_000_000f);
        previous = nowNanos;
        value = Math.clamp(value + (forward ? step : -step), 0, 1);
        return value;
    }

    public float value() { return value; }
    public void reset() { value = 0; previous = 0; }
    public static float easeInOut(float t) { return t < .5f ? 2 * t * t : 1 - (float) Math.pow(-2 * t + 2, 2) / 2; }
    public static float easeOut(float t) { return 1 - (1 - t) * (1 - t); }

    /** Jello SmoothInterpolator's cubic Bezier curve, evaluated without per-frame sample allocations. */
    public static float bezier(float x, double x1, double y1, double x2, double y2) {
        if (x <= 0 || x >= 1) return Math.clamp(x, 0, 1);
        double low = 0, high = 1, t = x;
        for (int i = 0; i < 18; i++) {
            double u = 1 - t, px = 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t;
            if (px < x) low = t; else high = t;
            t = (low + high) / 2;
        }
        double u = 1 - t;
        return (float) (3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t);
    }
}
