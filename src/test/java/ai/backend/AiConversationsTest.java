package ai.backend;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class AiConversationsTest {
    @TempDir Path directory;

    @Test
    void migratesBothLegacyHistoriesIncludingToolsExactlyOnce() throws Exception {
        Path file = directory.resolve("ai.json");
        Files.writeString(file, """
                {"model":"test-model","thinking":false,
                 "chatHistory":[{"role":"user","content":"hello"},{"role":"assistant","content":"hi"}],
                 "history":[{"role":"user","content":"task"},
                   {"role":"assistant","content":"","reasoning_content":"thinking",
                    "tool_calls":[{"id":"call-1","type":"function","function":{"name":"test","arguments":"{}"}}]},
                   {"role":"tool","tool_call_id":"call-1","content":"done"},
                   {"role":"assistant","content":"finished"}]}
                """);
        AiConfig config = new AiConfig(file);
        assertEquals(2, config.listConversations().getAsJsonArray("conversations").size());
        assertEquals(2, config.getHistorySize(AiChatMode.CHAT));
        assertEquals(4, config.getHistorySize(AiChatMode.AGENT));
        assertEquals("test-model", config.snapshot().model());
        assertFalse(config.snapshot().thinking());
        String agent = config.conversationId(AiChatMode.AGENT);
        assertEquals("thinking", config.conversation(agent).messages().get(1).reasoningContent());
        assertEquals("call-1", config.conversation(agent).messages().get(2).toolCallId());
        AiConfig reloaded = new AiConfig(file);
        assertEquals(config.listConversations(), reloaded.listConversations());
        assertEquals(config.conversation(agent), reloaded.conversation(agent));
        assertFalse(JsonParser.parseString(Files.readString(file)).getAsJsonObject().has("history"));
    }

    @Test
    void sameModeConversationsStayIndependentAndSelectionPersists() {
        Path file = directory.resolve("ai.json");
        AiConfig config = new AiConfig(file);
        String first = config.createConversation(AiChatMode.CHAT).id();
        String second = config.createConversation(AiChatMode.CHAT).id();
        config.appendTurn(first, "first question", answer("first answer"));
        assertTrue(config.conversation(second).messages().isEmpty());
        assertEquals("first question", config.conversation(first).title());
        config.renameConversation(first, "My conversation");
        config.selectConversation(first);
        AiConfig reloaded = new AiConfig(file);
        assertEquals(first, reloaded.listConversations().get("selectedConversationId").getAsString());
        assertEquals("My conversation", reloaded.conversation(first).title());
        assertEquals(2, reloaded.clearHistory(first));
        assertEquals("My conversation", reloaded.conversation(first).title());
        reloaded.deleteConversation(first);
        assertEquals(second, reloaded.listConversations().get("selectedConversationId").getAsString());
        reloaded.deleteConversation(second);
        assertTrue(new AiConfig(file).listConversations().getAsJsonArray("conversations").isEmpty());
    }

    @Test
    void failedSaveRollsBackMessagesAndSelection() throws Exception {
        Path file = directory.resolve("ai.json");
        AiConfig config = new AiConfig(file);
        String id = config.createConversation(AiChatMode.AGENT).id();
        JsonObject previous = config.listConversations();
        Files.delete(file);
        Files.createDirectory(file);
        Files.writeString(file.resolve("prevent-replacement"), "test");
        assertThrows(IllegalStateException.class, () -> config.appendTurn(id, "lost?", answer("no")));
        assertThrows(IllegalStateException.class, () -> config.createConversation(AiChatMode.CHAT));
        assertThrows(IllegalStateException.class, () -> config.deleteConversation(id));
        assertEquals(previous, config.listConversations());
        assertTrue(config.conversation(id).messages().isEmpty());
    }

    @Test
    void malformedConversationDoesNotDiscardValidEntries() throws Exception {
        Path file = directory.resolve("ai.json");
        AiConfig config = new AiConfig(file);
        String id = config.createConversation(AiChatMode.CHAT).id();
        JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        root.getAsJsonArray("conversations").add(new JsonObject());
        Files.writeString(file, root.toString());
        assertEquals(id, new AiConfig(file).conversation(id).id());
        assertThrows(IllegalArgumentException.class, () -> config.renameConversation(id, "  "));
        assertThrows(IllegalArgumentException.class, () -> config.conversation("missing"));
    }

    @Test
    void streamingPinsHistoryAndCompletionEvenWhenSelectionChangesBeforeCapture() {
        AiConfig config = new AiConfig(directory.resolve("ai.json"));
        String first = config.createConversation(AiChatMode.CHAT).id();
        config.appendTurn(first, "original", answer("original answer"));
        String second = config.createConversation(AiChatMode.CHAT).id();
        CompletableFuture<AiGameContext> capture = new CompletableFuture<>();
        StubProvider provider = new StubProvider();
        try (AiBackend backend = new AiBackend(config, provider, ignored -> capture)) {
            CompletableFuture<String> request = backend.streamChat("test", "next", AiChatMode.CHAT, first, ignored -> {});
            backend.selectConversation(second);
            assertThrows(IllegalStateException.class, () -> backend.deleteConversation(first));
            assertThrows(IllegalStateException.class, () -> backend.clearConversation(first));
            assertTrue(backend.streamChat("test", "busy", AiChatMode.CHAT, second, ignored -> {}).isCompletedExceptionally());
            capture.complete(null);
            assertEquals("original", provider.history.getFirst().content());
            assertEquals(2, provider.history.size());
            provider.response.complete(new AiTurnResult("done", answer("done")));
            assertEquals("done", request.join());
            assertEquals(4, config.conversation(first).messages().size());
            assertTrue(config.conversation(second).messages().isEmpty());
            assertEquals(second, config.listConversations().get("selectedConversationId").getAsString());
            assertFalse(backend.isChatActive());
            assertEquals("", backend.listConversations().get("activeConversationId").getAsString());
        }
    }

    @Test
    void rejectedModeAndFailedRequestReleaseBusyStateWithoutSavingATurn() {
        AiConfig config = new AiConfig(directory.resolve("ai.json"));
        String id = config.createConversation(AiChatMode.CHAT).id();
        StubProvider provider = new StubProvider();
        try (AiBackend backend = new AiBackend(config, provider, ignored -> CompletableFuture.completedFuture(null))) {
            assertTrue(backend.streamChat("test", "wrong mode", AiChatMode.AGENT, id, ignored -> {}).isCompletedExceptionally());
            assertFalse(backend.isChatActive());
            CompletableFuture<String> request = backend.streamChat("test", "fail", AiChatMode.CHAT, id, ignored -> {});
            provider.response.completeExceptionally(new IllegalStateException("network failure"));
            assertTrue(request.isCompletedExceptionally());
            assertFalse(backend.isChatActive());
            assertTrue(config.conversation(id).messages().isEmpty());
        }
    }

    private static List<AiMessage> answer(String content) {
        return List.of(AiMessage.assistant(content, "", List.of()));
    }

    private static final class StubProvider implements AiProvider {
        private List<AiMessage> history;
        private final CompletableFuture<AiTurnResult> response = new CompletableFuture<>();

        public CompletableFuture<List<String>> listModels() {
            return CompletableFuture.completedFuture(List.of());
        }

        public CompletableFuture<AiTurnResult> streamChat(AiGameContext context, String message, AiChatMode mode,
                                                         List<AiMessage> history, AiStreamListener listener) {
            this.history = history;
            return response;
        }
    }
}
