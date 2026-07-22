package cn.remix.command.impl;

import cn.remix.Client;
import cn.remix.command.Command;
import cn.remix.module.impl.player.LookTP;
import cn.remix.util.Util;

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
