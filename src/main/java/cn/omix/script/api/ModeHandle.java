package cn.omix.script.api;

import cn.omix.module.Module;
import cn.omix.module.value.Value;
import cn.omix.util.script.ModeHost;
import java.util.*;
import java.util.function.Function;

public final class ModeHandle extends FeatureHandle {
    private final Module module;
    private final String name;
    private final List<Value> settings = new ArrayList<>();
    private final Map<ModeHook<?, ?>, Function<?, ?>> hooks = new HashMap<>();
    public ModeHandle(ScriptContext context, String id, Module module, String name) {
        super(context, id); this.module = module; this.name = name;
    }
    public Module module() { return module; }
    public String name() { return name; }
    public List<Value> settings() { return List.copyOf(settings); }
    @Override public <T extends Value> T setting(T value) {
        context.ensurePreparing();
        if (settings.stream().anyMatch(old -> old.getName().equalsIgnoreCase(value.getName())))
            throw new IllegalArgumentException("Duplicate mode setting: " + value.getName());
        settings.add(value); return value;
    }
    public <I, O> ModeHandle hook(ModeHook<I, O> hook, Function<I, O> callback) {
        context.ensurePreparing(); hooks.put(hook, callback); return this;
    }
    public <I, O> O query(ModeHook<I, O> hook, I input, O fallback) {
        if (!active()) return fallback;
        @SuppressWarnings("unchecked") Function<I, O> callback = (Function<I, O>) hooks.get(hook);
        if (callback == null) return fallback;
        try {
            O output = hook.outputType().cast(callback.apply(input));
            if (output instanceof Double value && !Double.isFinite(value)) throw new IllegalArgumentException("Mode hook returned a non-finite number");
            return output == null ? fallback : output;
        }
        catch (Throwable error) { cn.omix.util.script.ScriptFailures.rethrowFatal(error); invoke(hook.name(), () -> { throw new IllegalStateException(error); }); return fallback; }
    }
    @Override public ModeHandle onEnable(Runnable callback) { super.onEnable(callback); return this; }
    @Override public ModeHandle onDisable(Runnable callback) { super.onDisable(callback); return this; }
    @Override protected void stopAfterError() {
        if (context.active()) ModeHost.stop(module);
    }
}
