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
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

final class OpenAiCompatibleProvider implements AiProvider {
    private static final Duration MODEL_REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration CHAT_REQUEST_TIMEOUT = Duration.ofMinutes(5);
    private static final int MAX_TOOL_ROUNDS = 8;
    private static final int MAX_TOOL_CALLS_PER_ROUND = 16;

    private final AiConfig config;
    private final HttpClient httpClient;
    private final Executor executor;
    private final AiToolExecutor toolExecutor = new MinecraftCommandToolExecutor();

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
    public CompletableFuture<AiTurnResult> streamChat(
            String username,
            String message,
            AiChatMode mode,
            AiStreamListener listener
    ) {
        AiConfig.Snapshot settings = config.snapshot();
        if (settings.model().isBlank()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Configure a model with .ai model <model>."));
        }

        AiToolSnapshot toolSnapshot = mode == AiChatMode.AGENT
                ? toolExecutor.snapshot()
                : new AiToolSnapshot(new JsonArray(), "");
        JsonArray messages = buildMessages(settings, username, message, mode, toolSnapshot);
        return runChatLoop(
                settings,
                messages,
                toolSnapshot,
                listener,
                new ArrayList<>(),
                new StringBuilder(),
                0
        );
    }

    private JsonArray buildMessages(
            AiConfig.Snapshot settings,
            String username,
            String message,
            AiChatMode mode,
            AiToolSnapshot toolSnapshot
    ) {
        JsonArray messages = new JsonArray();
        if (mode == AiChatMode.AGENT) {
            JsonObject systemMessage = new JsonObject();
            systemMessage.addProperty("role", "system");
            systemMessage.addProperty("content", AiSystemPrompt.forUser(username, toolSnapshot.promptContext()));
            messages.add(systemMessage);
        }
        for (AiMessage historyMessage : settings.history(mode)) {
            messages.add(historyMessage.toJson());
        }
        messages.add(AiMessage.user(message).toJson());
        return messages;
    }

    private CompletableFuture<AiTurnResult> runChatLoop(
            AiConfig.Snapshot settings,
            JsonArray messages,
            AiToolSnapshot toolSnapshot,
            AiStreamListener listener,
            List<AiMessage> turnMessages,
            StringBuilder completeAnswer,
            int toolRound
    ) {
        AiStreamListener roundListener = separatedRoundListener(listener, !completeAnswer.isEmpty());
        return requestCompletion(settings, messages, toolSnapshot, roundListener).thenCompose(completion -> {
            AiMessage assistant = AiMessage.assistant(
                    completion.content(),
                    completion.reasoningContent(),
                    completion.toolCalls()
            );
            messages.add(assistant.toJson());
            turnMessages.add(assistant);
            if (!completeAnswer.isEmpty() && !completion.content().isEmpty()) {
                completeAnswer.append('\n');
            }
            completeAnswer.append(completion.content());

            if (completion.toolCalls().isEmpty()) {
                return CompletableFuture.completedFuture(
                        new AiTurnResult(completeAnswer.toString(), turnMessages)
                );
            }
            if (toolRound >= MAX_TOOL_ROUNDS) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("AI exceeded the maximum of " + MAX_TOOL_ROUNDS
                                + " command-tool rounds.")
                );
            }
            if (completion.toolCalls().size() > MAX_TOOL_CALLS_PER_ROUND) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("AI requested too many command tools in one response.")
                );
            }

            return executeTools(completion.toolCalls(), messages, turnMessages, listener)
                    .thenCompose(ignored -> runChatLoop(
                            settings,
                            messages,
                            toolSnapshot,
                            listener,
                            turnMessages,
                            completeAnswer,
                            toolRound + 1
                    ));
        });
    }

    private AiStreamListener separatedRoundListener(AiStreamListener listener, boolean needsSeparator) {
        if (!needsSeparator) {
            return listener;
        }
        AtomicBoolean firstContent = new AtomicBoolean(true);
        return new AiStreamListener() {
            @Override
            public void onDelta(String content) {
                if (!content.isEmpty() && firstContent.compareAndSet(true, false)) {
                    listener.onDelta("\n");
                }
                listener.onDelta(content);
            }

            @Override
            public void onReasoning(String content) {
                listener.onReasoning(content);
            }
        };
    }

    private CompletableFuture<Void> executeTools(
            List<AiToolCall> toolCalls,
            JsonArray messages,
            List<AiMessage> turnMessages,
            AiStreamListener listener
    ) {
        CompletableFuture<Void> sequence = CompletableFuture.completedFuture(null);
        for (AiToolCall toolCall : toolCalls) {
            listener.onToolCall(
                    toolCall.id(),
                    toolCall.name(),
                    toolCall.arguments()
            );
            sequence = sequence.thenCompose(ignored -> toolExecutor.execute(toolCall)
                    .handle((content, error) -> error == null
                            ? content
                            : "Tool execution failed: " + throwableMessage(error))
                    .thenAccept(content -> {
                        listener.onToolResult(toolCall.id(), content);
                        AiMessage toolMessage = AiMessage.tool(toolCall.id(), content);
                        messages.add(toolMessage.toJson());
                        turnMessages.add(toolMessage);
                    }));
        }
        return sequence;
    }

    private CompletableFuture<AiCompletion> requestCompletion(
            AiConfig.Snapshot settings,
            JsonArray messages,
            AiToolSnapshot toolSnapshot,
            AiStreamListener listener
    ) {
        JsonObject body = new JsonObject();
        body.addProperty("model", settings.model());
        body.addProperty("stream", true);
        JsonObject thinking = new JsonObject();
        thinking.addProperty("type", settings.thinking() ? "enabled" : "disabled");
        body.add("thinking", thinking);
        body.add("messages", messages.deepCopy());
        if (!toolSnapshot.definitions().isEmpty()) {
            body.add("tools", toolSnapshot.definitions().deepCopy());
            body.addProperty("tool_choice", "auto");
        }

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

    private AiCompletion readResponse(InputStream stream, AiStreamListener listener) throws IOException {
        CompletionAccumulator accumulator = new CompletionAccumulator();
        StringBuilder eventData = new StringBuilder();
        StringBuilder rawResponse = new StringBuilder();
        boolean sawServerSentEvent = false;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    if (!eventData.isEmpty()) {
                        if (consumeEvent(eventData.toString(), accumulator, listener)) {
                            return accumulator.toCompletion();
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
            consumeEvent(eventData.toString(), accumulator, listener);
        }
        if (!sawServerSentEvent && !rawResponse.isEmpty()) {
            return parseNonStreamingCompletion(rawResponse.toString(), listener);
        }
        return accumulator.toCompletion();
    }

    private boolean consumeEvent(
            String data,
            CompletionAccumulator accumulator,
            AiStreamListener listener
    ) {
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
        if (choice.has("finish_reason") && !choice.get("finish_reason").isJsonNull()) {
            accumulator.finishReason = choice.get("finish_reason").getAsString();
        }
        if (!choice.has("delta") || !choice.get("delta").isJsonObject()) {
            return false;
        }

        JsonObject delta = choice.getAsJsonObject("delta");
        if (delta.has("reasoning_content") && !delta.get("reasoning_content").isJsonNull()) {
            String reasoning = delta.get("reasoning_content").getAsString();
            if (!reasoning.isEmpty()) {
                listener.onReasoning(reasoning);
                accumulator.reasoningContent.append(reasoning);
            }
        }
        if (delta.has("content") && !delta.get("content").isJsonNull()) {
            String content = delta.get("content").getAsString();
            if (!content.isEmpty()) {
                listener.onDelta(content);
                accumulator.content.append(content);
            }
        }
        if (delta.has("tool_calls") && delta.get("tool_calls").isJsonArray()) {
            JsonArray calls = delta.getAsJsonArray("tool_calls");
            for (int position = 0; position < calls.size(); position++) {
                JsonElement element = calls.get(position);
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject call = element.getAsJsonObject();
                int index = call.has("index") && !call.get("index").isJsonNull()
                        ? call.get("index").getAsInt()
                        : position;
                accumulator.toolCalls
                        .computeIfAbsent(index, ignored -> new MutableToolCall())
                        .append(call);
            }
        }
        return false;
    }

    private AiCompletion parseNonStreamingCompletion(String body, AiStreamListener listener) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        if (!root.has("choices") || !root.get("choices").isJsonArray()) {
            return AiCompletion.empty();
        }
        JsonArray choices = root.getAsJsonArray("choices");
        if (choices.isEmpty() || !choices.get(0).isJsonObject()) {
            return AiCompletion.empty();
        }
        JsonObject choice = choices.get(0).getAsJsonObject();
        if (!choice.has("message") || !choice.get("message").isJsonObject()) {
            return AiCompletion.empty();
        }
        JsonObject message = choice.getAsJsonObject("message");
        String content = message.has("content") && !message.get("content").isJsonNull()
                ? message.get("content").getAsString()
                : "";
        String reasoning = message.has("reasoning_content") && !message.get("reasoning_content").isJsonNull()
                ? message.get("reasoning_content").getAsString()
                : "";
        List<AiToolCall> toolCalls = new ArrayList<>();
        if (message.has("tool_calls") && message.get("tool_calls").isJsonArray()) {
            for (JsonElement element : message.getAsJsonArray("tool_calls")) {
                if (element.isJsonObject()) {
                    toolCalls.add(AiToolCall.fromJson(element.getAsJsonObject()));
                }
            }
        }
        if (!reasoning.isEmpty()) {
            listener.onReasoning(reasoning);
        }
        if (!content.isEmpty()) {
            listener.onDelta(content);
        }
        String finishReason = choice.has("finish_reason") && !choice.get("finish_reason").isJsonNull()
                ? choice.get("finish_reason").getAsString()
                : "";
        return new AiCompletion(content, reasoning, toolCalls, finishReason);
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

    private static String throwableMessage(Throwable error) {
        Throwable cause = error;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    private record AiCompletion(
            String content,
            String reasoningContent,
            List<AiToolCall> toolCalls,
            String finishReason
    ) {
        private AiCompletion {
            content = content == null ? "" : content;
            reasoningContent = reasoningContent == null ? "" : reasoningContent;
            toolCalls = List.copyOf(toolCalls);
            finishReason = finishReason == null ? "" : finishReason;
        }

        private static AiCompletion empty() {
            return new AiCompletion("", "", List.of(), "");
        }
    }

    private static final class CompletionAccumulator {
        private final StringBuilder content = new StringBuilder();
        private final StringBuilder reasoningContent = new StringBuilder();
        private final Map<Integer, MutableToolCall> toolCalls = new TreeMap<>();
        private String finishReason = "";

        private AiCompletion toCompletion() {
            List<AiToolCall> calls = toolCalls.values().stream()
                    .map(MutableToolCall::build)
                    .toList();
            return new AiCompletion(
                    content.toString(),
                    reasoningContent.toString(),
                    calls,
                    finishReason
            );
        }
    }

    private static final class MutableToolCall {
        private String id = "";
        private final StringBuilder name = new StringBuilder();
        private final StringBuilder arguments = new StringBuilder();

        private void append(JsonObject chunk) {
            if (chunk.has("id") && !chunk.get("id").isJsonNull()) {
                String value = chunk.get("id").getAsString();
                if (!value.isEmpty()) {
                    id = value;
                }
            }
            if (!chunk.has("function") || !chunk.get("function").isJsonObject()) {
                return;
            }
            JsonObject function = chunk.getAsJsonObject("function");
            if (function.has("name") && !function.get("name").isJsonNull()) {
                name.append(function.get("name").getAsString());
            }
            if (function.has("arguments") && !function.get("arguments").isJsonNull()) {
                arguments.append(function.get("arguments").getAsString());
            }
        }

        private AiToolCall build() {
            return new AiToolCall(id, name.toString(), arguments.toString());
        }
    }
}
