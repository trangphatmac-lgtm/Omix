package cn.omix.config;

import cn.omix.security.SafeStorage;

import java.util.Arrays;

public enum ConfigStorageMode {
    NONE(0),
    NORMAL(1),
    HEAVY(2);

    private final int code;

    ConfigStorageMode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public String displayName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public String encode(String plaintext) {
        return switch (this) {
            case NONE -> plaintext;
            case NORMAL -> SafeStorage.encrypt(plaintext, SafeStorage.Mode.NORMAL);
            case HEAVY -> SafeStorage.encrypt(plaintext, SafeStorage.Mode.HEAVY);
        };
    }

    public String decode(String storedValue) {
        return this == NONE ? storedValue : SafeStorage.decrypt(storedValue);
    }

    public static ConfigStorageMode detect(String storedValue) {
        return SafeStorage.modeOf(storedValue)
                .map(mode -> mode == SafeStorage.Mode.NORMAL ? NORMAL : HEAVY)
                .orElse(NONE);
    }

    public static ConfigStorageMode fromCode(int code) {
        return Arrays.stream(values())
                .filter(mode -> mode.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Config encryption mode must be 0, 1, or 2."
                ));
    }
}
