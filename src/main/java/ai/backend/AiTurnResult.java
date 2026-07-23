package ai.backend;

import java.util.List;

record AiTurnResult(String content, List<AiMessage> messages) {
    AiTurnResult {
        content = content == null ? "" : content;
        messages = List.copyOf(messages);
    }
}
