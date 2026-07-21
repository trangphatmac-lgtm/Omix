package cn.remix.module.impl.world;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.KeyInputEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.KeyValue;
import cn.remix.module.value.impl.TextValue;
import org.lwjgl.glfw.GLFW;

public final class QuickMacro extends Module {
    private final KeyValue macroKey = new KeyValue("Macro Key", GLFW.GLFW_KEY_X);
    private final TextValue messageOrCommand = new TextValue("Message / Command", "");

    public QuickMacro() {
        super("Quick Macro", Category.World);
    }

    @EventTarget
    public void onKey(KeyInputEvent event) {
        if (mc.player == null
                || event.getAction() != GLFW.GLFW_PRESS
                || event.getKey() != macroKey.getValue()) {
            return;
        }

        String message = messageOrCommand.getValue();
        if (message.startsWith("/")) {
            mc.player.networkHandler.sendChatCommand(message.substring(1));
        } else {
            mc.player.networkHandler.sendChatMessage(message);
        }
    }
}
