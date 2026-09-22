package cn.omix.script.api;

import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.Value;
import cn.omix.util.script.ScriptModule;

public class ModuleHandle extends FeatureHandle {
    protected Module module;
    public ModuleHandle(ScriptContext context, String id, String name, Category category) {
        super(context, id); module = new ScriptModule(this.id, name, category, this);
    }
    protected ModuleHandle(ScriptContext context, String id) { super(context, id); }
    public Module nativeModule() { return module; }
    public boolean enabled() { return module.isEnabled(); }
    public void enabled(boolean enabled) { context.requireActive(); module.setEnabled(enabled); }
    public void suffix(String suffix) { module.setSuffix(suffix); }
    @Override public <T extends Value> T setting(T value) {
        context.ensurePreparing();
        if (module.getValues().stream().anyMatch(old -> old.getName().equalsIgnoreCase(value.getName())))
            throw new IllegalArgumentException("Duplicate setting: " + value.getName());
        module.getValues().add(value); return value;
    }
    @Override public ModuleHandle onEnable(Runnable callback) { super.onEnable(callback); return this; }
    @Override public ModuleHandle onDisable(Runnable callback) { super.onDisable(callback); return this; }
    @Override protected void stopAfterError() { if (context.active()) module.setEnabled(false); }
}
