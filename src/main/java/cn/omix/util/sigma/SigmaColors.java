package cn.omix.util.sigma;

/** Jello's original ARGB palette and alpha semantics. */
public final class SigmaColors {
    public static final int WHITE = -65794;
    public static final int BLACK = -16711423;
    public static final int GREY = -6710887;
    public static final int UNSPAWN = -7864320;

    private SigmaColors() {}

    public static int alpha(int color, float opacity) {
        return color & 0x00ffffff | (int) ((color >>> 24) * Math.clamp(opacity, 0, 1)) << 24;
    }

    public static int mix(int from, int to, float amount) {
        amount = Math.clamp(amount, 0, 1);
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            result |= (int) (((from >>> shift) & 255) * (1 - amount) + ((to >>> shift) & 255) * amount) << shift;
        }
        return result;
    }

    /** Original RenderUtil.method17690, including its intentionally unbounded first-color weight. */
    public static int sourceBlend(int first, int second, float firstWeight) {
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int component = (int) (((first >>> shift) & 255) * firstWeight + ((second >>> shift) & 255) * (1 - firstWeight));
            result |= (component & 255) << shift;
        }
        return result;
    }
}
