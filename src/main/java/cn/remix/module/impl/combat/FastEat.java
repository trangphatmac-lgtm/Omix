package cn.remix.module.impl.combat;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.UpdateEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.network.PacketUtil;
import net.minecraft.item.consume.UseAction;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

public final class FastEat extends Module {
    private final NumberValue packets = new NumberValue("Packets", 20, 1, 20, 1);

    public FastEat() {
        super("Fast Eat", Category.Combat);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null) return;

        UseAction action = mc.player.getMainHandStack().getUseAction();
        if (mc.player.isUsingItem()
                && mc.player.getItemUseTimeLeft() >= 30
                && (action == UseAction.EAT || action == UseAction.DRINK)) {
            boolean horizontalCollision = mc.player.horizontalCollision;
            int packetCount = packets.getValue().intValue();

            for (int i = 0; i < packetCount; i++) {
                PacketUtil.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(
                        true,
                        horizontalCollision
                ));
            }
        }
    }
}
