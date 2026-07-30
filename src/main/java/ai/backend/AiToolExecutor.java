package ai.backend;

import java.util.concurrent.CompletableFuture;

interface AiToolExecutor {
    AiToolSnapshot snapshot();

    CompletableFuture<String> execute(AiToolCall call);
}
