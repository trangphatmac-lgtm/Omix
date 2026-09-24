package cn.omix.module.impl.render;

import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.ModeValue;
import im.webui.WebUiRuntime;
import im.webui.screen.WebScreenOpenResult;
import im.webui.screen.WebScreenType;
import org.lwjgl.glfw.GLFW;

public final class ClickGui extends Module {
    private final ModeValue mode = new ModeValue("Mode", "Web", "Web", "Remix");

    public ClickGui() {
        super("ClickGui", Category.Render);
        setKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    @Override
    public void onEnable() {
        if (mode.is("Remix")) {
            mc.setScreen(instance.getClickGuiScreen());
        } else {
            WebScreenOpenResult result = WebUiRuntime.getInstance().openScreen(WebScreenType.CLICK_GUI);
            if (result == WebScreenOpenResult.FAILED) {
                // Keep the native ClickGUI available if CEF cannot start.
                mc.setScreen(instance.getClickGuiScreen());
            }
        }
        toggle();
    }
}
