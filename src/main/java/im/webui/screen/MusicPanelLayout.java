package im.webui.screen;

import im.webui.backend.BrowserViewport;
import net.minecraft.client.MinecraftClient;

public record MusicPanelLayout(
        int x,
        int y,
        int width,
        int height,
        int chromeHeight,
        int border
) {
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
        boolean compact = safeWidth / safeScale < 1_000.0D
                || safeHeight / safeScale < 620.0D;
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
        int border = Math.max(1, (int) Math.round(safeScale));
        int desiredChromeHeight = Math.max(
                border + 1,
                (int) Math.round(22.0D * safeScale)
        );
        int chromeHeight = Math.min(
                desiredChromeHeight,
                Math.max(border + 1, panelHeight / 5)
        );
        return new MusicPanelLayout(
                x,
                y,
                panelWidth,
                panelHeight,
                chromeHeight,
                border
        );
    }

    public BrowserViewport browserViewport() {
        int browserX = x + border;
        int browserY = y + chromeHeight;
        int browserWidth = Math.max(1, width - border * 2);
        int browserHeight = Math.max(1, height - chromeHeight - border);
        return new BrowserViewport(
                browserX,
                browserY,
                browserWidth,
                browserHeight,
                false
        );
    }
}
