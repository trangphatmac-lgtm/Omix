package cn.omix.util.world.anticheat;

import cn.omix.Client;
import cn.omix.module.impl.world.AntiCheat;
import net.minecraft.network.packet.Packet;

public final class AntiCheatSignals {
    private AntiCheatSignals() {}

    public static void beforeBlockPacket(Packet<?> packet) {
        if (Client.instance == null || Client.instance.getModuleManager() == null) return;
        AntiCheat module = Client.instance.getModuleManager().getModule(AntiCheat.class);
        if (module != null && module.isNativeBehaviorActive()) module.observeBlockPacket(packet);
    }

    public static void entityPacket(Packet<?> packet) {
        if (Client.instance == null || Client.instance.getModuleManager() == null) return;
        AntiCheat module = Client.instance.getModuleManager().getModule(AntiCheat.class);
        if (module != null && module.isNativeBehaviorActive()) module.observeRavenPacket(packet);
    }
}
