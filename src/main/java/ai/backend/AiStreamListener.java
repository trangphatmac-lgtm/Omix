package ai.backend;

@FunctionalInterface
public interface AiStreamListener {
    void onDelta(String content);

    default void onReasoning(String content) {
    }

    default void onToolCall(String id, String name, String arguments) {
    }

    default void onToolResult(String id, String content) {
    }
}
