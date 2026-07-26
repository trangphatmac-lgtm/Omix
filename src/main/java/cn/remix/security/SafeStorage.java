package cn.remix.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Objects;
import java.util.Optional;


public final class SafeStorage {
    private static final String ROOT_PREFIX = "safe:";
    private static final String PREFIX = "safe:v1:";
    private static final String NORMAL_PREFIX = PREFIX + "normal:";
    private static final String HEAVY_PREFIX = PREFIX + "heavy:";
    private static final char[] TWO_BIT_ALPHABET = {'l', 'O', 'I', '0'};
    private static final char[] HEAVY_PADDING_ALPHABET = "123456789ABCDEF".toCharArray();
    private static final byte[] HEAVY_XOR_KEY = "8964".getBytes(StandardCharsets.UTF_8);
    private static final SecureRandom RANDOM = new SecureRandom();

    private SafeStorage() {
    }

    public enum Mode {
        NORMAL,
        HEAVY
    }


    public static String encrypt(String plaintext) {
        return encrypt(plaintext, Mode.HEAVY);
    }

    public static String encrypt(String plaintext, Mode mode) {
        Objects.requireNonNull(plaintext, "plaintext");
        Objects.requireNonNull(mode, "mode");

        byte[] bytes = plaintext.getBytes(StandardCharsets.UTF_8);
        return switch (mode) {
            case NORMAL -> NORMAL_PREFIX + encodeTwoBit(bytes);
            case HEAVY -> HEAVY_PREFIX + addHeavyPadding(encodeTwoBit(xorHeavyKey(bytes)));
        };
    }


    public static String decrypt(String storedValue) {
        if (storedValue == null) {
            return null;
        }
        if (storedValue.startsWith(NORMAL_PREFIX)) {
            byte[] bytes = decodeTwoBit(storedValue.substring(NORMAL_PREFIX.length()));
            return new String(bytes, StandardCharsets.UTF_8);
        }
        if (storedValue.startsWith(HEAVY_PREFIX)) {
            String encoded = removeAndValidateHeavyPadding(
                    storedValue.substring(HEAVY_PREFIX.length())
            );
            byte[] bytes = xorHeavyKey(decodeTwoBit(encoded));
            return new String(bytes, StandardCharsets.UTF_8);
        }
        if (storedValue.startsWith(ROOT_PREFIX)) {
            throw new IllegalArgumentException("Unsupported Safe Storage format.");
        }
        return storedValue;
    }

    public static boolean isEncrypted(String storedValue) {
        return modeOf(storedValue).isPresent();
    }


    public static Optional<Mode> modeOf(String storedValue) {
        if (storedValue == null) {
            return Optional.empty();
        }
        if (storedValue.startsWith(NORMAL_PREFIX)) {
            return Optional.of(Mode.NORMAL);
        }
        if (storedValue.startsWith(HEAVY_PREFIX)) {
            return Optional.of(Mode.HEAVY);
        }
        if (storedValue.startsWith(ROOT_PREFIX)) {
            throw new IllegalArgumentException("Unsupported Safe Storage format.");
        }
        return Optional.empty();
    }

    private static String encodeTwoBit(byte[] bytes) {
        StringBuilder encoded = new StringBuilder(bytes.length * 4);
        for (byte value : bytes) {
            int unsigned = value & 0xFF;
            encoded.append(TWO_BIT_ALPHABET[(unsigned >>> 6) & 0b11]);
            encoded.append(TWO_BIT_ALPHABET[(unsigned >>> 4) & 0b11]);
            encoded.append(TWO_BIT_ALPHABET[(unsigned >>> 2) & 0b11]);
            encoded.append(TWO_BIT_ALPHABET[unsigned & 0b11]);
        }
        return encoded.toString();
    }

    private static byte[] decodeTwoBit(String encoded) {
        if (encoded.length() % 4 != 0) {
            throw new IllegalArgumentException(
                    "Safe Storage payload length must be divisible by four."
            );
        }

        byte[] decoded = new byte[encoded.length() / 4];
        for (int byteIndex = 0; byteIndex < decoded.length; byteIndex++) {
            int value = 0;
            for (int pair = 0; pair < 4; pair++) {
                value = (value << 2) | decodePair(encoded.charAt(byteIndex * 4 + pair));
            }
            decoded[byteIndex] = (byte) value;
        }
        return decoded;
    }

    private static int decodePair(char value) {
        return switch (value) {
            case 'l' -> 0b00;
            case 'O' -> 0b01;
            case 'I' -> 0b10;
            case '0' -> 0b11;
            default -> throw new IllegalArgumentException(
                    "Invalid character in Safe Storage payload."
            );
        };
    }

    private static byte[] xorHeavyKey(byte[] bytes) {
        byte[] transformed = new byte[bytes.length];
        for (int index = 0; index < bytes.length; index++) {
            transformed[index] = (byte) (bytes[index] ^ HEAVY_XOR_KEY[index % HEAVY_XOR_KEY.length]);
        }
        return transformed;
    }

    private static String addHeavyPadding(String encoded) {
        if (encoded.isEmpty()) {
            return "";
        }

        StringBuilder padded = new StringBuilder(encoded.length() * 10);
        padded.append(encoded.charAt(0));
        for (int index = 1; index < encoded.length(); index++) {
            int paddingLength = RANDOM.nextInt(5, 13);
            for (int paddingIndex = 0; paddingIndex < paddingLength; paddingIndex++) {
                padded.append(HEAVY_PADDING_ALPHABET[
                        RANDOM.nextInt(HEAVY_PADDING_ALPHABET.length)
                ]);
            }
            padded.append(encoded.charAt(index));
        }
        return padded.toString();
    }

    private static String removeAndValidateHeavyPadding(String padded) {
        if (padded.isEmpty()) {
            return "";
        }
        if (!isTwoBitCharacter(padded.charAt(0))) {
            throw new IllegalArgumentException(
                    "Heavy Safe Storage payload must begin with encoded data."
            );
        }

        StringBuilder encoded = new StringBuilder();
        int paddingLength = 0;
        for (int index = 0; index < padded.length(); index++) {
            char current = padded.charAt(index);
            if (isTwoBitCharacter(current)) {
                if (!encoded.isEmpty() && (paddingLength < 5 || paddingLength > 12)) {
                    throw new IllegalArgumentException(
                            "Heavy Safe Storage padding must contain 5 to 12 characters."
                    );
                }
                encoded.append(current);
                paddingLength = 0;
            } else {
                if (!isHeavyPaddingCharacter(current)) {
                    throw new IllegalArgumentException(
                            "Invalid character in heavy Safe Storage padding."
                    );
                }
                paddingLength++;
            }
        }
        if (paddingLength != 0) {
            throw new IllegalArgumentException(
                    "Heavy Safe Storage payload cannot end with padding."
            );
        }
        return encoded.toString();
    }

    private static boolean isTwoBitCharacter(char value) {
        return value == 'l' || value == 'O' || value == 'I' || value == '0';
    }

    private static boolean isHeavyPaddingCharacter(char value) {
        return (value >= '1' && value <= '9') || (value >= 'A' && value <= 'F');
    }
}
