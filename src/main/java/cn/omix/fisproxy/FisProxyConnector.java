package cn.omix.fisproxy;

import cn.omix.util.network.GameConnectionContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import org.fisproxy.SessionStatus;

public final class FisProxyConnector {
    private FisProxyConnector() {
    }

    public static void connect(MinecraftClient client, String address, SessionStatus status) {
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("FisProxy did not return a connection address.");
        }

        String normalized = address.trim();
        ServerAddress serverAddress = ServerAddress.parse(normalized);
        ServerInfo serverInfo = GameConnectionContext.fisProxyServerInfo(normalized, status);
        MultiplayerScreen parent = new MultiplayerScreen(new TitleScreen());

        if (client.world != null) {
            client.disconnect(Text.translatable("disconnect.quitting"));
        }
        ConnectScreen.connect(parent, client, serverAddress, serverInfo, false, null);
    }
}
