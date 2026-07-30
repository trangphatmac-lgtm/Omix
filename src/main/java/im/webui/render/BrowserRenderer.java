package im.webui.render;

import cn.omix.util.render.Render2D;
import im.webui.backend.Browser;
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
        float x = (float) (viewport.x() / scale);
        float y = (float) (viewport.y() / scale);
        float width = (float) (viewport.width() / scale);
        float height = (float) (viewport.height() / scale);

        // DrawContext's integer texture overload uses the destination width/height as
        // the sampled pixel region as well. With a GUI scale above 1 that cropped the
        // CEF texture to its upper-left quadrant. Use normalized 0..1 UVs so the whole
        // framebuffer-sized browser texture is mapped into GUI coordinates.
        float cornerRadius = (float) (viewport.cornerRadius() / scale);
        if (cornerRadius > 0.0F) {
            Render2D.drawRoundedTexture(
                    context,
                    browser.getTextureIdentifier(),
                    x,
                    y,
                    width,
                    height,
                    cornerRadius
            );
        } else {
            Render2D.drawTexture(
                    context,
                    browser.getTextureIdentifier(),
                    x,
                    y,
                    width,
                    height
            );
        }
    }
}
