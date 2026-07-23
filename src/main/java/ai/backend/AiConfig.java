package ai.backend;

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
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
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
    private final List<AiMessage> history = new ArrayList<>();

    AiConfig(Path file) {
        this.file = file;
        load();
    }

    synchronized Snapshot snapshot() {
        return new Snapshot(baseUrl, apiKey, model, thinking, List.copyOf(history));
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

    synchronized int getHistorySize() {
        return history.size();
    }

    synchronized void appendExchange(String userMessage, String assistantMessage) {
        int previousSize = history.size();
        history.add(new AiMessage("user", userMessage));
        history.add(new AiMessage("assistant", assistantMessage));
        try {
            save();
        } catch (RuntimeException exception) {
            history.subList(previousSize, history.size()).clear();
            throw exception;
        }
    }

    synchronized int clearHistory() {
        List<AiMessage> previous = List.copyOf(history);
        history.clear();
        try {
            save();
            return previous.size();
        } catch (RuntimeException exception) {
            history.addAll(previous);
            throw exception;
        }
    }

    private void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }

        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.has("baseUrl")) {
                baseUrl = normalizeBaseUrl(root.get("baseUrl").getAsString());
            }
            if (root.has("apiKey")) {
                apiKey = root.get("apiKey").getAsString().trim();
            }
            if (root.has("model") && !root.get("model").getAsString().isBlank()) {
                model = root.get("model").getAsString().trim();
            }
            if (root.has("thinking") && root.get("thinking").isJsonPrimitive()) {
                thinking = root.get("thinking").getAsBoolean();
            }
            if (root.has("history") && root.get("history").isJsonArray()) {
                for (JsonElement element : root.getAsJsonArray("history")) {
                    if (!element.isJsonObject()) continue;
                    JsonObject message = element.getAsJsonObject();
                    if (!message.has("role") || !message.has("content")
                            || message.get("role").isJsonNull() || message.get("content").isJsonNull()) {
                        continue;
                    }
                    try {
                        history.add(new AiMessage(
                                message.get("role").getAsString(),
                                message.get("content").getAsString()
                        ));
                    } catch (IllegalArgumentException ignored) {
                        // Ignore malformed history entries without discarding valid configuration.
                    }
                }
            }
        } catch (Exception ignored) {
            baseUrl = DEFAULT_BASE_URL;
            apiKey = "";
            model = DEFAULT_MODEL;
            thinking = true;
            history.clear();
        }
    }

    private void save() {
        JsonObject root = new JsonObject();
        root.addProperty("baseUrl", baseUrl);
        root.addProperty("apiKey", apiKey);
        root.addProperty("model", model);
        root.addProperty("thinking", thinking);
        JsonArray messages = new JsonArray();
        for (AiMessage message : history) {
            JsonObject entry = new JsonObject();
            entry.addProperty("role", message.role());
            entry.addProperty("content", message.content());
            messages.add(entry);
        }
        root.add("history", messages);

        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(
                    file,
                    gson.toJson(root),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
            try {
                Files.setPosixFilePermissions(file, OWNER_ONLY_PERMISSIONS);
            } catch (UnsupportedOperationException ignored) {
                // POSIX permissions are not available on every supported platform.
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save AI configuration.", exception);
        }
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

    record Snapshot(String baseUrl, String apiKey, String model, boolean thinking, List<AiMessage> history) {
    }
}
