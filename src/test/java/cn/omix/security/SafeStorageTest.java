package cn.omix.security;

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

        assertEquals("OllO", encrypted);
        assertEquals(SafeStorage.Mode.NORMAL, SafeStorage.modeOf(encrypted).orElseThrow());
        assertEquals("A", SafeStorage.decrypt(encrypted));
    }

    @Test
    void heavyModeXorsWith8964AndUsesValidPadding() {
        String encrypted = SafeStorage.encrypt("A", SafeStorage.Mode.HEAVY);
        String encoded = encrypted.replaceAll("[1-9A-F]", "");

        assertEquals("O0IO", encoded);
        assertHeavyPaddingIsValid(encrypted);
        assertEquals(SafeStorage.Mode.HEAVY, SafeStorage.modeOf(encrypted).orElseThrow());
        assertEquals("A", SafeStorage.decrypt(encrypted));
    }

    @Test
    void defaultModeIsHeavyAndRoundTripsUnicode() {
        String plaintext = "密钥-Token-🔐";
        String encrypted = SafeStorage.encrypt(plaintext);

        assertFalse(encrypted.startsWith("safe:"));
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
    void oldPrefixedFormatsRemainReadable() {
        assertTrue(SafeStorage.hasLegacyHeader("safe:v1:normal:OllO"));
        assertTrue(SafeStorage.hasLegacyHeader("safe:v1:heavy:O11111011111I11111O"));
        assertEquals("A", SafeStorage.decrypt("safe:v1:normal:OllO"));
        assertEquals(
                "A",
                SafeStorage.decrypt("safe:v1:heavy:O11111011111I11111O")
        );
    }

    @Test
    void controlCharacterFalsePositiveRemainsPlaintext() {
        assertTrue(SafeStorage.modeOf("lOI0").isEmpty());
        assertEquals("lOI0", SafeStorage.decrypt("lOI0"));
    }

    @Test
    void malformedOrUnknownPrefixedValuesAreRejected() {
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
