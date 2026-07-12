package me.ksyz.accountmanager.auth;

import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import injection.accessor.MinecraftClientAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;

public final class SessionService {
    private SessionService() {
    }

    public static Session current() {
        return MinecraftClient.getInstance().getSession();
    }

    public static SwitchResult switchTo(Session session) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            MinecraftClientAccessor accessor = (MinecraftClientAccessor) client;
            accessor.setSession(session);
            accessor.setUserApiService(new YggdrasilAuthenticationService(client.getNetworkProxy())
                    .createUserApiService(session.getAccessToken()));
            return new SwitchResult(true, "Logged in as " + session.getUsername() + ".");
        } catch (Exception e) {
            return new SwitchResult(false, "Could not switch session: " + e.getMessage());
        }
    }

    public record SwitchResult(boolean success, String message) {
    }
}
