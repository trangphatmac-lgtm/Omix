package im.webui.backend.input;

import im.webui.backend.Browser;
import net.minecraft.client.MinecraftClient;

import java.util.List;
import java.util.function.Supplier;

public final class BrowserInputRouter {
    private final Supplier<List<? extends Browser>> browserSupplier;

    public BrowserInputRouter(Supplier<List<? extends Browser>> browserSupplier) {
        this.browserSupplier = browserSupplier;
    }

    public void mouseButton(int button, int action) {
        double[] position = mousePositionInFramebuffer();
        for (Browser browser : acceptingBrowsers()) {
            if (action == 1) {
                if (browser.getViewport().contains(position[0], position[1])) {
                    browser.mouseClicked(position[0], position[1], button);
                }
            } else if (action == 0) {
                browser.mouseReleased(position[0], position[1], button);
            }
        }
    }

    public void mouseMoved(double windowX, double windowY) {
        var window = MinecraftClient.getInstance().getWindow();
        double x = windowX * window.getFramebufferWidth() / (double) window.getWidth();
        double y = windowY * window.getFramebufferHeight() / (double) window.getHeight();
        for (Browser browser : acceptingBrowsers()) {
            browser.mouseMoved(x, y);
        }
    }

    public void mouseScrolled(double vertical) {
        double[] position = mousePositionInFramebuffer();
        for (Browser browser : acceptingBrowsers()) {
            if (browser.getViewport().contains(position[0], position[1])) {
                browser.mouseScrolled(position[0], position[1], vertical);
            }
        }
    }

    public void key(int keyCode, int scanCode, int action, int modifiers) {
        for (Browser browser : acceptingBrowsers()) {
            if (action == 1 || action == 2) {
                browser.keyPressed(keyCode, scanCode, modifiers);
            } else if (action == 0) {
                browser.keyReleased(keyCode, scanCode, modifiers);
            }
        }
    }

    public void character(int codePoint, int modifiers) {
        for (Browser browser : acceptingBrowsers()) {
            browser.charTyped(codePoint, modifiers);
        }
    }

    private List<? extends Browser> acceptingBrowsers() {
        return browserSupplier.get().stream()
                .filter(Browser::acceptsInput)
                .toList()
                .reversed();
    }

    private static double[] mousePositionInFramebuffer() {
        MinecraftClient client = MinecraftClient.getInstance();
        var window = client.getWindow();
        return new double[]{
                client.mouse.getX() * window.getFramebufferWidth() / (double) window.getWidth(),
                client.mouse.getY() * window.getFramebufferHeight() / (double) window.getHeight()
        };
    }
}
