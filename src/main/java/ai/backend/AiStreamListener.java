package ai.backend;

@FunctionalInterface
public interface AiStreamListener {
    void onDelta(String content);
}
