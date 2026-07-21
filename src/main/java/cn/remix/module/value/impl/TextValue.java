package cn.remix.module.value.impl;

import cn.remix.module.value.Value;
import lombok.Getter;

import java.util.function.Supplier;

@Getter
public final class TextValue extends Value {
    private String value;

    public TextValue(String name, String value, Supplier<Boolean> visible) {
        super(name, visible);
        this.value = value == null ? "" : value;
    }

    public TextValue(String name, String value) {
        this(name, value, () -> true);
    }

    public void setValue(String value) {
        this.value = value == null ? "" : value;
    }
}
