package im.webui.interop;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Map;
import java.util.Set;

public final class PersistentLocalStorage {
    private final Gson gson = new Gson();
    private final Path file;
    private final JsonObject values = new JsonObject();

    public PersistentLocalStorage(Path file) {
        this.file = file;
    }

    public synchronized void load() throws IOException {
        values.entrySet().clear();
        if (!Files.isRegularFile(file)) {
            return;
        }
        setOwnerOnlyPermissions(file);
        JsonObject loaded = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class);
        if (loaded != null) {
            for (Map.Entry<String, JsonElement> entry : loaded.entrySet()) {
                values.add(entry.getKey(), entry.getValue().deepCopy());
            }
        }
    }

    public synchronized JsonObject all() {
        return values.deepCopy();
    }

    public synchronized JsonElement get(String key) {
        JsonElement value = values.get(key);
        return value == null ? null : value.deepCopy();
    }

    public synchronized void put(String key, JsonElement value) throws IOException {
        values.add(key, value == null ? com.google.gson.JsonNull.INSTANCE : value.deepCopy());
        save();
    }

    public synchronized void replace(JsonObject replacement) throws IOException {
        values.entrySet().clear();
        for (Map.Entry<String, JsonElement> entry : replacement.entrySet()) {
            values.add(entry.getKey(), entry.getValue().deepCopy());
        }
        save();
    }

    public synchronized boolean delete(String key) throws IOException {
        boolean removed = values.remove(key) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    private void save() throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, gson.toJson(values), StandardCharsets.UTF_8);
        setOwnerOnlyPermissions(temporary);
        try {
            Files.move(
                    temporary,
                    file,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
        setOwnerOnlyPermissions(file);
    }

    private static void setOwnerOnlyPermissions(Path target) throws IOException {
        try {
            Files.setPosixFilePermissions(target, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE
            ));
        } catch (UnsupportedOperationException ignored) {
            // Windows ACL inheritance is used when POSIX permissions are unavailable.
        }
    }
}
