package im.webui.screen;

import im.webui.backend.BrowserViewport;
import net.minecraft.client.MinecraftClient;

public record MusicPanelLayout(
        int x,
        int y,
        int width,
        int height,
        int cornerRadius
) {
    private static final double CORNER_RADIUS = 18.0D;
    private static final double LARGE_WIDTH_RATIO = 0.80D;
    private static final double LARGE_HEIGHT_RATIO = 0.82D;
    private static final double COMPACT_WIDTH_RATIO = 0.92D;
    private static final double COMPACT_HEIGHT_RATIO = 0.90D;

    public static MusicPanelLayout current() {
        var window = MinecraftClient.getInstance().getWindow();
        return calculate(
                window.getFramebufferWidth(),
                window.getFramebufferHeight(),
                window.getScaleFactor()
        );
    }

    public static MusicPanelLayout calculate(
            int framebufferWidth,
            int framebufferHeight,
            double guiScale
    ) {
        int safeWidth = Math.max(1, framebufferWidth);
        int safeHeight = Math.max(1, framebufferHeight);
        double safeScale = Math.max(1.0D, guiScale);
        // Minecraft's GUI scale can be 4 on a Retina display even when the window
        // itself is large. Use framebuffer size for the breakpoint so high GUI
        // scales do not incorrectly turn a desktop window into the compact layout.
        boolean compact = safeWidth < 1_280 || safeHeight < 720;
        double widthRatio = compact ? COMPACT_WIDTH_RATIO : LARGE_WIDTH_RATIO;
        double heightRatio = compact ? COMPACT_HEIGHT_RATIO : LARGE_HEIGHT_RATIO;

        int minimumMargin = Math.max(2, (int) Math.round(8.0D * safeScale));
        int availableWidth = Math.max(1, safeWidth - minimumMargin * 2);
        int availableHeight = Math.max(1, safeHeight - minimumMargin * 2);
        int panelWidth = Math.min(
                availableWidth,
                Math.max(1, (int) Math.round(safeWidth * widthRatio))
        );
        int panelHeight = Math.min(
                availableHeight,
                Math.max(1, (int) Math.round(safeHeight * heightRatio))
        );
        int x = Math.max(0, (safeWidth - panelWidth) / 2);
        int y = Math.max(0, (safeHeight - panelHeight) / 2);
        return new MusicPanelLayout(
                x,
                y,
                panelWidth,
                panelHeight,
                Math.max(1, (int) Math.round(CORNER_RADIUS * safeScale))
        );
    }

    public BrowserViewport browserViewport() {
        return new BrowserViewport(
                x,
                y,
                width,
                height,
                false,
                cornerRadius
        );
    }
}
