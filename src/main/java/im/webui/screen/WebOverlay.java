package im.webui.screen;

import im.webui.WebUiRuntime;
import im.webui.backend.Browser;
import im.webui.backend.BrowserSettings;
import im.webui.backend.BrowserViewport;
import im.webui.backend.input.InputAcceptor;

public final class WebOverlay implements AutoCloseable {
    private final WebScreenType type;
    private final BrowserSettings settings;
    private final short priority;
    private final InputAcceptor inputAcceptor;
    private Browser browser;

    public WebOverlay(
            WebScreenType type,
            BrowserSettings settings,
            short priority,
            InputAcceptor inputAcceptor
    ) {
        this.type = type;
        this.settings = settings;
        this.priority = priority;
        this.inputAcceptor = inputAcceptor;
    }

    public Browser open() {
        if (browser == null) {
            browser = WebUiRuntime.getInstance().createBrowser(
                    type,
                    BrowserViewport.fullFrame(),
                    settings,
                    priority,
                    inputAcceptor
            );
        }
        browser.setVisible(true);
        return browser;
    }

    public boolean isOpen() {
        return browser != null;
    }

    public boolean isVisible() {
        return browser != null && browser.isVisible();
    }

    public void setVisible(boolean visible) {
        if (visible) {
            open();
        } else if (browser != null) {
            browser.setVisible(false);
        }
    }

    @Override
    public void close() {
        if (browser != null) {
            browser.close();
            browser = null;
        }
    }
}
