package me.ksyz.accountmanager.auth;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import injection.accessor.MinecraftClientAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.ProfileKeys;
import net.minecraft.client.session.Session;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

public final class SessionService {
    private SessionService() {
    }

    public static Session current() {
        return MinecraftClient.getInstance().getSession();
    }

    public static SwitchResult switchTo(Session session) {
        return switchTo(session, false);
    }

    public static SwitchResult switchToOffline(String username) {
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
        Session session = new Session(
                username,
                uuid,
                "",
                Optional.empty(),
                Optional.empty()
        );
        return switchTo(session, true);
    }

    private static SwitchResult switchTo(Session session, boolean offline) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            MinecraftClientAccessor accessor = (MinecraftClientAccessor) client;
            UserApiService userApiService = offline
                    ? UserApiService.OFFLINE
                    : new YggdrasilAuthenticationService(client.getNetworkProxy())
                            .createUserApiService(session.getAccessToken());
            accessor.setSession(session);
            accessor.setUserApiService(userApiService);
            accessor.setProfileKeys(offline
                    ? ProfileKeys.MISSING
                    : ProfileKeys.create(userApiService, session, client.runDirectory.toPath()));
            String prefix = offline ? "Switched to offline account " : "Logged in as ";
            return new SwitchResult(true, prefix + session.getUsername() + ".");
        } catch (Exception e) {
            return new SwitchResult(false, "Could not switch session: " + e.getMessage());
        }
    }

    public record SwitchResult(boolean success, String message) {
    }
}
