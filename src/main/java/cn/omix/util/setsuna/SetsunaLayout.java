package cn.omix.util.setsuna;

/** Pop geometry adapted from SetsunaClient's PopClickGuiScreen (see assets/omix/setsuna). */
public final class SetsunaLayout {
    public static final float BUBBLE_RADIUS = 27, HEADER = 44, MODULE_ROW = 25, GAP = 10;
    private SetsunaLayout() {}

    public record Rect(float x, float y, float width, float height) {
        public float right() { return x + width; }
        public float bottom() { return y + height; }
        public boolean contains(float mx, float my) { return mx >= x && mx < right() && my >= y && my < bottom(); }
        public boolean intersects(Rect other) { return right() > other.x && x < other.right() && bottom() > other.y && y < other.bottom(); }
        public Rect body() { return new Rect(x, y + HEADER, width, Math.max(0, height - HEADER)); }
    }
    public record Point(float x, float y) {}
    public record Panels(Rect modules, Rect settings) {}
    public record Viewport(float width, float height, float inputScale, float renderX, float renderY, int density) {
        public float mouse(double coordinate) { return (float) coordinate / inputScale; }
    }

    public static Viewport viewport(int width, int height, int framebufferWidth, int framebufferHeight,
                                    float guiScale, float configured) {
        width = Math.max(1, width); height = Math.max(1, height);
        framebufferWidth = Math.max(1, framebufferWidth); framebufferHeight = Math.max(1, framebufferHeight);
        guiScale = Math.max(1, guiScale);
        // Fit the two panels even when Minecraft's GUI scale leaves a very small screen.
        float scale = Math.max(.01f, Math.min(Math.clamp(configured, .65f, 1.25f), Math.min(width / 320f, height / 240f)));
        float pixelX = framebufferWidth / (float) Math.max(1, width);
        float pixelY = framebufferHeight / (float) Math.max(1, height);
        return new Viewport(width / scale, height / scale, scale,
                pixelX / guiScale * scale, pixelY / guiScale * scale,
                Math.clamp((int) Math.ceil(Math.max(pixelX, pixelY) * scale), 1, 8));
    }

    public static float ringRadius(float width, float height) { return Math.clamp(Math.min(width, height) * .27f, 78, 135); }
    public static Point bubble(int index, int count, float width, float height) {
        double angle = Math.toRadians(-90 + index * (360f / count));
        float radius = ringRadius(width, height);
        return new Point(width / 2 + (float) Math.cos(angle) * radius, height / 2 + (float) Math.sin(angle) * radius);
    }
    public static Panels panels(float width, float height, float settingsProgress) {
        float panelWidth = Math.min(Math.clamp(width * .23f, 142, 196), (width - 28 - GAP) / 2);
        float listHeight = Math.min(Math.clamp(height * .61f, 218, 322), height - 24);
        float settingsHeight = Math.min(Math.clamp(height * .75f, 260, 405), height - 16);
        float closedX = (width - panelWidth) / 2, pairX = (width - panelWidth * 2 - GAP) / 2;
        float progress = smooth(settingsProgress), listY = (height - listHeight) / 2;
        return new Panels(new Rect(lerp(closedX, pairX, progress), listY, panelWidth, listHeight),
                new Rect(lerp(closedX, pairX + panelWidth + GAP, progress),
                        lerp(listY, (height - settingsHeight) / 2, progress), panelWidth,
                        lerp(listHeight, settingsHeight, progress)));
    }
    public static Rect expand(Point origin, Rect target, float progress) {
        float t = smooth(progress);
        return new Rect(lerp(origin.x - BUBBLE_RADIUS, target.x, t), lerp(origin.y - BUBBLE_RADIUS, target.y, t),
                lerp(BUBBLE_RADIUS * 2, target.width, t), lerp(BUBBLE_RADIUS * 2, target.height, t));
    }
    public static float scroll(float value, float content, float visible) { return Math.clamp(value, 0, Math.max(0, content - visible)); }
    public static float smooth(float value) { float t = Math.clamp(value, 0, 1); return t * t * (3 - 2 * t); }
    public static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    public static float animate(float value, float target, float speed, float delta) {
        return lerp(value, target, 1 - (float) Math.exp(-speed * delta));
    }
}
