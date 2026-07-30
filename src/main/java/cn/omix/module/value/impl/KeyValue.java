package cn.omix.module.value.impl;

import cn.omix.module.value.Value;
import lombok.Getter;

import java.util.function.Supplier;

@Getter
public final class KeyValue extends Value {
    private volatile int value;

    public KeyValue(String name, int value, Supplier<Boolean> visible) {
        super(name, visible);
        this.value = value;
    }

    public KeyValue(String name, int value) {
        this(name, value, () -> true);
    }

    public void setValue(int value) {
        this.value = value;
    }
}
