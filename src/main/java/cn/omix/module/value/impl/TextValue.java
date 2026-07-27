package cn.omix.module.value.impl;

import cn.omix.module.value.Value;
import lombok.Getter;

import java.util.function.Supplier;

@Getter
public final class TextValue extends Value {
    private String value;
    private final boolean sensitive;

    public TextValue(String name, String value, Supplier<Boolean> visible) {
        this(name, value, visible, false);
    }

    public TextValue(String name, String value, Supplier<Boolean> visible, boolean sensitive) {
        super(name, visible);
        this.value = value == null ? "" : value;
        this.sensitive = sensitive;
    }

    public TextValue(String name, String value) {
        this(name, value, () -> true);
    }

    public void setValue(String value) {
        this.value = value == null ? "" : value;
    }

    public boolean isSensitive() {
        return sensitive;
    }
}
