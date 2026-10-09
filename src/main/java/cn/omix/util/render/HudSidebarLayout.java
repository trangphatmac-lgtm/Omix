package cn.omix.util.render;

/** Sidebar placement in scaled GUI coordinates, including translated text widths. */
public final class HudSidebarLayout {
    private static Bounds classicModules;
    private HudSidebarLayout() {}

    public record Bounds(float left, float top, float right, float bottom) {
        public Bounds union(Bounds other) {
            return new Bounds(Math.min(left, other.left), Math.min(top, other.top),
                    Math.max(right, other.right), Math.max(bottom, other.bottom));
        }
    }
    public record Offset(float x, float y) {
        public static final Offset NONE = new Offset(0, 0);
    }

    public static void clearClassicModules() { classicModules = null; }
    public static void addClassicModule(Bounds row) {
        classicModules = classicModules == null ? row : classicModules.union(row);
    }
    public static Offset avoidClassicModules(Bounds sidebar, int width, int height) {
        return avoid(sidebar, classicModules, width, height);
    }

    static Offset avoid(Bounds sidebar, Bounds occupied, int width, int height) {
        if (occupied == null) return Offset.NONE;
        float gap = 3;
        if (sidebar.right + gap <= occupied.left || sidebar.left >= occupied.right + gap
                || sidebar.bottom + gap <= occupied.top || sidebar.top >= occupied.bottom + gap) return Offset.NONE;
        Offset[] candidates = {
                new Offset(occupied.left - gap - sidebar.right, 0),
                new Offset(occupied.right + gap - sidebar.left, 0),
                new Offset(0, occupied.bottom + gap - sidebar.top),
                new Offset(0, occupied.top - gap - sidebar.bottom)
        };
        for (Offset offset : candidates) {
            if (sidebar.left + offset.x >= 1 && sidebar.right + offset.x <= width - 1
                    && sidebar.top + offset.y >= 1 && sidebar.bottom + offset.y <= height - 1) return offset;
        }
        // There is no room to separate two oversized panels without clipping one.
        return Offset.NONE;
    }
}
