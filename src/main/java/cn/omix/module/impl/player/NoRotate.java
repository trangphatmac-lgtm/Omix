package cn.omix.module.impl.player;

import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.util.player.NoRotateCorrection;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;

public final class NoRotate extends Module {
    private final BoolValue flagOnly = new BoolValue("Flag Only", true);

    public NoRotate() {
        super("NoRotate", Category.Player);
    }

    public boolean shouldPreserve(PlayerPositionLookS2CPacket packet) {
        return isNativeBehaviorActive() && mc.player != null && mc.player.age > 0
                && !mc.player.hasVehicle()
                && (!flagOnly.getValue() || NoRotateCorrection.isNearby(
                        packet.change(), packet.relatives(), mc.player.getEntityPos()));
    }

    public boolean shouldPreserveRotation() {
        return isNativeBehaviorActive() && mc.player != null && mc.player.age > 0;
    }
}
