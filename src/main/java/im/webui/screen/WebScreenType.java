package im.webui.screen;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public record WebScreenType(String routeName, boolean inGame) {
    private static final Map<String, WebScreenType> TYPES = new ConcurrentHashMap<>();

    public static final WebScreenType AI = register(new WebScreenType("ai", true));
    public static final WebScreenType CLICK_GUI = register(new WebScreenType("clickgui", true));
    public static final WebScreenType MUSIC = register(new WebScreenType("music", true));

    public WebScreenType {
        if (routeName == null || routeName.isBlank()) {
            throw new IllegalArgumentException("Screen route name cannot be blank");
        }
    }

    public static WebScreenType register(WebScreenType type) {
        WebScreenType previous = TYPES.putIfAbsent(type.routeName(), type);
        if (previous != null && !previous.equals(type)) {
            throw new IllegalArgumentException("Screen route already registered: " + type.routeName());
        }
        return previous == null ? type : previous;
    }

    public static WebScreenType byName(String name) {
        return TYPES.get(name);
    }

    public static Map<String, WebScreenType> registeredTypes() {
        return Map.copyOf(TYPES);
    }
}
