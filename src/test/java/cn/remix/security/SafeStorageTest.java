package cn.remix.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeStorageTest {
    @Test
    void normalModeUsesSpecifiedTwoBitAlphabet() {
        String encrypted = SafeStorage.encrypt("A", SafeStorage.Mode.NORMAL);

        assertEquals("safe:v1:normal:OllO", encrypted);
        assertEquals("A", SafeStorage.decrypt(encrypted));
    }

    @Test
    void heavyModeXorsWith8964AndUsesValidPadding() {
        String encrypted = SafeStorage.encrypt("A", SafeStorage.Mode.HEAVY);
        String payload = encrypted.substring("safe:v1:heavy:".length());
        String encoded = payload.replaceAll("[1-9A-F]", "");

        assertEquals("O0IO", encoded);
        assertHeavyPaddingIsValid(payload);
        assertEquals("A", SafeStorage.decrypt(encrypted));
    }

    @Test
    void defaultModeIsHeavyAndRoundTripsUnicode() {
        String plaintext = "密钥-Token-🔐";
        String encrypted = SafeStorage.encrypt(plaintext);

        assertTrue(encrypted.startsWith("safe:v1:heavy:"));
        assertEquals(plaintext, SafeStorage.decrypt(encrypted));
    }

    @Test
    void emptyValuesRoundTripInBothModes() {
        for (SafeStorage.Mode mode : SafeStorage.Mode.values()) {
            assertEquals("", SafeStorage.decrypt(SafeStorage.encrypt("", mode)));
        }
    }

    @Test
    void legacyPlaintextRemainsReadable() {
        assertFalse(SafeStorage.isEncrypted("legacy-api-key"));
        assertTrue(SafeStorage.modeOf("legacy-api-key").isEmpty());
        assertEquals("legacy-api-key", SafeStorage.decrypt("legacy-api-key"));
        assertNull(SafeStorage.decrypt(null));
    }

    @Test
    void malformedOrUnknownSafeStorageValuesAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SafeStorage.decrypt("safe:v1:normal:l")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> SafeStorage.decrypt("safe:v1:heavy:O1I")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> SafeStorage.decrypt("safe:v2:heavy:anything")
        );
    }

    private static void assertHeavyPaddingIsValid(String payload) {
        String[] padding = payload.split("[lOI0]", -1);
        assertEquals("", padding[0]);
        assertEquals("", padding[padding.length - 1]);
        for (int index = 1; index < padding.length - 1; index++) {
            assertTrue(padding[index].matches("[1-9A-F]{5,12}"));
        }
    }
}
