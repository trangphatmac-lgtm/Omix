package im.webui.interop;

import com.google.gson.JsonObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public final class WebSocketEventBus {
    @FunctionalInterface
    public interface Handler {
        void handle(JsonObject event, BiConsumer<String, JsonObject> reply);
    }

    private final Map<String, Handler> handlers = new ConcurrentHashMap<>();

    public void register(String name, Handler handler) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("WebSocket event name cannot be blank");
        }
        if (handlers.putIfAbsent(name, handler) != null) {
            throw new IllegalArgumentException("WebSocket event already registered: " + name);
        }
    }

    public boolean dispatch(
            String name,
            JsonObject event,
            BiConsumer<String, JsonObject> reply
    ) {
        Handler handler = handlers.get(name);
        if (handler == null) {
            return false;
        }
        handler.handle(event == null ? new JsonObject() : event, reply);
        return true;
    }

    public Map<String, Handler> registeredHandlers() {
        return Map.copyOf(handlers);
    }
}
