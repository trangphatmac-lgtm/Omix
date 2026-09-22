package cn.omix.util.script;

import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.script.api.ModuleHandle;

/** Native module entry used by a script handle; behavior subscriptions belong to the handle. */
public final class ScriptModule extends Module {
    private final ModuleHandle handle;
    public ScriptModule(String id, String name, Category category, ModuleHandle handle) {
        super(name, category); setId(id); this.handle = handle;
    }
    @Override public void onEnable() { handle.activate(); }
    @Override public void onDisable() { handle.deactivate(); }
}
