package cn.remix.module.impl.render;

import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.util.Util;
import im.webui.WebUiRuntime;
import im.webui.screen.WebScreenOpenResult;

/**
 * Development-only entry point for the WebUI framework acceptance page.
 * It intentionally has no default key binding and must not replace ClickGui.
 */
public final class WebUiTest extends Module {

    public WebUiTest() {
        super("WebUiTest", Category.Render);
    }

    @Override
    public void onEnable() {
        WebUiRuntime runtime = WebUiRuntime.getInstance();
        WebScreenOpenResult result = runtime.openTestScreen();
        if (result == WebScreenOpenResult.QUEUED) {
            Util.log("&eWebUI is loading: " + runtime.getState());
        } else if (result == WebScreenOpenResult.FAILED) {
            Throwable failure = runtime.getFailure();
            Util.log("&cWebUI failed"
                    + (failure == null || failure.getMessage() == null ? "" : ": " + failure.getMessage()));
        }
        toggle();
    }
}
