package cn.omix.command.impl;

import cn.omix.Client;
import cn.omix.command.Command;
import cn.omix.module.impl.player.LookTP;
import cn.omix.util.Util;

public final class VClipCommand extends Command {

    public VClipCommand() {
        super(".vclip", "vclip");
    }

    @Override
    public void execute(String[] arguments) {
        if (arguments.length != 1) {
            Util.logToChat(getUsage());
            return;
        }

        LookTP lookTP = Client.instance.getModuleManager().getModule(LookTP.class);
        if (lookTP == null) {
            Util.logToChat("&cLookTP is unavailable.");
            return;
        }
        lookTP.teleportToSurface();
    }
}
