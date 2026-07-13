package cn.remix.module.impl.render;

import cn.remix.module.Category;
import cn.remix.module.Module;

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
