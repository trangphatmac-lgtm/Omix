package ai.backend;

import java.util.List;
import java.util.concurrent.CompletableFuture;

interface AiProvider {
    CompletableFuture<List<String>> listModels();

    CompletableFuture<String> streamChat(String message, AiStreamListener listener);
}
