package im.webui.theme;

import im.webui.screen.WebScreenType;

public record WebTheme(String id, String baseUrl, boolean external) {
    public WebTheme {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Theme id cannot be blank");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Theme base URL cannot be blank");
        }
    }

    public String screenUrl(WebScreenType type) {
        int fragment = baseUrl.indexOf('#');
        String withoutFragment = fragment < 0 ? baseUrl : baseUrl.substring(0, fragment);
        return withoutFragment + "#/" + type.routeName();
    }
}
