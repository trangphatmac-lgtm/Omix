package cn.omix.management;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.PacketEvent;
import cn.omix.event.impl.WorldEvent;
import cn.omix.management.packet.impl.Blink;
import cn.omix.management.packet.impl.Delay;
import cn.omix.util.IMinecraft;
import lombok.Getter;

@Getter
public class PacketManager implements IMinecraft {
    private final Delay delay = new Delay();
    private final Blink blink = new Blink();

    public PacketManager() {
        instance.getEventManager().register(this);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.player == null) {
            delay.clear();
            blink.clear();
            return;
        }

        if (mc.player.isDead() || mc.getNetworkHandler() == null || mc.isInSingleplayer()) {
            delay.dispatch(true);
            blink.dispatch(true);
            return;
        }

        switch (event.getType()) {
            case Received -> delay.handle(event);
            case Send -> blink.handle(event);
        }
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        delay.dispatch(true);
        blink.dispatch(true);
    }
}