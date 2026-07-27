package cn.omix.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigStorageModeTest {
    private static final String CONFIG_JSON = """
            {
              "Speed": {
                "enabled": true
              }
            }
            """;

    @Test
    void numericCodesSelectExpectedModes() {
        assertEquals(ConfigStorageMode.NONE, ConfigStorageMode.fromCode(0));
        assertEquals(ConfigStorageMode.NORMAL, ConfigStorageMode.fromCode(1));
        assertEquals(ConfigStorageMode.HEAVY, ConfigStorageMode.fromCode(2));
        assertThrows(IllegalArgumentException.class, () -> ConfigStorageMode.fromCode(3));
    }

    @Test
    void noneRemainsPlainJsonAndIsDetected() {
        String stored = ConfigStorageMode.NONE.encode(CONFIG_JSON);

        assertEquals(CONFIG_JSON, stored);
        assertEquals(ConfigStorageMode.NONE, ConfigStorageMode.detect(stored));
        assertEquals(CONFIG_JSON, ConfigStorageMode.NONE.decode(stored));
    }

    @Test
    void normalConfigIsDetectedAndRoundTrips() {
        String stored = ConfigStorageMode.NORMAL.encode(CONFIG_JSON);

        assertTrue(stored.matches("[lOI0]+"));
        assertEquals(ConfigStorageMode.NORMAL, ConfigStorageMode.detect(stored));
        assertEquals(CONFIG_JSON, ConfigStorageMode.NORMAL.decode(stored));
    }

    @Test
    void heavyConfigIsDetectedAndRoundTrips() {
        String stored = ConfigStorageMode.HEAVY.encode(CONFIG_JSON);

        assertFalse(stored.startsWith("safe:"));
        assertTrue(stored.matches("[lOI01-9A-F]+"));
        assertEquals(ConfigStorageMode.HEAVY, ConfigStorageMode.detect(stored));
        assertEquals(CONFIG_JSON, ConfigStorageMode.HEAVY.decode(stored));
    }

    @Test
    void legacyPlainJsonAlwaysLoadsAsNone() {
        assertEquals(ConfigStorageMode.NONE, ConfigStorageMode.detect("{\"legacy\":true}"));
    }
}
