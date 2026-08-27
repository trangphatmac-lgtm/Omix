package cn.omix.command.impl;

import cn.omix.command.Command;
import cn.omix.util.Util;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;

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

        ServerAddress serverAddress = ServerAddress.parse(serverInfo.address);

        // The command is detected while the chat packet is being sent. Queue the
        // reconnect so the old send call can finish before its connection is closed.
        client.send(() -> reconnect(client, serverAddress, serverInfo));
    }

    private static void reconnect(
            MinecraftClient client,
            ServerAddress serverAddress,
            ServerInfo serverInfo
    ) {
        // Use the vanilla quit path so the old channel is closed cleanly and all
        // world/network state is torn down before a new connection is opened.
        client.disconnect(Text.translatable("disconnect.quitting"));

        MultiplayerScreen parent = new MultiplayerScreen(new TitleScreen());
        ConnectScreen.connect(parent, client, serverAddress, serverInfo, false, null);
    }
}
