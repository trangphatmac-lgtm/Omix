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
    private boolean dynamic;
    public ModeValue(String name, String defaultValue, Supplier<Boolean> visible, String... modes) {
        super(name, visible); this.modes.addAll(Arrays.asList(modes)); this.value = defaultValue;
    }
    public ModeValue(String name, String defaultValue, String... modes) { this(name, defaultValue, () -> true, modes); }
    public String[] getModes() {
        if (!dynamic || modes.contains(value)) return modes.toArray(String[]::new);
        var visible = new ArrayList<>(modes); visible.add(value);
        return visible.toArray(String[]::new);
    }
    /** Opt-in for externally discovered choices. A saved exact ID survives offline restoration. */
    public ModeValue dynamic() { dynamic = true; return this; }
    public boolean isDynamic() { return dynamic; }
    public void replaceModes(Collection<String> choices) {
        if (!dynamic) throw new IllegalStateException("Not a dynamic choice");
        if (choices.isEmpty() || choices.stream().anyMatch(item -> item == null || item.isBlank()))
            throw new IllegalArgumentException("Empty dynamic choices");
        modes.clear(); modes.addAll(new LinkedHashSet<>(choices));
    }
    public String getValue() { return value; }
    public boolean is(String mode) { return dynamic ? value.equals(mode) : value.equalsIgnoreCase(mode); }
    public void setValue(String mode) {
        if (mode == null || dynamic && (mode.isBlank() || mode.length() > 256)) return;
        String next = dynamic ? mode : modes.stream().filter(item -> item.equalsIgnoreCase(mode)).findFirst().orElse(null);
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
