package cn.omix.util.script;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.io.IOException;

/** Versioned source storage, shared by the UI, tools and commands. */
public final class ScriptFiles {
    public record Source(String id, String text, String hash) {}
    public static final int MAX_BYTES = 512 * 1024;
    private final Path root;
    public ScriptFiles(Path root) throws IOException { Files.createDirectories(root); this.root = root.toRealPath(); }
    public Path root() { return root; }
    public static String id(String input) {
        String value = input != null && input.endsWith(".java") ? input.substring(0, input.length() - 5) : input;
        if (value == null || !value.matches("[A-Za-z][A-Za-z0-9_-]{0,79}"))
            throw new IllegalArgumentException("Script id must start with a letter and contain only letters, digits, _ or - (max 80).");
        return value;
    }
    public Path path(String id) throws IOException {
        Path path = root.resolve(id(id) + ".java");
        if (Files.isSymbolicLink(path)) throw new IOException("Symbolic links are not script sources.");
        return path;
    }
    public synchronized Source read(String id) throws IOException {
        Path file = path(id);
        if (Files.size(file) > MAX_BYTES) throw new IOException("Script source exceeds 512 KiB.");
        String text = Files.readString(file, StandardCharsets.UTF_8);
        return new Source(id(id), text, hash(text));
    }
    public synchronized Source write(String id, String text, String expectedHash) throws IOException {
        if (text == null || text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new IllegalArgumentException("Source exceeds 512 KiB.");
        Path target = path(id);
        String current = Files.exists(target) ? read(id).hash() : "";
        if (!Objects.equals(current, expectedHash)) throw new IllegalStateException("Source changed; read it again before saving.");
        atomicWrite(target, text);
        return new Source(id(id), text, hash(text));
    }
    public synchronized void delete(String id, String expectedHash) throws IOException {
        if (!read(id).hash().equals(expectedHash)) throw new IllegalStateException("Source changed; read it again before deleting.");
        Files.delete(path(id));
    }
    public List<String> list() throws IOException {
        try (var files = Files.list(root)) {
            return files.filter(file -> Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
                    .map(file -> file.getFileName().toString()).filter(name -> name.matches("[A-Za-z][A-Za-z0-9_-]{0,79}\\.java"))
                    .map(ScriptFiles::id).sorted(String.CASE_INSENSITIVE_ORDER).toList();
        }
    }
    public static void atomicWrite(Path target, String text) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".write-", ".tmp");
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
    public static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
