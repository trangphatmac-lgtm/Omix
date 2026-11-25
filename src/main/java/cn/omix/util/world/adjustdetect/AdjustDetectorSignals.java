package cn.omix.util.world.adjustdetect;

import cn.omix.Client;
import cn.omix.module.impl.world.AntiCheat;
import net.minecraft.network.packet.Packet;

/** Bridges applied packets into the active AntiCheat module. */
public final class AdjustDetectorSignals {
    private AdjustDetectorSignals() {}

    public static void applied(Packet<?> packet) {
        if (Client.instance == null || Client.instance.getModuleManager() == null) return;
        AntiCheat module = Client.instance.getModuleManager().getModule(AntiCheat.class);
        if (module != null) module.observePacket(packet);
    }
}
