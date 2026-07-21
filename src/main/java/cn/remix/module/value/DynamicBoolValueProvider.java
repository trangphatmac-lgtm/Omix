package cn.remix.module.value;

import cn.remix.module.value.impl.BoolValue;

/**
 * Creates boolean settings whose names are only known at runtime.
 */
public interface DynamicBoolValueProvider {
    BoolValue getOrCreateBoolValue(String name, boolean initialValue);
}
