package im.webui.theme;

import im.webui.screen.WebScreenType;

import java.util.Objects;

public final class WebThemeManager {
    private volatile WebTheme current;

    public void useBundled(String authenticatedBaseUrl) {
        current = new WebTheme("remix-bundled", authenticatedBaseUrl, false);
    }

    public void useExternal(String id, String baseUrl) {
        current = new WebTheme(id, baseUrl, true);
    }

    public WebTheme getCurrent() {
        return Objects.requireNonNull(current, "No WebUI theme has been selected");
    }

    public String getScreenUrl(WebScreenType type) {
        return getCurrent().screenUrl(type);
    }
}
