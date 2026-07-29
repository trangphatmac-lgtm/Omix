package im.music;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicSidecarManagerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void replacesLegacyVersionOnlyExtractionWithCurrentBundle() throws Exception {
        Path directory = temporaryDirectory.resolve("sidecar").resolve("0.1.0");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("index.js"), "legacy", StandardCharsets.UTF_8);
        Files.writeString(directory.resolve(".complete"), "0.1.0", StandardCharsets.UTF_8);

        Path extracted = new MusicSidecarManager(temporaryDirectory).ensureExtracted();

        String index = Files.readString(extracted.resolve("index.js"), StandardCharsets.UTF_8);
        String marker = Files.readString(extracted.resolve(".complete"), StandardCharsets.UTF_8);
        assertNotEquals("legacy", index);
        assertTrue(index.contains("'/album/sublist'"));
        assertTrue(index.contains("'/audio/transcode'"));
        assertTrue(marker.matches("0\\.1\\.0\\R[0-9a-f]{64}"));
    }
}
