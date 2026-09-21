package cn.omix.util.network;

import net.minecraft.entity.EntityPosition;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.PacketType;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static cn.omix.util.network.PacketLogFilter.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

class PacketLogFilterTest {
    private static PacketLogFilter filter(boolean cancelled, boolean bundle,
                                          Set<PacketLogFilter.Kind> ignored, PacketLogRules rules) {
        return new PacketLogFilter(true, cancelled, true, bundle, ignored, rules);
    }

    @Test
    void sentAndReceivedUseIndependentIgnoreIncludeAndListSettings() {
        var sent = filter(true, true, Set.of(KEEP_ALIVE), PacketLogRules.ALL);
        var received = filter(false, true, Set.of(), PacketLogRules.parse("keep_alive,ping", "ping"));
        var options = new PacketLogBuffer.Options(sent, received, false, true, true);
        var buffer = new PacketLogBuffer();
        buffer.offer(new KeepAliveC2SPacket(1), true, false, false, false, 1, options);
        buffer.offer(new KeepAliveS2CPacket(2), false, false, false, false, 2, options);
        buffer.offer(new KeepAliveS2CPacket(3), false, true, false, false, 3, options);
        buffer.offer(new CommonPingS2CPacket(4), false, false, false, false, 4, options);
        var result = buffer.drain(10);
        assertEquals(1, result.size());
        assertFalse(result.getFirst().sent());
        assertTrue(result.getFirst().detail());
        assertTrue(result.getFirst().details().contains("KeepAlive ID: 2"));
        assertTrue(result.getFirst().details().contains("Fields:"));

        var receivedIgnored = new PacketLogBuffer.Options(
                filter(false, true, Set.of(), PacketLogRules.ALL),
                filter(true, true, Set.of(KEEP_ALIVE), PacketLogRules.ALL), false, false, true);
        buffer.offer(new KeepAliveC2SPacket(5), true, false, false, false, 5, receivedIgnored);
        buffer.offer(new KeepAliveS2CPacket(6), false, false, false, false, 6, receivedIgnored);
        buffer.offer(new CommonPingS2CPacket(7), false, true, false, false, 7, receivedIgnored);
        result = buffer.drain(10);
        assertEquals(2, result.size());
        assertTrue(result.getFirst().sent());
        assertTrue(result.getLast().cancelled());
    }

    @Test
    void bundleMembersMustPassTheirOwnRulesAndWhitelistDoesNotOverrideIgnore() {
        var options = filter(true, false, Set.of(), PacketLogRules.ALL);
        assertFalse(options.allows(new CommonPingS2CPacket(1), false, false, true));
        assertTrue(options.allows(new CommonPingS2CPacket(1), false, false, false));
        options = filter(true, true, Set.of(), PacketLogRules.parse("keep_alive", ""));
        assertFalse(options.allows(new CommonPingS2CPacket(1), false, false, true));
        assertTrue(options.allows(new KeepAliveS2CPacket(1), false, false, true));
        options = filter(true, true, Set.of(KEEP_ALIVE), PacketLogRules.parse("keep_alive", ""));
        assertFalse(options.allows(new KeepAliveS2CPacket(1), false, false, false));
    }

    @Test
    void ignoringEntityMovementKeepsPlayerCorrectionsAndKnockback() {
        var options = filter(true, true, Set.of(MOVEMENT), PacketLogRules.ALL);
        assertFalse(options.allows(new EntityS2CPacket.MoveRelative(1, (short) 2, (short) 3, (short) 4, true), false, false, false));
        assertTrue(options.allows(new PlayerPositionLookS2CPacket(1,
                new EntityPosition(Vec3d.ZERO, Vec3d.ZERO, 0, 0), Set.of()), false, false, false));
        assertTrue(options.allows(new EntityVelocityUpdateS2CPacket(1, Vec3d.ZERO), false, false, false));
    }

    @Test
    void noisyReceivedCategoriesAreIndependentAndNamespaceAware() {
        var options = filter(true, true, Set.of(CHUNKS, LIGHT, PARTICLES, SOUNDS, CUSTOM_PAYLOAD), PacketLogRules.ALL);
        for (String id : new String[]{"level_chunk_with_light", "chunks_biomes", "forget_level_chunk",
                "light_update", "level_particles", "sound", "sound_entity", "stop_sound", "custom_payload"}) {
            assertFalse(options.allows(new StubPacket("minecraft:" + id), false, false, false), id);
        }
        assertTrue(options.allows(new StubPacket("mod:sound"), false, false, false));
        assertTrue(options.allows(new StubPacket("minecraft:system_chat"), false, false, false));
        assertTrue(filter(true, true, Set.of(LIGHT), PacketLogRules.ALL)
                .allows(new StubPacket("minecraft:level_chunk_with_light"), false, false, false));
    }

    private record StubPacket(String id) implements Packet<PacketListener> {
        @Override public PacketType<StubPacket> getPacketType() {
            return new PacketType<>(NetworkSide.CLIENTBOUND, Identifier.of(id));
        }
        @Override public void apply(PacketListener listener) {}
    }
}
