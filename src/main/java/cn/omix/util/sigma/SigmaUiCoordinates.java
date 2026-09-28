package cn.omix.util.sigma;

/** Per-axis conversions between GLFW logical pixels and Minecraft GUI coordinates. */
public final class SigmaUiCoordinates {
    private SigmaUiCoordinates() {}

    /** Whole raster-density buckets avoid upscaling glyphs, including at fractional display scales. */
    public static int rasterScale(int width, int height, int framebufferWidth, int framebufferHeight) {
        double densityX = (double) framebufferWidth / Math.max(1, width);
        double densityY = (double) framebufferHeight / Math.max(1, height);
        return Math.max(1, (int) Math.ceil(Math.max(densityX, densityY)));
    }

    public static float renderScale(int windowSize, int framebufferSize, int guiScale) {
        // GuiRenderer projects onto framebufferSize / guiScale WITHOUT rounding.
        return (float) Math.max(1, framebufferSize) / Math.max(1, guiScale) / Math.max(1, windowSize);
    }

    public static float fromMouse(double coordinate, int windowSize, int scaledSize) {
        // Mouse.scaleX/Y instead use Window's rounded-up scaled dimensions.
        return (float) (coordinate * Math.max(1, windowSize) / Math.max(1, scaledSize));
    }
}
