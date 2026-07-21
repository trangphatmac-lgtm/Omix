package cn.remix.module.impl.combat;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.AttackEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.network.PacketUtil;
import net.minecraft.item.MaceItem;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

public final class MaceDamageBooster extends Module {
    private final NumberValue height = new NumberValue("Height", 10, 1, 100, 1);

    public MaceDamageBooster() {
        super("Mace Exploit", Category.Combat);
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (mc.player == null || !(mc.player.getMainHandStack().getItem() instanceof MaceItem)) return;

        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();
        boolean horizontalCollision = mc.player.horizontalCollision;
        int heightValue = height.getValue().intValue();

        setSuffix(heightValue + "m");
        sendPosition(x, y, z, true, horizontalCollision);

        if (heightValue > 10) {
            for (int i = 0; i < 10; i++) {
                sendPosition(x, y, z, true, horizontalCollision);
            }
        }

        sendPosition(x, y + heightValue, z, false, horizontalCollision);
        sendPosition(x, y + 0.5, z, false, horizontalCollision);
    }

    private void sendPosition(double x, double y, double z, boolean onGround, boolean horizontalCollision) {
        PacketUtil.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                x,
                y,
                z,
                onGround,
                horizontalCollision
        ));
    }
}
