package cn.omix.command.impl;

import cn.omix.command.Command;
import cn.omix.util.Util;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

public final class ReconnectCommand extends Command {

    public ReconnectCommand() {
        super(".reconnect", "reconnect", "r");
    }

    @Override
    public void execute(String[] arguments) {
        if (arguments.length != 1) {
            Util.log(getUsage());
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        ServerInfo serverInfo = client.getCurrentServerEntry();
        if (serverInfo == null) {
            Util.log("Not connected to a multiplayer server.");
            return;
        }

        MultiplayerScreen parent = new MultiplayerScreen(new TitleScreen());
        ConnectScreen.connect(
                parent,
                client,
                ServerAddress.parse(serverInfo.address),
                serverInfo,
                false,
                null
        );
    }
}
