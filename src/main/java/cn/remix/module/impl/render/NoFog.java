package cn.remix.module.impl.render;

import cn.remix.Client;
import cn.remix.module.Category;
import cn.remix.module.Module;

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
