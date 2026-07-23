package ai.backend;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

final class OpenAiCompatibleProvider implements AiProvider {
    private static final Duration MODEL_REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration CHAT_REQUEST_TIMEOUT = Duration.ofMinutes(5);

    private final AiConfig config;
    private final HttpClient httpClient;
    private final Executor executor;

    OpenAiCompatibleProvider(AiConfig config, Executor executor) {
        this.config = config;
        this.executor = executor;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .executor(executor)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public CompletableFuture<List<String>> listModels() {
        AiConfig.Snapshot settings = config.snapshot();
        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint(settings, "models"))
                .timeout(MODEL_REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET();
        addAuthorization(request, settings);

        return httpClient.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    requireSuccessful(response.statusCode(), response.body());
                    JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                    JsonArray data = root.has("data") && root.get("data").isJsonArray()
                            ? root.getAsJsonArray("data")
                            : new JsonArray();
                    List<String> models = new ArrayList<>();
                    for (JsonElement element : data) {
                        if (!element.isJsonObject()) continue;
                        JsonObject model = element.getAsJsonObject();
                        if (model.has("id") && !model.get("id").isJsonNull()) {
                            String id = model.get("id").getAsString();
                            if (!id.isBlank()) {
                                models.add(id);
                            }
                        }
                    }
                    return models;
                });
    }

    @Override
    public CompletableFuture<String> streamChat(String message, AiStreamListener listener) {
        AiConfig.Snapshot settings = config.snapshot();
        if (settings.model().isBlank()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Configure a model with .ai model <model>."));
        }

        JsonObject body = new JsonObject();
        body.addProperty("model", settings.model());
        body.addProperty("stream", true);
        JsonArray messages = new JsonArray();
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", message);
        messages.add(userMessage);
        body.add("messages", messages);

        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint(settings, "chat/completions"))
                .timeout(CHAT_REQUEST_TIMEOUT)
                .header("Accept", "text/event-stream")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
        addAuthorization(request, settings);

        return httpClient.sendAsync(request.build(), HttpResponse.BodyHandlers.ofInputStream())
                .thenCompose(response -> CompletableFuture.supplyAsync(() -> {
                    try (InputStream stream = response.body()) {
                        if (response.statusCode() < 200 || response.statusCode() >= 300) {
                            String errorBody = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                            throw apiError(response.statusCode(), errorBody);
                        }
                        return readResponse(stream, listener);
                    } catch (IOException exception) {
                        throw new CompletionException(exception);
                    }
                }, executor));
    }

    private String readResponse(InputStream stream, AiStreamListener listener) throws IOException {
        StringBuilder complete = new StringBuilder();
        StringBuilder eventData = new StringBuilder();
        StringBuilder rawResponse = new StringBuilder();
        boolean sawServerSentEvent = false;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    if (!eventData.isEmpty()) {
                        if (consumeEvent(eventData.toString(), complete, listener)) {
                            return complete.toString();
                        }
                        eventData.setLength(0);
                    }
                    continue;
                }

                if (line.startsWith("data:")) {
                    sawServerSentEvent = true;
                    if (!eventData.isEmpty()) {
                        eventData.append('\n');
                    }
                    String data = line.substring("data:".length());
                    eventData.append(data.startsWith(" ") ? data.substring(1) : data);
                } else if (!line.startsWith(":")) {
                    if (!rawResponse.isEmpty()) {
                        rawResponse.append('\n');
                    }
                    rawResponse.append(line);
                }
            }
        }

        if (!eventData.isEmpty()) {
            consumeEvent(eventData.toString(), complete, listener);
        }
        if (!sawServerSentEvent && !rawResponse.isEmpty()) {
            String content = parseNonStreamingContent(rawResponse.toString());
            if (!content.isEmpty()) {
                listener.onDelta(content);
                complete.append(content);
            }
        }
        return complete.toString();
    }

    private boolean consumeEvent(String data, StringBuilder complete, AiStreamListener listener) {
        if ("[DONE]".equals(data.trim())) {
            return true;
        }

        JsonObject root = JsonParser.parseString(data).getAsJsonObject();
        if (!root.has("choices") || !root.get("choices").isJsonArray()) {
            return false;
        }

        JsonArray choices = root.getAsJsonArray("choices");
        if (choices.isEmpty() || !choices.get(0).isJsonObject()) {
            return false;
        }

        JsonObject choice = choices.get(0).getAsJsonObject();
        if (!choice.has("delta") || !choice.get("delta").isJsonObject()) {
            return false;
        }

        JsonObject delta = choice.getAsJsonObject("delta");
        if (delta.has("content") && !delta.get("content").isJsonNull()) {
            String content = delta.get("content").getAsString();
            if (!content.isEmpty()) {
                listener.onDelta(content);
                complete.append(content);
            }
        }
        return false;
    }

    private String parseNonStreamingContent(String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        if (!root.has("choices") || !root.get("choices").isJsonArray()) {
            return "";
        }
        JsonArray choices = root.getAsJsonArray("choices");
        if (choices.isEmpty() || !choices.get(0).isJsonObject()) {
            return "";
        }
        JsonObject choice = choices.get(0).getAsJsonObject();
        if (!choice.has("message") || !choice.get("message").isJsonObject()) {
            return "";
        }
        JsonObject message = choice.getAsJsonObject("message");
        return message.has("content") && !message.get("content").isJsonNull()
                ? message.get("content").getAsString()
                : "";
    }

    private URI endpoint(AiConfig.Snapshot settings, String path) {
        return URI.create(settings.baseUrl() + "/" + path);
    }

    private void addAuthorization(HttpRequest.Builder request, AiConfig.Snapshot settings) {
        if (!settings.apiKey().isBlank()) {
            request.header("Authorization", "Bearer " + settings.apiKey());
        }
    }

    private void requireSuccessful(int statusCode, String body) {
        if (statusCode < 200 || statusCode >= 300) {
            throw apiError(statusCode, body);
        }
    }

    private RuntimeException apiError(int statusCode, String body) {
        String message = "";
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (root.has("error") && root.get("error").isJsonObject()) {
                JsonObject error = root.getAsJsonObject("error");
                if (error.has("message") && !error.get("message").isJsonNull()) {
                    message = error.get("message").getAsString();
                }
            }
        } catch (Exception ignored) {
            // Fall back to a shortened plain-text response below.
        }

        if (message.isBlank()) {
            message = body == null ? "" : body.replaceAll("\\s+", " ").trim();
            if (message.length() > 300) {
                message = message.substring(0, 300) + "...";
            }
        }
        return new IllegalStateException("AI API returned HTTP " + statusCode
                + (message.isBlank() ? "." : ": " + message));
    }
}
