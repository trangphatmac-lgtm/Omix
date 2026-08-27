package cn.omix.fisproxy;

import cn.omix.security.SafeStorage;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.fisproxy.Client;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

final class FisProxyConfig {
    private static final Pattern CLIENT_ID_PATTERN = Pattern.compile("[A-Za-z0-9._~:-]{1,96}");
    private static final Set<PosixFilePermission> OWNER_ONLY_PERMISSIONS = EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE
    );

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;

    private String apiKey = "";
    private String baseUrl = Client.DEFAULT_BASE_URL;
    private String clientId = newClientId();
    private int timeoutSeconds = 30;

    FisProxyConfig(Path file) {
        this.file = file;
        if (!load()) {
            save();
        }
    }

    synchronized Snapshot snapshot() {
        return new Snapshot(apiKey, baseUrl, clientId, timeoutSeconds);
    }

    synchronized void update(String newApiKey, String newBaseUrl, String newClientId, int newTimeoutSeconds) {
        String validatedApiKey = newApiKey == null ? apiKey : newApiKey.trim();
        String validatedBaseUrl = normalizeBaseUrl(newBaseUrl);
        String validatedClientId = validateClientId(newClientId);
        int validatedTimeout = validateTimeout(newTimeoutSeconds);

        apiKey = validatedApiKey;
        baseUrl = validatedBaseUrl;
        clientId = validatedClientId;
        timeoutSeconds = validatedTimeout;
        save();
    }

    synchronized void setApiKey(String value) {
        apiKey = value == null ? "" : value.trim();
        save();
    }

    synchronized void setBaseUrl(String value) {
        baseUrl = normalizeBaseUrl(value);
        save();
    }

    synchronized void setClientId(String value) {
        clientId = value == null || value.isBlank() ? newClientId() : validateClientId(value);
        save();
    }

    synchronized void setTimeoutSeconds(int value) {
        timeoutSeconds = validateTimeout(value);
        save();
    }

    private boolean load() {
        if (!Files.isRegularFile(file)) {
            return false;
        }

        boolean migrateApiKey = false;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.has("apiKey")) {
                String stored = root.get("apiKey").getAsString().trim();
                apiKey = SafeStorage.decrypt(stored);
                migrateApiKey = !stored.isEmpty()
                        && (!SafeStorage.isEncrypted(stored) || SafeStorage.hasLegacyHeader(stored));
            }
            if (root.has("baseUrl")) {
                baseUrl = normalizeBaseUrl(root.get("baseUrl").getAsString());
            }
            if (root.has("clientId")) {
                clientId = validateClientId(root.get("clientId").getAsString());
            }
            if (root.has("timeoutSeconds")) {
                timeoutSeconds = validateTimeout(root.get("timeoutSeconds").getAsInt());
            }
        } catch (Exception ignored) {
            apiKey = "";
            baseUrl = Client.DEFAULT_BASE_URL;
            clientId = newClientId();
            timeoutSeconds = 30;
            return false;
        }

        if (migrateApiKey) {
            save();
        }
        return true;
    }

    private void save() {
        JsonObject root = new JsonObject();
        root.addProperty("apiKey", SafeStorage.encrypt(apiKey));
        root.addProperty("baseUrl", baseUrl);
        root.addProperty("clientId", clientId);
        root.addProperty("timeoutSeconds", timeoutSeconds);

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
                // POSIX permissions are not available on Windows.
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save FisProxy configuration.", exception);
        }
    }

    static String normalizeBaseUrl(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("FisProxy base URL cannot be empty.");
        }
        String value = input.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }

        final URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid FisProxy base URL.", exception);
        }
        String scheme = uri.getScheme();
        if (scheme == null
                || (!scheme.toLowerCase(Locale.ROOT).equals("http")
                && !scheme.toLowerCase(Locale.ROOT).equals("https"))
                || uri.getHost() == null
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException("FisProxy base URL must be HTTP(S) without a query or fragment.");
        }
        return value;
    }

    static String validateClientId(String input) {
        String value = input == null ? "" : input.trim();
        if (!CLIENT_ID_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Client ID must match [A-Za-z0-9._~:-]{1,96}.");
        }
        return value;
    }

    static int validateTimeout(int value) {
        if (value < 1 || value > 300) {
            throw new IllegalArgumentException("HTTP timeout must be between 1 and 300 seconds.");
        }
        return value;
    }

    private static String newClientId() {
        return "omix-" + UUID.randomUUID().toString().replace("-", "");
    }

    record Snapshot(String apiKey, String baseUrl, String clientId, int timeoutSeconds) {
    }
}
