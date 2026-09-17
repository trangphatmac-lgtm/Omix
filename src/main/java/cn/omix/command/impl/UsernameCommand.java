package cn.omix.command.impl;

import cn.omix.command.Command;
import cn.omix.util.Util;
import cn.omix.util.network.GameConnectionContext;
import net.minecraft.client.MinecraftClient;

public final class UsernameCommand extends Command {

    public UsernameCommand() {
        super(".username/.name/.ign", "username", "name", "ign");
    }

    @Override
    public void execute(String[] arguments) {
        Util.logToChat("Username: &b" + GameConnectionContext.username(MinecraftClient.getInstance()));
    }
}
