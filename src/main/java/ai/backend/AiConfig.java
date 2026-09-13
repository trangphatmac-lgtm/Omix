package ai.backend;

import cn.omix.security.SafeStorage;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.UUID;
import java.util.Locale;
import java.util.Set;

final class AiConfig {
    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com";
    private static final String DEFAULT_MODEL = "deepseek-v4-flash";
    private static final Set<PosixFilePermission> OWNER_ONLY_PERMISSIONS = EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE
    );

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;

    private String baseUrl = DEFAULT_BASE_URL;
    private String apiKey = "";
    private String model = DEFAULT_MODEL;
    private boolean thinking = true;
    private final LinkedHashMap<String, Conversation> conversations = new LinkedHashMap<>();
    private String selectedConversationId = "";

    AiConfig(Path file) {
        this.file = file;
        load();
    }

    synchronized Snapshot snapshot() {
        return new Snapshot(
                baseUrl,
                apiKey,
                model,
                thinking
        );
    }

    synchronized void setBaseUrl(String baseUrl) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        save();
    }

    synchronized void setApiKey(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        save();
    }

    synchronized void setModel(String model) {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Model cannot be empty.");
        }
        this.model = model.trim();
        save();
    }

    synchronized void setThinking(boolean thinking) {
        this.thinking = thinking;
        save();
    }

    synchronized int getHistorySize(AiChatMode mode) {
        return history(mode).size();
    }

    synchronized JsonArray getSerializedHistory(AiChatMode mode) {
        return serializeHistory(history(mode));
    }

    synchronized JsonObject listConversations() {
        JsonObject result = new JsonObject();
        JsonArray entries = new JsonArray();
        conversations.values().stream()
                .sorted(Comparator.comparingLong(Conversation::updatedAt).reversed())
                .forEach(conversation -> entries.add(conversation.toJson(false)));
        result.add("conversations", entries);
        result.addProperty("selectedConversationId", selectedConversationId);
        return result;
    }

    synchronized Conversation conversation(String id) {
        Conversation conversation = conversations.get(id);
        if (conversation == null) throw new IllegalArgumentException("Conversation not found.");
        return conversation;
    }

    synchronized String conversationId(AiChatMode mode) {
        Conversation selected = selectedForMode(mode);
        return selected == null ? createConversation(mode).id() : selected.id();
    }

    synchronized Conversation createConversation(AiChatMode mode) {
        if (mode == null) throw new IllegalArgumentException("AI mode cannot be null.");
        long now = System.currentTimeMillis();
        Conversation conversation = new Conversation(UUID.randomUUID().toString(), "新对话", mode, now, now, List.of());
        updateConversations(() -> {
            conversations.put(conversation.id(), conversation);
            selectedConversationId = conversation.id();
        });
        return conversation;
    }

    synchronized void selectConversation(String id) {
        conversation(id);
        updateConversations(() -> selectedConversationId = id);
    }

    synchronized void renameConversation(String id, String title) {
        if (title == null || title.isBlank() || title.strip().length() > 100) {
            throw new IllegalArgumentException("Title must contain 1–100 characters.");
        }
        Conversation previous = conversation(id);
        updateConversations(() -> conversations.put(id, new Conversation(id, title.strip(), previous.mode(),
                previous.createdAt(), previous.updatedAt(), previous.messages())));
    }

    synchronized void deleteConversation(String id) {
        conversation(id);
        updateConversations(() -> {
            conversations.remove(id);
            if (id.equals(selectedConversationId)) {
                selectedConversationId = conversations.values().stream()
                        .max(Comparator.comparingLong(Conversation::updatedAt)).map(Conversation::id).orElse("");
            }
        });
    }

    synchronized void appendTurn(String id, String userMessage, List<AiMessage> turnMessages) {
        Conversation previous = conversation(id);
        List<AiMessage> messages = new ArrayList<>(previous.messages());
        messages.add(AiMessage.user(userMessage));
        messages.addAll(turnMessages);
        String title = previous.title();
        if (previous.messages().isEmpty() && title.equals("新对话")) {
            title = userMessage.strip().replaceAll("\\s+", " ");
            if (title.length() > 40) title = title.substring(0, 40) + "…";
        }
        Conversation next = new Conversation(id, title, previous.mode(), previous.createdAt(),
                System.currentTimeMillis(), List.copyOf(messages));
        updateConversations(() -> conversations.put(id, next));
    }

    synchronized int clearHistory(String id) {
        Conversation previous = conversation(id);
        updateConversations(() -> conversations.put(id, new Conversation(id, previous.title(), previous.mode(),
                previous.createdAt(), System.currentTimeMillis(), List.of())));
        return previous.messages().size();
    }

    private void updateConversations(Runnable mutation) {
        var previous = new LinkedHashMap<>(conversations);
        String previousSelection = selectedConversationId;
        try {
            mutation.run();
            save();
        } catch (RuntimeException exception) {
            conversations.clear();
            conversations.putAll(previous);
            selectedConversationId = previousSelection;
            throw exception;
        }
    }

    private void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }

        boolean migrateApiKey = false;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.has("baseUrl")) {
                baseUrl = normalizeBaseUrl(root.get("baseUrl").getAsString());
            }
            if (root.has("apiKey")) {
                String storedApiKey = root.get("apiKey").getAsString().trim();
                apiKey = SafeStorage.decrypt(storedApiKey);
                migrateApiKey = !storedApiKey.isEmpty()
                        && (!SafeStorage.isEncrypted(storedApiKey)
                        || SafeStorage.hasLegacyHeader(storedApiKey));
            }
            if (root.has("model") && !root.get("model").getAsString().isBlank()) {
                model = root.get("model").getAsString().trim();
            }
            if (root.has("thinking") && root.get("thinking").isJsonPrimitive()) {
                thinking = root.get("thinking").getAsBoolean();
            }
            if (root.has("conversations") && root.get("conversations").isJsonArray()) {
                for (JsonElement element : root.getAsJsonArray("conversations")) {
                    try {
                        JsonObject entry = element.getAsJsonObject();
                        List<AiMessage> messages = new ArrayList<>();
                        loadHistory(entry.getAsJsonArray("messages"), messages);
                        Conversation conversation = new Conversation(entry.get("id").getAsString(),
                                entry.get("title").getAsString(), AiChatMode.fromName(entry.get("mode").getAsString()),
                                entry.get("createdAt").getAsLong(), entry.get("updatedAt").getAsLong(), List.copyOf(messages));
                        if (!conversation.id().isBlank()) conversations.putIfAbsent(conversation.id(), conversation);
                    } catch (RuntimeException ignored) {
                        // A damaged conversation must not discard other conversations or settings.
                    }
                }
                if (root.has("selectedConversationId")) {
                    selectedConversationId = root.get("selectedConversationId").getAsString();
                }
            } else {
                migrateHistory(root, "chatHistory", AiChatMode.CHAT);
                migrateHistory(root, "history", AiChatMode.AGENT);
                migrateApiKey = true;
            }
            if (!conversations.containsKey(selectedConversationId)) {
                selectedConversationId = conversations.keySet().stream().findFirst().orElse("");
            }
        } catch (Exception ignored) {
            baseUrl = DEFAULT_BASE_URL;
            apiKey = "";
            model = DEFAULT_MODEL;
            thinking = true;
            conversations.clear();
            selectedConversationId = "";
            return;
        }

        if (migrateApiKey) {
            try {
                save();
            } catch (RuntimeException ignored) {
                // Keep the successfully loaded plaintext key in memory and retry
                // migration the next time configuration is saved.
            }
        }
    }

    private void save() {
        JsonObject root = new JsonObject();
        root.addProperty("baseUrl", baseUrl);
        root.addProperty("apiKey", SafeStorage.encrypt(apiKey));
        root.addProperty("model", model);
        root.addProperty("thinking", thinking);
        root.addProperty("conversationVersion", 1);
        root.addProperty("selectedConversationId", selectedConversationId);
        JsonArray entries = new JsonArray();
        conversations.values().forEach(conversation -> entries.add(conversation.toJson(true)));
        root.add("conversations", entries);

        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), "ai-config-", ".tmp");
            try {
                try {
                    Files.setPosixFilePermissions(temporary, OWNER_ONLY_PERMISSIONS);
                } catch (UnsupportedOperationException ignored) {
                    // POSIX permissions are not available on every supported platform.
                }
                Files.writeString(temporary, gson.toJson(root), StandardCharsets.UTF_8,
                        StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                try {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save AI configuration.", exception);
        }
    }

    private List<AiMessage> history(AiChatMode mode) {
        Conversation selected = selectedForMode(mode);
        return selected == null ? List.of() : selected.messages();
    }

    private Conversation selectedForMode(AiChatMode mode) {
        Conversation selected = conversations.get(selectedConversationId);
        if (selected != null && selected.mode() == mode) return selected;
        return conversations.values().stream().filter(conversation -> conversation.mode() == mode)
                .max(Comparator.comparingLong(Conversation::updatedAt)).orElse(null);
    }

    private void migrateHistory(JsonObject root, String key, AiChatMode mode) {
        if (!root.has(key) || !root.get(key).isJsonArray()) return;
        List<AiMessage> messages = new ArrayList<>();
        loadHistory(root.getAsJsonArray(key), messages);
        if (messages.isEmpty()) return;
        long now = System.currentTimeMillis();
        String id = UUID.randomUUID().toString();
        conversations.put(id, new Conversation(id, mode == AiChatMode.CHAT ? "Chat 历史对话" : "Agent 历史对话",
                mode, now, now, List.copyOf(messages)));
    }

    record Conversation(String id, String title, AiChatMode mode, long createdAt, long updatedAt,
                        List<AiMessage> messages) {
        JsonObject toJson(boolean includeMessages) {
            JsonObject result = new JsonObject();
            result.addProperty("id", id);
            result.addProperty("title", title);
            result.addProperty("mode", mode.routeName());
            result.addProperty("createdAt", createdAt);
            result.addProperty("updatedAt", updatedAt);
            result.addProperty("messageCount", messages.size());
            if (includeMessages) result.add("messages", serializeHistory(messages));
            return result;
        }
    }

    private static void loadHistory(JsonArray source, List<AiMessage> target) {
        if (source == null) return;
        for (JsonElement element : source) {
            if (!element.isJsonObject()) continue;
            try {
                target.add(AiMessage.fromJson(element.getAsJsonObject()));
            } catch (RuntimeException ignored) {
                // Ignore malformed history entries without discarding valid configuration.
            }
        }
    }

    private static JsonArray serializeHistory(List<AiMessage> history) {
        JsonArray messages = new JsonArray();
        for (AiMessage message : history) {
            messages.add(message.toJson());
        }
        return messages;
    }

    private static String normalizeBaseUrl(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Base URL cannot be empty.");
        }

        String value = input.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }

        final URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid base URL.", exception);
        }

        String scheme = uri.getScheme();
        if (scheme == null
                || (!scheme.toLowerCase(Locale.ROOT).equals("http")
                && !scheme.toLowerCase(Locale.ROOT).equals("https"))
                || uri.getHost() == null
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException("Base URL must be an HTTP(S) URL without a query or fragment.");
        }
        return value;
    }

    record Snapshot(
            String baseUrl,
            String apiKey,
            String model,
            boolean thinking
    ) {}
}
