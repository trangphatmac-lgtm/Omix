package im.webui.backend;

import net.minecraft.client.MinecraftClient;

public record BrowserViewport(
        int x,
        int y,
        int width,
        int height,
        boolean fullscreen,
        int cornerRadius
) {
    public BrowserViewport(int x, int y, int width, int height, boolean fullscreen) {
        this(x, y, width, height, fullscreen, 0);
    }

    public static BrowserViewport fullFrame() {
        var window = MinecraftClient.getInstance().getWindow();
        return new BrowserViewport(
                0,
                0,
                window.getFramebufferWidth(),
                window.getFramebufferHeight(),
                true,
                0
        );
    }

    public int relativeX(double globalX) {
        return (int) Math.round(globalX - x);
    }

    public int relativeY(double globalY) {
        return (int) Math.round(globalY - y);
    }

    public boolean contains(double globalX, double globalY) {
        if (globalX < x
                || globalY < y
                || globalX >= (double) x + width
                || globalY >= (double) y + height) {
            return false;
        }

        int radius = Math.max(0, Math.min(cornerRadius, Math.min(width, height) / 2));
        if (radius == 0) {
            return true;
        }

        double localX = globalX - x;
        double localY = globalY - y;
        if (localX >= radius
                && localX < width - radius
                || localY >= radius
                && localY < height - radius) {
            return true;
        }

        double centerX = localX < radius ? radius : width - radius;
        double centerY = localY < radius ? radius : height - radius;
        double deltaX = localX - centerX;
        double deltaY = localY - centerY;
        return deltaX * deltaX + deltaY * deltaY <= radius * (double) radius;
    }

    public BrowserViewport resized(int width, int height) {
        return fullscreen
                ? new BrowserViewport(x, y, width, height, true, cornerRadius)
                : this;
    }

    public int scaledWidth(float quality) {
        return Math.max(1, (int) (width * quality));
    }

    public int scaledHeight(float quality) {
        return Math.max(1, (int) (height * quality));
    }

    public double zoomLevel(float quality) {
        return Math.log(quality * devicePixelRatio()) / Math.log(1.2D);
    }

    public int transformMouseX(double globalX, float quality) {
        return (int) ((globalX - x) * quality);
    }

    public int transformMouseY(double globalY, float quality) {
        return (int) ((globalY - y) * quality);
    }

    private static double devicePixelRatio() {
        var window = MinecraftClient.getInstance().getWindow();
        if (window.getWidth() <= 0) {
            return 1.0D;
        }
        return Math.max(1.0D, window.getFramebufferWidth() / (double) window.getWidth());
    }
}
