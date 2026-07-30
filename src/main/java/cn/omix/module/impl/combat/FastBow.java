package cn.omix.module.impl.combat;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.UpdateEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.NumberValue;
import cn.omix.util.network.PacketUtil;
import net.minecraft.item.consume.UseAction;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class FastBow extends Module {
    private final NumberValue packets = new NumberValue("Packets", 20, 1, 20, 1);

    public FastBow() {
        super("Fast Bow", Category.Combat);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null) return;

        if (mc.player.isUsingItem()
                && mc.player.getItemUseTimeLeft() >= 30
                && mc.player.getMainHandStack().getUseAction() == UseAction.BOW) {
            float yaw = mc.player.getYaw();
            float pitch = mc.player.getPitch();
            boolean horizontalCollision = mc.player.horizontalCollision;
            int packetCount = packets.getValue().intValue();

            for (int i = 0; i < packetCount; i++) {
                PacketUtil.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                        yaw,
                        pitch,
                        true,
                        horizontalCollision
                ));
            }

            PacketUtil.sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.RELEASE_USE_ITEM,
                    BlockPos.ORIGIN,
                    Direction.DOWN
            ));
        }
    }
}
