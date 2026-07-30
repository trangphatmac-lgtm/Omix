package cn.omix.module.impl.render;

import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.util.Util;
import im.webui.WebUiRuntime;
import im.webui.screen.WebScreenOpenResult;

public final class MusicPlayer extends Module {
    public MusicPlayer() {
        super("MusicPlayer", Category.Render);
    }

    @Override
    public void onEnable() {
        WebUiRuntime runtime = WebUiRuntime.getInstance();
        WebScreenOpenResult result = runtime.openMusicScreen();
        if (result == WebScreenOpenResult.QUEUED) {
            Util.log("&eOmix Music is preparing");
        } else if (result == WebScreenOpenResult.FAILED) {
            Util.log("&cOmix Music failed to open");
        }
        toggle();
    }
}
