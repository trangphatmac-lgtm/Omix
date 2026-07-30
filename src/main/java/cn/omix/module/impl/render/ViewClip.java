package cn.omix.module.impl.render;

import cn.omix.module.Category;
import cn.omix.module.Module;

public final class ViewClip extends Module {

    public ViewClip() {
        super("ViewClip", Category.Render);
    }

    @Override
    public void onEnable() {
        reloadChunks();
    }

    @Override
    public void onDisable() {
        reloadChunks();
    }

    private void reloadChunks() {
        if (mc.worldRenderer != null) {
            mc.worldRenderer.reload();
        }
    }
}
