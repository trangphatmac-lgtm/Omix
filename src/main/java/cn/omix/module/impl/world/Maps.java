package cn.omix.module.impl.world;

import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.ui.sigma.SigmaMapsScreen;

public final class Maps extends Module {
    public Maps() { super("Maps", Category.World); }
    @Override public void onEnable() {
        if (mc.world != null && mc.player != null) mc.setScreen(new SigmaMapsScreen());
        setEnabled(false);
    }
}
