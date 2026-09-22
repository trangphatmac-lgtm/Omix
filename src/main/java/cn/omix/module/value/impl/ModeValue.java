package cn.omix.module.value.impl;

import cn.omix.module.value.Value;
import cn.omix.script.api.Registration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.*;

public final class ModeValue extends Value {
    private final List<String> modes = new CopyOnWriteArrayList<>();
    private final List<BiConsumer<String, String>> listeners = new CopyOnWriteArrayList<>();
    private volatile String value;
    public ModeValue(String name, String defaultValue, Supplier<Boolean> visible, String... modes) {
        super(name, visible); this.modes.addAll(Arrays.asList(modes)); this.value = defaultValue;
    }
    public ModeValue(String name, String defaultValue, String... modes) { this(name, defaultValue, () -> true, modes); }
    public String[] getModes() { return modes.toArray(String[]::new); }
    public String getValue() { return value; }
    public boolean is(String mode) { return value.equalsIgnoreCase(mode); }
    public void setValue(String mode) {
        String next = modes.stream().filter(item -> item.equalsIgnoreCase(mode)).findFirst().orElse(null);
        if (next == null || next.equals(value)) return;
        String old = value; value = next;
        try { for (var listener : listeners) listener.accept(old, next); }
        catch (RuntimeException error) { value = old; throw error; }
    }
    public Registration onChange(BiConsumer<String, String> listener) {
        listeners.add(listener); return () -> listeners.remove(listener);
    }
    public Registration addMode(String mode) {
        if (mode == null || mode.isBlank() || modes.stream().anyMatch(mode::equalsIgnoreCase))
            throw new IllegalArgumentException("Mode already exists or is blank: " + mode);
        modes.add(mode);
        return () -> { if (is(mode)) setValue(modes.getFirst()); modes.remove(mode); };
    }
}
