package cn.omix.util.skeet;

/** Gamesense's fixed logical canvas, fitted to window pixels independently of GUI Scale. */
public final class SkeetLayout {
    public static final float WIDTH = 390, HEIGHT = 350, SIDEBAR = 48;
    public static final float GROUP_WIDTH = 149.5f, GAP = 12, ROW_GAP = 4;

    private SkeetLayout() {}

    public record Rect(float x, float y, float width, float height) {
        public boolean contains(double mx, double my) {
            return mx >= x && mx < x + width && my >= y && my < y + height;
        }
        public boolean intersects(Rect other) {
            return x < other.x + other.width && x + width > other.x
                    && y < other.y + other.height && y + height > other.y;
        }
        public Rect inset(float amount) {
            return new Rect(x + amount, y + amount, Math.max(0, width - amount * 2), Math.max(0, height - amount * 2));
        }
    }

    public record Viewport(float width, float height, float scaleX, float scaleY,
                           float mouseScaleX, float mouseScaleY, int fontDensity) {
        public float mouseX(double x) { return (float) x * mouseScaleX; }
        public float mouseY(double y) { return (float) y * mouseScaleY; }
    }

    public static Viewport viewport(int windowWidth, int windowHeight, int framebufferWidth,
                                    int framebufferHeight, int scaledWidth, int scaledHeight, float guiScale) {
        float ww = Math.max(1, windowWidth), wh = Math.max(1, windowHeight);
        float size = Math.max(.001f, Math.min(2, Math.min(ww / (WIDTH + 16), wh / (HEIGHT + 16))));
        float width = ww / size, height = wh / size;
        float sx = Math.max(1, framebufferWidth) / width / Math.max(1, guiScale);
        float sy = Math.max(1, framebufferHeight) / height / Math.max(1, guiScale);
        int density = Math.max(1, (int) Math.ceil(Math.max(sx, sy) * Math.max(1, guiScale)));
        return new Viewport(width, height, sx, sy, width / Math.max(1, scaledWidth),
                height / Math.max(1, scaledHeight), density);
    }

    public static float scroll(float requested, float content, float visible) {
        return Math.clamp(requested, 0, Math.max(0, content - visible));
    }

    /** Opens upward near the bottom and caps long lists so every option remains reachable. */
    public static Rect popup(Rect anchor, float width, float height, Rect viewport) {
        float w = Math.min(width, viewport.width), h = Math.min(height, viewport.height);
        float x = Math.clamp(anchor.x, viewport.x, viewport.x + viewport.width - w);
        float below = anchor.y + anchor.height;
        float y = below + h <= viewport.y + viewport.height ? below : anchor.y - h;
        return new Rect(x, Math.clamp(y, viewport.y, viewport.y + viewport.height - h), w, h);
    }

    /** Snap from the minimum, retaining both endpoints even when the step does not divide the range. */
    public static float sliderValue(float fraction, float min, float max, float step) {
        if (max <= min) return min;
        float t = Math.clamp(fraction, 0, 1);
        if (t == 0) return min;
        if (t == 1) return max;
        double raw = min + (double) t * (max - min);
        // Value bounds are floats: tolerate their representation error at an exact half-step.
        return (float) Math.clamp(step > 0 ? min + Math.floor((raw - min) / step + .500001d) * step : raw, min, max);
    }

    public static String format(float value) {
        return java.math.BigDecimal.valueOf(Math.round(value * 100000d) / 100000d).stripTrailingZeros().toPlainString();
    }
}
