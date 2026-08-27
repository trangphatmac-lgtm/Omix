package cn.omix.fisproxy;

import org.fisproxy.Client;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FisProxyConfigTest {
    @TempDir
    Path tempDir;

    @Test
    void persistsSettingsWithoutWritingPlaintextApiKey() throws Exception {
        Path file = tempDir.resolve("fisproxy.json");
        FisProxyConfig config = new FisProxyConfig(file);
        String initialClientId = config.snapshot().clientId();

        config.update("fp_super_secret", "https://api.example.test/", "omix-test:client", 45);

        String stored = Files.readString(file, StandardCharsets.UTF_8);
        assertFalse(stored.contains("fp_super_secret"));
        assertTrue(stored.contains("https://api.example.test"));

        FisProxyConfig.Snapshot reloaded = new FisProxyConfig(file).snapshot();
        assertEquals("fp_super_secret", reloaded.apiKey());
        assertEquals("https://api.example.test", reloaded.baseUrl());
        assertEquals("omix-test:client", reloaded.clientId());
        assertEquals(45, reloaded.timeoutSeconds());
        assertNotEquals(initialClientId, reloaded.clientId());
    }

    @Test
    void createsStableDefaultsAndValidatesSdkSettings() {
        Path file = tempDir.resolve("defaults.json");
        FisProxyConfig first = new FisProxyConfig(file);
        FisProxyConfig.Snapshot snapshot = first.snapshot();

        assertEquals(Client.DEFAULT_BASE_URL, snapshot.baseUrl());
        assertTrue(snapshot.clientId().startsWith("omix-"));
        assertEquals(snapshot.clientId(), new FisProxyConfig(file).snapshot().clientId());

        assertThrows(IllegalArgumentException.class, () -> first.setBaseUrl("file:///tmp/api"));
        assertThrows(IllegalArgumentException.class, () -> first.setClientId("contains spaces"));
        assertThrows(IllegalArgumentException.class, () -> first.setTimeoutSeconds(0));
        assertThrows(IllegalArgumentException.class, () -> first.setTimeoutSeconds(301));
    }
}
