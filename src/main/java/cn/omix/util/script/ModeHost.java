package cn.omix.util.script;

import cn.omix.module.Module;
import cn.omix.module.value.Value;
import cn.omix.module.value.impl.ModeValue;
import cn.omix.script.api.*;
import java.util.*;

/** Main-thread mode registry. A synthetic Behavior setting exists only while extensions are installed. */
public final class ModeHost {
    private static final Map<Module, ModeHost> HOSTS = new IdentityHashMap<>();
    private final Module module;
    private final ModeValue selector;
    private final boolean synthetic;
    private final Map<String, ModeHandle> modes = new LinkedHashMap<>();
    private final Registration change;
    private String builtin;
    private ModeHost(Module module) {
        this.module = module;
        String label = selectorName(module);
        ModeValue existing = module.getValues().stream().filter(value -> value instanceof ModeValue && value.getName().equals(label))
                .map(value -> (ModeValue) value).findFirst().orElse(null);
        synthetic = existing == null;
        selector = synthetic ? new ModeValue("Behavior", "Built-in", "Built-in") : existing;
        builtin = selector.getValue();
        if (synthetic) module.getValues().addFirst(selector);
        change = selector.onChange((old, next) -> {
            if (!modes.containsKey(old)) builtin = old;
            module.setScriptMode(modes.get(next));
        });
    }
    public static String selectorName(Module module) {
        return switch (module.getId()) {
            case "AutoTool", "ChestESP" -> "Implementation";
            case "Chams" -> "Render Mode";
            default -> module.getValues().stream().anyMatch(value -> value instanceof ModeValue && value.getName().equals("Mode")) ? "Mode" : "Behavior";
        };
    }
    public static void validate(ModeHandle mode, Set<ModeHandle> replacing) {
        if (mode.module().getId().startsWith("script:")) throw new IllegalArgumentException("Mode hosts must be built-in modules from mode-hosts.json");
        ModeHost host = HOSTS.get(mode.module());
        Set<Value> removed = Collections.newSetFromMap(new IdentityHashMap<>());
        replacing.stream().filter(old -> old.module() == mode.module()).forEach(old -> removed.addAll(old.settings()));
        for (Value value : mode.settings()) {
            if (mode.module().getValues().stream().anyMatch(old -> !removed.contains(old) && old.getName().equalsIgnoreCase(value.getName()))
                    || value.getName().equalsIgnoreCase(selectorName(mode.module())))
                throw new IllegalArgumentException("Mode setting collides with host setting: " + value.getName());
        }
        if (mode.name().equalsIgnoreCase("Built-in") || mode.name().isBlank()) throw new IllegalArgumentException("Blank mode name");
        if (host != null) {
            ModeHandle old = host.modes.get(mode.name());
            if (old != null && replacing.contains(old)) return;
            if (Arrays.stream(host.selector.getModes()).anyMatch(mode.name()::equalsIgnoreCase)) throw new IllegalArgumentException("Mode exists: " + mode.name());
        } else for (Value value : mode.module().getValues()) {
            if (value instanceof ModeValue choice && value.getName().equals(selectorName(mode.module()))
                    && Arrays.stream(choice.getModes()).anyMatch(mode.name()::equalsIgnoreCase)) throw new IllegalArgumentException("Mode exists: " + mode.name());
        }
    }
    public static Registration install(ModeHandle mode) {
        validate(mode, Set.of());
        ModeHost host = HOSTS.computeIfAbsent(mode.module(), ModeHost::new);
        Registration option = host.selector.addMode(mode.name()); host.modes.put(mode.name(), mode);
        // Values retain their native types for both ClickGUIs; visibility is provided by the Value base class.
        for (Value value : mode.settings()) {
            value.setScriptVisibility(() -> mode.module().getScriptMode() == mode);

        }
        host.module.getValues().addAll(mode.settings());
        return new Registration() {
            private boolean closed;
            public void close() {
                if (closed) return; closed = true;
                if (host.module.getScriptMode() == mode) {
                    stop(host.module);
                    host.selector.setValue(host.builtin);
                }
                host.modes.remove(mode.name()); option.close();
                host.module.getValues().removeAll(mode.settings());
                if (host.modes.isEmpty()) {
                    host.change.close(); if (host.synthetic) host.module.getValues().remove(host.selector);
                    HOSTS.remove(host.module);
                }
            }
        };
    }
    public static void stop(Module module) {
        if (Set.of("PathFinder", "Targets").contains(module.getId())) {
            ModeHost host = HOSTS.get(module);
            if (host != null) host.selector.setValue(host.builtin);
        } else module.setEnabled(false);
    }
    public static String selected(Module module) { ModeHost host = HOSTS.get(module); return host == null ? null : host.selector.getValue(); }
    public static void select(Module module, String mode) { ModeHost host = HOSTS.get(module); if (host != null && mode != null) host.selector.setValue(mode); }
    public static <I, O> O query(Module module, ModeHook<I, O> hook, I input, O fallback) {
        return module != null && module.isEnabled() && module.getScriptMode() != null ? module.getScriptMode().query(hook, input, fallback) : fallback;
    }
}
