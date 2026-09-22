package cn.omix.module.impl.render;

import cn.omix.Client;
import cn.omix.module.Category;
import cn.omix.module.Module;

public final class NoFog extends Module {
    public NoFog() {
        super("NoFog", Category.Render);
    }

    public static boolean isActive() {
        Client client = Client.instance;
        if (client == null || client.getModuleManager() == null) return false;

        NoFog module = client.getModuleManager().getModule(NoFog.class);
        if (module != null && module.getScriptMode() != null) return cn.omix.util.script.ModeHost.query(module, cn.omix.script.api.ModeHooks.INTERCEPT, "fog", false);
        return module != null && module.isNativeBehaviorActive();
    }
}
