package cn.omix.module.impl.combat;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.UpdateEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.NumberValue;
import cn.omix.util.network.PacketUtil;
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
