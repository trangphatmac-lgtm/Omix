package cn.omix.util.network;

import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.entity.EntityPosition;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PacketLogFormatterTest {
    @Test
    void movementDoesNotInventAbsentCoordinatesOrRotations() {
        String ground = PacketLogFormatter.details(new PlayerMoveC2SPacket.OnGroundOnly(true, true));
        assertTrue(ground.contains("Position: not included"));
        assertTrue(ground.contains("Rotation: not included"));
        assertTrue(ground.contains("Horizontal collision: true"));
        var position = new PlayerMoveC2SPacket.PositionAndOnGround(1.23456, 64, -5, false, false);
        assertEquals("minecraft:move_player_pos", PacketLogFormatter.name(position));
        assertTrue(PacketLogFormatter.details(position).contains("Position: 1.235, 64.000, -5.000"));
        String look = PacketLogFormatter.details(new PlayerMoveC2SPacket.LookAndOnGround(90, 45, true, false));
        assertTrue(look.contains("Position: not included"));
        assertTrue(look.contains("Rotation: 90.000, 45.000"));
    }

    @Test
    void keepAliveDoesNotTruncateModernLongId() {
        assertTrue(PacketLogFormatter.details(new KeepAliveC2SPacket(Long.MAX_VALUE)).contains("9223372036854775807"));
    }

    @Test
    void itemUseHasHandSequenceAndRotation() {
        String info = PacketLogFormatter.details(new PlayerInteractItemC2SPacket(Hand.OFF_HAND, 123, 90, 15));
        assertTrue(info.contains("Hand: OFF_HAND"));
        assertTrue(info.contains("Sequence: 123"));
        assertTrue(info.contains("Rotation: 90.000, 15.000"));
    }

    @Test
    void modernContainerClickUsesRevisionAndHashes() {
        var packet = new ClickSlotC2SPacket(7, 123, (short) 12, (byte) 1,
                SlotActionType.QUICK_MOVE, new Int2ObjectOpenHashMap<>(), null);
        String info = PacketLogFormatter.details(packet);
        assertTrue(info.contains("Window: 7"));
        assertTrue(info.contains("Revision: 123"));
        assertTrue(info.contains("Action: QUICK_MOVE"));
        assertTrue(info.contains("ItemStackHash"));
    }

    @Test
    void correctionsKeepRelativeFlagsAndVelocityIsAlreadyDecoded() {
        var packet = new PlayerPositionLookS2CPacket(9,
                new EntityPosition(new Vec3d(1, 2, 3), new Vec3d(0.1, 0.2, 0.3), 90, 0), Set.of(PositionFlag.X));
        String info = PacketLogFormatter.details(packet);
        assertTrue(info.contains("Teleport ID: 9"));
        assertTrue(info.contains("Relative flags: [X]"));
        assertTrue(info.contains("Velocity change: 0.100, 0.200, 0.300"));
        String velocity = PacketLogFormatter.details(new EntityVelocityUpdateS2CPacket(2, new Vec3d(1, 0.5, -1)));
        assertTrue(velocity.contains("Velocity: 1.000, 0.500, -1.000"));
    }

    @Test
    void entityInteractionVisitorDescribesAllActionsWithoutLookingUpWorldEntities() {
        for (int action = 0; action < 3; action++) {
            PacketByteBuf bytes = new PacketByteBuf(Unpooled.buffer());
            try {
                bytes.writeVarInt(77);
                bytes.writeVarInt(action);
                if (action == 2) {
                    bytes.writeFloat(1);
                    bytes.writeFloat(2);
                    bytes.writeFloat(3);
                }
                if (action != 1) bytes.writeEnumConstant(Hand.OFF_HAND);
                bytes.writeBoolean(true);
                String info = PacketLogFormatter.details(PlayerInteractEntityC2SPacket.CODEC.decode(bytes));
                assertTrue(info.contains("Sneaking: true"));
                assertTrue(info.contains("Action: " + new String[]{"INTERACT", "ATTACK", "INTERACT_AT"}[action]));
                if (action == 2) assertTrue(info.contains("Hit vector: 1.000, 2.000, 3.000"));
            } finally {
                bytes.release();
            }
        }
    }
}
