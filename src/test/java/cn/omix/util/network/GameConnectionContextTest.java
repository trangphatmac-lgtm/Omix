package cn.omix.util.network;

import com.mojang.authlib.GameProfile;
import net.minecraft.SharedConstants;
import net.minecraft.client.network.ServerInfo;
import org.fisproxy.SessionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GameConnectionContextTest {
    @BeforeAll
    static void initializeGameVersion() {
        SharedConstants.createGameVersion();
    }

    @Test
    void autoNfaUsesServerProfileInsteadOfLauncherAccount() {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "AllocatedNfa");
        assertEquals("AllocatedNfa", GameConnectionContext.resolveUsername(profile, "LauncherAccount"));
    }

    @Test
    void disconnectFallsBackToCurrentLauncherAccountWithoutRememberingNfa() {
        GameConnectionContext.resolveUsername(new GameProfile(UUID.randomUUID(), "OldNfa"), "LauncherAccount");
        assertEquals("NewLauncherAccount", GameConnectionContext.resolveUsername(null, "NewLauncherAccount"));
        assertEquals("LauncherAccount", GameConnectionContext.resolveUsername(
                new GameProfile(UUID.randomUUID(), " "), "LauncherAccount"));
    }

    @Test
    void targetDoesNotOverwriteEntranceUsedForHandshakeAndReconnect() {
        ServerInfo proxy = GameConnectionContext.fisProxyServerInfo("entrance.example:25566", status(" mc.hypixel.net "));
        assertEquals("mc.hypixel.net", GameConnectionContext.serverAddress(proxy));
        assertEquals("entrance.example:25566", proxy.address);
        // The same entry is reused by .reconnect, including an explicitly selected secondary entrance.
        assertEquals("mc.hypixel.net", GameConnectionContext.serverAddress(proxy));
    }

    @Test
    void normalServersAndOtherProxySessionsNeverInheritPreviousTarget() {
        ServerInfo first = GameConnectionContext.fisProxyServerInfo("entrance.example", status("first.example"));
        ServerInfo second = GameConnectionContext.fisProxyServerInfo("entrance.example", status("second.example:25570"));
        ServerInfo direct = new ServerInfo("Direct", "direct.example", ServerInfo.ServerType.OTHER);
        assertEquals("first.example", GameConnectionContext.serverAddress(first));
        assertEquals("second.example:25570", GameConnectionContext.serverAddress(second));
        assertEquals("direct.example", GameConnectionContext.serverAddress(direct));
        assertNull(GameConnectionContext.serverAddress((ServerInfo) null));
    }

    @Test
    void missingOrMalformedTargetFallsBackToEntrance() {
        for (Object target : new Object[]{"", "  ", 123, Map.of("unexpected", "value")}) {
            assertEquals("entrance.example", GameConnectionContext.serverAddress(
                    GameConnectionContext.fisProxyServerInfo("entrance.example", status(target))));
        }
        for (Map<String, Object> response : java.util.List.<Map<String, Object>>of(
                Map.of("running", true),
                Map.of("running", true, "session", Map.of()),
                Map.of("running", false, "session", Map.of("target", "old.example")))) {
            assertEquals("entrance.example", GameConnectionContext.serverAddress(
                    GameConnectionContext.fisProxyServerInfo("entrance.example", SessionStatus.fromResponse(response))));
        }
    }

    @Test
    void preservesCustomPortsAndIpv6Targets() {
        String address = "[2001:db8::1]:25570";
        assertEquals(address, GameConnectionContext.serverAddress(
                GameConnectionContext.fisProxyServerInfo("entrance.example", status(address))));
    }

    private static SessionStatus status(Object target) {
        return SessionStatus.fromResponse(Map.of("running", true, "session", Map.of("target", target)));
    }
}
