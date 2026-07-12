package cn.remix.util;

import cn.remix.Client;
import cn.remix.module.impl.render.Notify;
import lombok.experimental.UtilityClass;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

@UtilityClass
public class Util implements IMinecraft{
    public int offGroundTicks, onGroundTicks;

    private void addChatMessage(String message) {
        if (mc.player == null) return;
        mc.player.sendMessage(Text.literal(message), false);
    }

    public void log(String message) {
        if (Client.instance != null && Client.instance.getModuleManager() != null) {
            Notify notify = Client.instance.getModuleManager().getModule(Notify.class);
            if (notify != null && notify.isEnabled()) {
                notify.post(message);
                return;
            }
        }

        logToChat(message);
    }

    public void logToChat(String message) {
        addChatMessage(Formatting.DARK_GRAY + "[" + Formatting.AQUA + Client.name
                + Formatting.DARK_GRAY + "] " + Formatting.RESET + formatCodes(message));
    }

    public String formatCodes(String message) {
        return message == null ? "" : message.replace('&', '§');
    }

    public void debug(String message) {
        addChatMessage(Formatting.DARK_GRAY + "[" + Formatting.RED + "Debug" + Formatting.DARK_GRAY + "] " + Formatting.RESET + message);
    }
}
