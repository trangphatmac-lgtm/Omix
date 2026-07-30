package im.webui.backend;

public record BrowserAccelerationFlags(boolean supported, boolean beta) {
    public static final BrowserAccelerationFlags UNSUPPORTED =
            new BrowserAccelerationFlags(false, false);
}
