package ai.backend;

import com.google.gson.JsonArray;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AiBackend implements AutoCloseable {
    private static final long MODEL_CACHE_DURATION_MS = 5 * 60 * 1000L;
    private static final long MODEL_RETRY_DELAY_MS = 30 * 1000L;

    private final AiConfig config;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AiProvider provider;
    private final AtomicBoolean chatActive = new AtomicBoolean();

    private volatile List<String> cachedModels = List.of();
    private volatile long lastModelRefresh;
    private volatile long lastModelAttempt;
    private CompletableFuture<List<String>> modelRefresh;

    public AiBackend(Path configFile) {
        this.config = new AiConfig(configFile);
        this.provider = new OpenAiCompatibleProvider(config, executor);
    }

    public String getBaseUrl() {
        return config.snapshot().baseUrl();
    }

    public boolean hasApiKey() {
        return !config.snapshot().apiKey().isBlank();
    }

    public String getModel() {
        return config.snapshot().model();
    }

    public boolean isThinkingEnabled() {
        return config.snapshot().thinking();
    }

    public void setBaseUrl(String baseUrl) {
        config.setBaseUrl(baseUrl);
        invalidateModels();
    }

    public void setApiKey(String apiKey) {
        config.setApiKey(apiKey);
        invalidateModels();
    }

    public void setModel(String model) {
        config.setModel(model);
    }

    public void setThinking(boolean thinking) {
        config.setThinking(thinking);
    }

    public boolean isChatActive() {
        return chatActive.get();
    }

    public int getConversationMessageCount() {
        return getConversationMessageCount(AiChatMode.AGENT);
    }

    public int getConversationMessageCount(AiChatMode mode) {
        return config.getHistorySize(mode);
    }

    public JsonArray getConversationHistory(AiChatMode mode) {
        return config.getSerializedHistory(mode);
    }

    public int clearConversation() {
        return clearConversation(AiChatMode.AGENT);
    }

    public int clearConversation(AiChatMode mode) {
        if (chatActive.get()) {
            throw new IllegalStateException("Wait for the current AI response before clearing context.");
        }
        return config.clearHistory(mode);
    }

    public CompletableFuture<String> streamChat(String username, String message, AiStreamListener listener) {
        return streamChat(username, message, AiChatMode.AGENT, listener);
    }

    public CompletableFuture<String> streamChat(
            String username,
            String message,
            AiChatMode mode,
            AiStreamListener listener
    ) {
        if (message == null || message.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Message cannot be empty."));
        }
        if (mode == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("AI mode cannot be null."));
        }
        if (!chatActive.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Another AI response is still streaming."));
        }

        final CompletableFuture<AiTurnResult> request;
        try {
            request = provider.streamChat(username, message, mode, listener);
        } catch (Exception exception) {
            chatActive.set(false);
            return CompletableFuture.failedFuture(exception);
        }
        return request.thenApply(result -> {
            if (!result.messages().isEmpty()) {
                config.appendTurn(mode, message, result.messages());
            }
            return result.content();
        }).whenComplete((ignored, error) -> chatActive.set(false));
    }

    public synchronized CompletableFuture<List<String>> refreshModels() {
        if (modelRefresh != null && !modelRefresh.isDone()) {
            return modelRefresh;
        }

        lastModelAttempt = System.currentTimeMillis();
        CompletableFuture<List<String>> request = provider.listModels()
                .thenApply(models -> models.stream()
                        .filter(model -> model != null && !model.isBlank())
                        .distinct()
                        .sorted(Comparator.naturalOrder())
                        .toList());
        modelRefresh = request;
        request.whenComplete((models, error) -> finishModelRefresh(request, models, error));
        return request;
    }

    public List<String> getModelSuggestions() {
        long now = System.currentTimeMillis();
        if (now - lastModelRefresh > MODEL_CACHE_DURATION_MS
                && now - lastModelAttempt > MODEL_RETRY_DELAY_MS) {
            refreshModels().exceptionally(error -> List.of());
        }

        LinkedHashSet<String> suggestions = new LinkedHashSet<>(cachedModels);
        String currentModel = getModel();
        if (!currentModel.isBlank()) {
            suggestions.add(currentModel);
        }
        return new ArrayList<>(suggestions);
    }

    private synchronized void finishModelRefresh(
            CompletableFuture<List<String>> request,
            List<String> models,
            Throwable error
    ) {
        if (modelRefresh != request) {
            return;
        }
        if (error == null) {
            cachedModels = List.copyOf(models);
            lastModelRefresh = System.currentTimeMillis();
        }
        modelRefresh = null;
    }

    private synchronized void invalidateModels() {
        cachedModels = List.of();
        lastModelRefresh = 0L;
        lastModelAttempt = 0L;
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
