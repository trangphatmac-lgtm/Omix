package im.webui.backend;

import net.minecraft.client.MinecraftClient;

public record BrowserViewport(int x, int y, int width, int height, boolean fullscreen) {
    public static BrowserViewport fullFrame() {
        var window = MinecraftClient.getInstance().getWindow();
        return new BrowserViewport(0, 0, window.getFramebufferWidth(), window.getFramebufferHeight(), true);
    }

    public int relativeX(double globalX) {
        return (int) Math.round(globalX - x);
    }

    public int relativeY(double globalY) {
        return (int) Math.round(globalY - y);
    }

    public BrowserViewport resized(int width, int height) {
        return fullscreen ? new BrowserViewport(x, y, width, height, true) : this;
    }

    public int scaledWidth(float quality) {
        return Math.max(1, (int) (width * quality));
    }

    public int scaledHeight(float quality) {
        return Math.max(1, (int) (height * quality));
    }

    public double zoomLevel(float quality) {
        return Math.log(quality) / Math.log(1.2D);
    }

    public int transformMouseX(double globalX, float quality) {
        return (int) ((globalX - x) * quality);
    }

    public int transformMouseY(double globalY, float quality) {
        return (int) ((globalY - y) * quality);
    }
}
