package cn.omix.module.impl.world;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.PacketEvent;
import cn.omix.event.impl.UpdateEvent;
import cn.omix.event.impl.WorldEvent;
import cn.omix.util.Util;

public final class AutoGG extends VictoryActionModule {
    public AutoGG() {
        super("AutoGG");
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        handlePacket(event);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        handleUpdate();
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        resetVictoryState();
    }

    @Override
    protected void performVictoryAction() {
        mc.player.networkHandler.sendChatMessage("GG");
        Util.log("&aAutoGG sent GG.");
    }
}
