package cn.omix.module.impl.render;

import cn.omix.module.Category;
import cn.omix.module.Module;
import im.webui.WebUiRuntime;
import im.webui.screen.WebScreenOpenResult;
import im.webui.screen.WebScreenType;
import org.lwjgl.glfw.GLFW;

public final class ClickGui extends Module {

    public ClickGui() {
        super("ClickGui", Category.Render);
        setKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    @Override
    public void onEnable() {
        WebScreenOpenResult result = WebUiRuntime.getInstance().openScreen(WebScreenType.CLICK_GUI);
        if (result == WebScreenOpenResult.FAILED) {
            // Keep the original native ClickGUI as a safe fallback when CEF is unavailable.
            mc.setScreen(instance.getClickGuiScreen());
        }
        toggle();
    }
}
