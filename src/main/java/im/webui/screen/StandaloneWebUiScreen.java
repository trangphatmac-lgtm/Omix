package im.webui.screen;

import im.webui.WebUiRuntime;
import im.webui.backend.Browser;
import im.webui.backend.BrowserSettings;
import im.webui.backend.BrowserViewport;
import im.webui.render.BrowserRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public final class StandaloneWebUiScreen extends Screen implements AutoCloseable {
    private final Screen parent;
    private final WebScreenType type;
    private Browser browser;

    public StandaloneWebUiScreen(Screen parent, WebScreenType type) {
        super(Text.literal("Remix WebUI — " + type.routeName()));
        this.parent = parent;
        this.type = type;
    }

    @Override
    protected void init() {
        if (browser == null) {
            browser = WebUiRuntime.getInstance().createBrowser(
                    type,
                    BrowserViewport.fullFrame(),
                    BrowserSettings.DEFAULT,
                    (short) 20,
                    () -> client != null && client.currentScreen == this
            );
        }
        browser.setVisible(true);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        BrowserRenderer.render(context, browser);
    }

    @Override
    public void close() {
        if (browser != null) {
            browser.close();
            browser = null;
        }
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
