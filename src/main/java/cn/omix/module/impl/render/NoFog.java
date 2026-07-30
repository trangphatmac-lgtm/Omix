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
        return module != null && module.isEnabled();
    }
}
