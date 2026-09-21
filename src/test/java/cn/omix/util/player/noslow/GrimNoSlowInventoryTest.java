package cn.omix.util.player.noslow;

import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.SetPlayerInventoryS2CPacket;
import net.minecraft.util.Hand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GrimNoSlowInventoryTest {
    @Test
    void fullInventorySnapshotsWaitWithBothKindsOfSingleSlotUpdates() {
        // Test production classification using real packet classes without trying
        // to bootstrap item registries outside the Fabric/Mixin runtime.
        for (Class<?> type : List.of(InventoryS2CPacket.class, ScreenHandlerSlotUpdateS2CPacket.class,
                SetPlayerInventoryS2CPacket.class)) {
            assertFalse(GrimNoSlowPackets.bypassesBuffer(type), type.getSimpleName());
        }
    }

    @ParameterizedTest
    @EnumSource(Hand.class)
    void usePacketDescriptionPreservesTheVanillaHandSequenceAndView(Hand hand) {
        var use = new PlayerInteractItemC2SPacket(hand, 37, 123.5F, -42.25F);
        var description = GrimNoSlowPackets.describe(use);
        assertEquals(hand == Hand.MAIN_HAND ? GrimNoSlowState.Hand.MAIN_HAND : GrimNoSlowState.Hand.OFF_HAND,
                description.hand());
        assertEquals(use.getSequence(), description.sequence());
        assertEquals(use.getYaw(), description.yaw());
        assertEquals(use.getPitch(), description.pitch());
    }
}
