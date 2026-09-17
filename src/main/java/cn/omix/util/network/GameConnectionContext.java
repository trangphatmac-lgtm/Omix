package cn.omix.util.network;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ServerInfo;
import org.fisproxy.SessionStatus;

/** Gameplay identity is separate from the launcher session and the proxy transport address. */
public final class GameConnectionContext {
    private GameConnectionContext() {
    }

    public static String username(MinecraftClient client) {
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        return resolveUsername(handler == null ? null : handler.getProfile(), client.getSession().getUsername());
    }

    static String resolveUsername(GameProfile profile, String sessionUsername) {
        return profile != null && profile.name() != null && !profile.name().isBlank()
                ? profile.name() : sessionUsername;
    }

    public static String serverAddress(MinecraftClient client) {
        return serverAddress(client.getCurrentServerEntry());
    }

    public static String serverAddress(ServerInfo server) {
        if (server == null) return null;
        return server instanceof FisProxyServerInfo proxy ? proxy.targetAddress : server.address;
    }

    public static ServerInfo fisProxyServerInfo(String entrance, SessionStatus status) {
        Object target = status.running() && status.session() != null ? status.session().get("target") : null;
        String targetAddress = target instanceof String value && !value.isBlank() ? value.trim() : entrance;
        return new FisProxyServerInfo(entrance, targetAddress);
    }

    // Bound to this connection's ServerInfo, so disconnects and unrelated connections cannot
    // inherit stale proxy metadata. Reconnect reuses the entry and keeps both addresses.
    private static final class FisProxyServerInfo extends ServerInfo {
        private final String targetAddress;

        private FisProxyServerInfo(String entrance, String targetAddress) {
            super("FisProxy", entrance, ServerType.OTHER);
            this.targetAddress = targetAddress;
        }
    }
}
