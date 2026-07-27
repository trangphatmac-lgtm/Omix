package cn.omix.module.impl.render;

import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.util.Util;
import im.webui.WebUiRuntime;
import im.webui.screen.WebScreenOpenResult;
import org.lwjgl.glfw.GLFW;

public final class AIScreen extends Module {

    public AIScreen() {
        super("AIScreen", Category.Render);
        setKey(GLFW.GLFW_KEY_PERIOD);
    }

    @Override
    public void onEnable() {
        WebUiRuntime runtime = WebUiRuntime.getInstance();
        WebScreenOpenResult result = runtime.openAiScreen();
        if (result == WebScreenOpenResult.QUEUED) {
            Util.log("&eAI WebUI is loading: " + runtime.getState());
        } else if (result == WebScreenOpenResult.FAILED) {
            Throwable failure = runtime.getFailure();
            Util.log("&cAI WebUI failed"
                    + (failure == null || failure.getMessage() == null ? "" : ": " + failure.getMessage()));
        }
        toggle();
    }
}
