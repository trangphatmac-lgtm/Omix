package cn.omix.util;

import cn.omix.Client;
import cn.omix.module.impl.render.Notify;
import lombok.experimental.UtilityClass;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

@UtilityClass
public class Util implements IMinecraft{
    public int offGroundTicks, onGroundTicks;

    private void addChatMessage(String message) {
        addChatMessage(Text.literal(message));
    }

    private void addChatMessage(Text message) {
        if (mc.player == null) return;
        mc.player.sendMessage(message, false);
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

    public void logToChat(Text message) {
        addChatMessage(Text.empty()
                .append(Text.literal("[").formatted(Formatting.DARK_GRAY))
                .append(Text.literal(Client.name).formatted(Formatting.AQUA))
                .append(Text.literal("] ").formatted(Formatting.DARK_GRAY))
                .append(message));
    }

    public void logRaw(String message) {
        addChatMessage(formatCodes(message));
    }

    public void logRaw(Text message) {
        addChatMessage(message);
    }

    public String formatCodes(String message) {
        return message == null ? "" : message.replace('&', '§');
    }

    public void debug(String message) {
        addChatMessage(Formatting.DARK_GRAY + "[" + Formatting.RED + "Debug"
                + Formatting.DARK_GRAY + "] " + Formatting.RESET + message);
    }
}
