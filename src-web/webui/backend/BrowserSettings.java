package im.webui.backend;

public record BrowserSettings(int fps, float quality, boolean accelerated) {
    public static final BrowserSettings DEFAULT = new BrowserSettings(60, 1.0F, false);

    public BrowserSettings {
        fps = Math.max(1, fps);
        quality = Math.clamp(quality, 0.5F, 1.0F);
    }
}
