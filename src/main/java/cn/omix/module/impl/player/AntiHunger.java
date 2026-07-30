package cn.omix.module.impl.player;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.PacketEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.BoolValue;
import injection.accessor.PlayerMoveC2SPacketAccessor;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

public class AntiHunger extends Module {
    private final BoolValue ground = new BoolValue("Cancel Ground", true);
    private final BoolValue sprint = new BoolValue("Cancel Sprint", true);

    public AntiHunger() {
        super("AntiHunger", Category.Player);
    }

    @EventTarget
    public void onPacketSend(PacketEvent e) {
        if (e.getPacket() instanceof PlayerMoveC2SPacket pac && ground.getValue()) {
            ((PlayerMoveC2SPacketAccessor) pac).setOnGround(false);
        }

        if (e.getPacket() instanceof ClientCommandC2SPacket pac && sprint.getValue()) {
            if (pac.getMode() == ClientCommandC2SPacket.Mode.START_SPRINTING) {
                e.setCancelled();
            }
        }
    }
}
