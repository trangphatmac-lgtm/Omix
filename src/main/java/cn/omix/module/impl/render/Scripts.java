package cn.omix.module.impl.render;

import cn.omix.module.Module;
import cn.omix.module.Category;
import im.webui.WebUiRuntime;
import im.webui.screen.WebScreenType;

public final class Scripts extends Module {
    public Scripts() { super("Scripts", Category.Render); }
    @Override public void onEnable() { WebUiRuntime.getInstance().openScreen(WebScreenType.SCRIPTS); setEnabled(false); }
}
