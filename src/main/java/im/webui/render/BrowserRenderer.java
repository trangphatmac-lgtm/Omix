package im.webui.render;

import im.webui.backend.Browser;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.MinecraftClient;
import java.util.List;

public final class BrowserRenderer {
    private BrowserRenderer() {
    }

    public static void renderAll(DrawContext context, List<? extends Browser> browsers) {
        browsers.stream()
                .filter(Browser::isVisible)
                .sorted(java.util.Comparator.comparingInt(Browser::getPriority))
                .forEach(browser -> render(context, browser));
    }

    public static void render(DrawContext context, Browser browser) {
        if (browser == null || !browser.isVisible() || !browser.isTextureReady()) {
            return;
        }

        var viewport = browser.getViewport();
        double scale = MinecraftClient.getInstance().getWindow().getScaleFactor();
        int x = (int) Math.round(viewport.x() / scale);
        int y = (int) Math.round(viewport.y() / scale);
        int width = (int) Math.round(viewport.width() / scale);
        int height = (int) Math.round(viewport.height() / scale);
        int textureWidth = Math.max(1, browser.getTextureWidth());
        int textureHeight = Math.max(1, browser.getTextureHeight());

        context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                browser.getTextureIdentifier(),
                x,
                y,
                0.0F,
                0.0F,
                width,
                height,
                textureWidth,
                textureHeight
        );
    }
}
