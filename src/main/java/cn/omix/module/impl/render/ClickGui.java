package cn.omix.module.impl.render;

import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.ModeValue;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.module.value.impl.ColorValue;
import cn.omix.module.value.impl.NumberValue;
import im.webui.WebUiRuntime;
import im.webui.screen.WebScreenOpenResult;
import im.webui.screen.WebScreenType;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;

public final class ClickGui extends Module {
    private final ModeValue mode = new ModeValue("Mode", "Web", "Web", "Remix", "Sigma", "Skeet", "Setsuna");
    private final BoolValue setsunaDaylight = new BoolValue("Setsuna Daylight", false, () -> mode.is("Setsuna"));
    private final NumberValue setsunaBlur = new NumberValue("Setsuna Blur", 5, 0, 10, 1, () -> mode.is("Setsuna"));
    private final ColorValue setsunaAccent = new ColorValue("Setsuna Accent", new Color(166, 86, 238), () -> mode.is("Setsuna"));
    private final NumberValue setsunaScale = new NumberValue("Setsuna Scale", 100, 65, 125, 5, () -> mode.is("Setsuna"));

    public boolean setsunaDaylight() { return setsunaDaylight.getValue(); }
    public int setsunaBlur() { return setsunaBlur.getValue().intValue(); }
    public int setsunaAccent() { return setsunaAccent.getValue().getRGB(); }
    public float setsunaScale() { return setsunaScale.getValue() / 100f; }

    public ClickGui() {
        super("ClickGui", Category.Render);
        setKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    @Override
    public void onEnable() {
        if (mode.is("Setsuna")) {
            mc.setScreen(new cn.omix.ui.setsuna.SetsunaClickGuiScreen());
        } else if (mode.is("Skeet")) {
            mc.setScreen(new cn.omix.ui.skeet.SkeetClickGuiScreen());
        } else if (mode.is("Sigma")) {
            mc.setScreen(new cn.omix.ui.sigma.SigmaClickGuiScreen());
        } else if (mode.is("Remix")) {
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
