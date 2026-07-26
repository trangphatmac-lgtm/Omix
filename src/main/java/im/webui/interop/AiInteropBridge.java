package im.webui.interop;

import ai.backend.AiBackend;
import ai.backend.AiChatMode;
import ai.backend.AiStreamListener;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.netty.handler.codec.http.HttpResponseStatus;
import me.ksyz.accountmanager.auth.SessionService;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public final class AiInteropBridge {
    private static final String CONFIG_PATH = "/api/v1/ai/config";
    private static final String CONVERSATION_PATH = "/api/v1/ai/conversation";

    private final AiBackend backend;

    public AiInteropBridge(InteropServer server, AiBackend backend) {
        this.backend = backend;
        registerRoutes(server.getRoutes());
        registerSocketEvents(server.getSocketEvents());
    }

    private void registerRoutes(InteropRouteRegistry routes) {
        routes.get(CONFIG_PATH, ignored ->
                InteropResponse.json(HttpResponseStatus.OK, configuration()));
        routes.put(CONFIG_PATH, request -> {
            try {
                JsonObject body = request.body();
                boolean refreshModels = false;
                if (body.has("baseUrl")) {
                    backend.setBaseUrl(body.get("baseUrl").getAsString());
                    refreshModels = true;
                }
                if (body.has("apiKey")) {
                    backend.setApiKey(body.get("apiKey").getAsString());
                    refreshModels = true;
                }
                if (body.has("model")) {
                    backend.setModel(body.get("model").getAsString());
                }
                if (body.has("thinking")) {
                    backend.setThinking(body.get("thinking").getAsBoolean());
                }
                if (refreshModels && backend.hasApiKey()) {
                    backend.refreshModels().exceptionally(error -> java.util.List.of());
                }
                return InteropResponse.json(HttpResponseStatus.OK, configuration());
            } catch (IllegalArgumentException exception) {
                return InteropResponse.text(HttpResponseStatus.BAD_REQUEST, errorMessage(exception));
            } catch (Exception exception) {
                return InteropResponse.text(
                        HttpResponseStatus.INTERNAL_SERVER_ERROR,
                        errorMessage(exception)
                );
            }
        });
        routes.get(CONVERSATION_PATH, request -> {
            try {
                AiChatMode mode = AiChatMode.fromName(firstQueryValue(request, "mode"));
                JsonObject response = new JsonObject();
                response.addProperty("mode", mode.routeName());
                response.add("messages", backend.getConversationHistory(mode));
                return InteropResponse.json(HttpResponseStatus.OK, response);
            } catch (IllegalArgumentException exception) {
                return InteropResponse.text(HttpResponseStatus.BAD_REQUEST, errorMessage(exception));
            }
        });
        routes.delete(CONVERSATION_PATH, request -> {
            try {
                String modeName = firstQueryValue(request, "mode");
                AiChatMode mode = AiChatMode.fromName(modeName);
                int removed = backend.clearConversation(mode);
                JsonObject response = new JsonObject();
                response.addProperty("mode", mode.routeName());
                response.addProperty("removed", removed);
                return InteropResponse.json(HttpResponseStatus.OK, response);
            } catch (IllegalArgumentException | IllegalStateException exception) {
                return InteropResponse.text(HttpResponseStatus.BAD_REQUEST, errorMessage(exception));
            }
        });
    }

    private void registerSocketEvents(WebSocketEventBus socketEvents) {
        socketEvents.register("aiChat", (event, reply) -> {
            String requestId = stringValue(event, "requestId");
            String message = stringValue(event, "message");
            final AiChatMode mode;
            try {
                if (requestId.isBlank()) {
                    throw new IllegalArgumentException("Missing request ID.");
                }
                mode = AiChatMode.fromName(stringValue(event, "mode"));
                if (message.isBlank()) {
                    throw new IllegalArgumentException("Message cannot be empty.");
                }
            } catch (IllegalArgumentException exception) {
                reply.accept("aiError", event(requestId, errorMessage(exception)));
                return;
            }

            JsonObject started = event(requestId, "");
            started.addProperty("mode", mode.routeName());
            reply.accept("aiStarted", started);

            final String username;
            try {
                username = SessionService.current().getUsername();
            } catch (Exception exception) {
                reply.accept("aiError", event(requestId, errorMessage(exception)));
                return;
            }
            backend.streamChat(username, message, mode, new AiStreamListener() {
                @Override
                public void onDelta(String content) {
                    if (!content.isEmpty()) {
                        reply.accept("aiDelta", event(requestId, content));
                    }
                }

                @Override
                public void onReasoning(String content) {
                    if (!content.isEmpty()) {
                        reply.accept("aiReasoning", event(requestId, content));
                    }
                }

                @Override
                public void onToolCall(String id, String name, String arguments) {
                    JsonObject payload = event(requestId, "");
                    payload.addProperty("toolCallId", id);
                    payload.addProperty("toolName", name);
                    payload.addProperty("arguments", arguments);
                    reply.accept("aiToolCall", payload);
                }

                @Override
                public void onToolResult(String id, String content) {
                    JsonObject payload = event(requestId, content);
                    payload.addProperty("toolCallId", id);
                    reply.accept("aiToolResult", payload);
                }
            }).whenComplete((content, error) -> {
                if (error != null) {
                    reply.accept("aiError", event(requestId, errorMessage(error)));
                    return;
                }
                reply.accept("aiComplete", event(requestId, content));
            });
        });
    }

    private JsonObject configuration() {
        JsonObject response = new JsonObject();
        response.addProperty("baseUrl", backend.getBaseUrl());
        response.addProperty("hasApiKey", backend.hasApiKey());
        response.addProperty("model", backend.getModel());
        response.addProperty("thinking", backend.isThinkingEnabled());
        response.addProperty("active", backend.isChatActive());
        JsonObject history = new JsonObject();
        history.addProperty("chat", backend.getConversationMessageCount(AiChatMode.CHAT));
        history.addProperty("agent", backend.getConversationMessageCount(AiChatMode.AGENT));
        response.add("history", history);
        JsonArray models = new JsonArray();
        backend.getModelSuggestions().forEach(models::add);
        response.add("models", models);
        return response;
    }

    private static JsonObject event(String requestId, String content) {
        JsonObject event = new JsonObject();
        event.addProperty("requestId", requestId);
        event.addProperty("content", content == null ? "" : content);
        return event;
    }

    private static String stringValue(JsonObject object, String name) {
        return object != null && object.has(name) && !object.get(name).isJsonNull()
                ? object.get(name).getAsString()
                : "";
    }

    private static String firstQueryValue(InteropRequest request, String key) {
        var values = request.query().get(key);
        return values == null || values.isEmpty() ? null : values.getFirst();
    }

    private static String errorMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null
                && (cause instanceof CompletionException || cause instanceof ExecutionException)) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
