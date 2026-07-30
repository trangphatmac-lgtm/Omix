package ai.backend;

import java.util.List;
import java.util.concurrent.CompletableFuture;

interface AiProvider {
    CompletableFuture<List<String>> listModels();

    CompletableFuture<AiTurnResult> streamChat(
            AiGameContext gameContext,
            String message,
            AiChatMode mode,
            AiStreamListener listener
    );
}
