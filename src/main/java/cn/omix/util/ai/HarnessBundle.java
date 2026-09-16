package cn.omix.util.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.zip.ZipInputStream;

/** Extract only the current platform from the immutable, checked JAR resources. */
final class HarnessBundle {
    private static final String RESOURCE = "/assets/omix/ai/harness/";

    static synchronized Path prepare(Path root, String platform) throws Exception {
        JsonObject manifest;
        try (var input = resource("manifest.json")) {
            manifest = JsonParser.parseReader(new InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
        JsonObject hashes = manifest.getAsJsonObject("archives");
        if (!hashes.has(platform + ".zip")) throw new IOException("Harness does not support " + platform);
        String identity = hashes.get("common.zip").getAsString() + hashes.get(platform + ".zip").getAsString();
        Path target = root.resolve(manifest.get("version").getAsString() + "-" + platform + "-" + identity.substring(0, 12));
        Path marker = target.resolve(".complete");
        if (Files.isRegularFile(marker) && Files.readString(marker).equals(identity)
                && Files.isRegularFile(target.resolve("launch.mjs"))) return target;
        Files.createDirectories(root);
        Path temporary = root.resolve(".extract-" + UUID.randomUUID());
        Files.createDirectories(temporary);
        try {
            for (String name : new String[]{"common", platform}) {
                Path archive = temporary.resolve(name + ".zip");
                try (var source = resource(name + ".zip")) { Files.copy(source, archive); }
                if (!sha256(archive).equals(hashes.get(name + ".zip").getAsString())) throw new IOException("Harness archive checksum mismatch: " + name);
                extract(archive, temporary);
                Files.delete(archive);
                try (var reader = Files.newBufferedReader(temporary.resolve("executables-" + name + ".json"))) {
                    for (var item : JsonParser.parseReader(reader).getAsJsonArray()) {
                        Path file = safePath(temporary, item.getAsString());
                        if (!platform.startsWith("windows") && !file.toFile().setExecutable(true, true))
                            throw new IOException("Cannot make Harness helper executable: " + file.getFileName());
                    }
                }
            }
            Files.writeString(temporary.resolve(".complete"), identity);
            if (Files.exists(target)) deleteTree(target);
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, target); }
            return target;
        } finally { deleteTree(temporary); }
    }

    static Path safePath(Path root, String name) throws IOException {
        Path target = root.resolve(name.replace('\\', '/')).normalize();
        if (!target.startsWith(root) || name.contains(":")) throw new IOException("Unsafe Harness archive path");
        return target;
    }
    private static void extract(Path archive, Path root) throws IOException {
        try (var zip = new ZipInputStream(new BufferedInputStream(Files.newInputStream(archive)))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path target = safePath(root, entry.getName());
                if (entry.isDirectory()) Files.createDirectories(target);
                else { Files.createDirectories(target.getParent()); Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING); }
            }
        }
    }
    private static InputStream resource(String name) throws IOException {
        InputStream input = HarnessBundle.class.getResourceAsStream(RESOURCE + name);
        if (input == null) throw new IOException("Missing bundled Harness resource: " + name);
        return input;
    }
    private static String sha256(Path path) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = new byte[65536];
            for (int read; (read = input.read(buffer)) >= 0;) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path file : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
        }
    }
}
