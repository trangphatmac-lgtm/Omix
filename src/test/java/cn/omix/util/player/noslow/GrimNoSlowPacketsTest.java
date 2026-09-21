package cn.omix.util.player.noslow;

import net.minecraft.entity.EntityPosition;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.HealthUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerActionResponseS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GrimNoSlowPacketsTest {
    private static final int PLAYER = 7;
    private static final int LIVING_FLAGS = 8;
    private final GrimNoSlowPackets policy = new GrimNoSlowPackets();
    private GrimNoSlowState.Hand useHand = GrimNoSlowState.Hand.MAIN_HAND;

    private static EntityTrackerUpdateS2CPacket metadata(int entity, int index, byte flags) {
        // The policy reads the decoded id/value only; no registry or live Minecraft client is needed.
        return new EntityTrackerUpdateS2CPacket(entity,
                List.of(new DataTracker.SerializedEntry<>(index, null, flags)));
    }

    private boolean passes(Packet<?> packet) {
        return policy.bypassesBuffer(packet, PLAYER, LIVING_FLAGS, useHand);
    }

    @Test
    void metadataBracketDoesNotConfirmUsingBeforeBufferedMetadataIsApplied() {
        var before = new CommonPingS2CPacket(-10);
        var using = metadata(PLAYER, LIVING_FLAGS, (byte) 1);
        var after = new CommonPingS2CPacket(-11);
        List<Integer> replies = new ArrayList<>();
        // PacketSelfMetadataListener clears certain use at the first transaction,
        // and sets it at the next. Exercise the actual packet-policy adapter.
        assertFalse(policy.allowsNoSlow(), "Bow must remain slowed before the metadata boundary");
        assertTrue(passes(before));
        replies.add(before.getParameter());
        for (Packet<?> packet : List.of(using, after, new CommonPingS2CPacket(-12))) {
            if (!passes(packet)) policy.enqueue(packet);
        }
        assertTrue(policy.allowsNoSlow());
        assertEquals(List.of(-10), replies, "No pong may jump over buffered use metadata");
        assertEquals(3, policy.size());
        // FIFO replay after release emits each pending pong exactly once, in order.
        List<Packet<?>> replayed = new ArrayList<>();
        policy.flush(() -> true, packet -> {
            replayed.add(packet);
            if (packet instanceof CommonPingS2CPacket ping) replies.add(ping.getParameter());
        });
        assertSame(using, replayed.getFirst());
        assertSame(after, replayed.get(1));
        assertEquals(0, policy.size());
        assertEquals(List.of(-10, -11, -12), replies);
        policy.flush(() -> true, packet -> fail("Already replayed"));
    }

    @Test
    void serverConsumptionStatusKeepsItsPassThroughClassification() {
        PacketByteBuf bytes = new PacketByteBuf(Unpooled.buffer());
        try {
            bytes.writeInt(PLAYER);
            bytes.writeByte(9); // EntityStatuses.CONSUME_ITEM
            var consumed = EntityStatusS2CPacket.CODEC.decode(bytes);
            assertEquals(9, consumed.getStatus());
            assertEquals(GrimNoSlowState.PacketType.ENTITY_STATUS,
                    GrimNoSlowPackets.describe(consumed).type());
            assertEquals(GrimNoSlowState.PacketType.ENTITY_TRACKER,
                    GrimNoSlowPackets.describe(metadata(PLAYER, LIVING_FLAGS, (byte) 3)).type());
            assertEquals(GrimNoSlowState.PacketType.OTHER,
                    GrimNoSlowPackets.describe(new CommonPingS2CPacket(-1)).type());
        } finally {
            bytes.release();
        }
    }

    @Test
    void offhandFlagAlsoOpensBoundaryButOtherEntitiesAndUnrelatedMetadataDoNot() {
        useHand = GrimNoSlowState.Hand.OFF_HAND;
        passes(metadata(8, LIVING_FLAGS, (byte) 1));
        passes(metadata(PLAYER, 0, (byte) 1));
        passes(metadata(PLAYER, LIVING_FLAGS, (byte) 0));
        passes(metadata(PLAYER, LIVING_FLAGS, (byte) 1));
        assertFalse(policy.allowsNoSlow());
        assertTrue(passes(new CommonPingS2CPacket(-1)));
        passes(metadata(PLAYER, LIVING_FLAGS, (byte) 3));
        assertTrue(policy.allowsNoSlow());
        assertFalse(passes(new CommonPingS2CPacket(-2)));
    }

    @Test
    void eachUseNeedsANewConfirmationBoundary() {
        passes(metadata(PLAYER, LIVING_FLAGS, (byte) 1));
        assertTrue(policy.allowsNoSlow());
        policy.clear();
        assertFalse(policy.allowsNoSlow());
        assertTrue(passes(new CommonPingS2CPacket(-3)));
        passes(metadata(PLAYER, LIVING_FLAGS, (byte) 1));
        assertFalse(passes(new CommonPingS2CPacket(-4)));
    }

    @Test
    void mainHandUseCannotBeUnlockedByStaleOffhandMetadata() {
        passes(metadata(PLAYER, LIVING_FLAGS, (byte) 3));
        assertFalse(policy.allowsNoSlow());
        assertTrue(passes(new CommonPingS2CPacket(-1)));
        passes(metadata(PLAYER, LIVING_FLAGS, (byte) 1));
        assertTrue(policy.allowsNoSlow());
        assertFalse(passes(new CommonPingS2CPacket(-2)));
    }

    @Test
    void clearDiscardsWithoutEmittingRepliesAndSessionChangeStopsReplay() {
        policy.enqueue(new CommonPingS2CPacket(-1));
        policy.clear();
        policy.flush(() -> true, packet -> fail("A clear must not emit a pong"));
        policy.enqueue(new CommonPingS2CPacket(-2));
        policy.enqueue(new CommonPingS2CPacket(-3));
        List<Packet<?>> replayed = new ArrayList<>();
        policy.flush(replayed::isEmpty, replayed::add);
        assertEquals(1, replayed.size());
        assertEquals(-2, ((CommonPingS2CPacket) replayed.getFirst()).getParameter());
        assertEquals(0, policy.size(), "Old packets must never reach a new session");
    }

    @Test
    void correctionAndUseAcknowledgementAreNeverStoredUntilRelease() {
        passes(metadata(PLAYER, LIVING_FLAGS, (byte) 1));
        var position = new EntityPosition(Vec3d.ZERO, Vec3d.ZERO, 0, 0);
        assertTrue(passes(new PlayerPositionLookS2CPacket(123, position, Set.of())));
        assertTrue(passes(new PlayerActionResponseS2CPacket(42)));
        assertTrue(passes(new HealthUpdateS2CPacket(20, 20, 5)));
        assertTrue(passes(new KeepAliveS2CPacket(99L)));
        assertFalse(passes(new CommonPingS2CPacket(-4)), "Ping is not KeepAlive");
    }
}
