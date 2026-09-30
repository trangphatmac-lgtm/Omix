package cn.omix.util.world;

import cn.omix.Client;
import cn.omix.module.impl.world.AutoL;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;

/** Called only while vanilla applies a packet on the client thread. */
public final class AutoLSignals {
    private AutoLSignals() {}

    private static AutoL active() {
        if (Client.instance == null || Client.instance.getModuleManager() == null) return null;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return null;
        AutoL module = Client.instance.getModuleManager().getModule(AutoL.class);
        return module != null && module.isNativeBehaviorActive() ? module : null;
    }

    public static void status(EntityStatusS2CPacket packet) {
        AutoL module = active();
        if (module != null && packet.getStatus() == EntityStatuses.PLAY_DEATH_SOUND_OR_ADD_PROJECTILE_HIT_PARTICLES) {
            module.confirmDeath(packet.getEntity(MinecraftClient.getInstance().world));
        }
    }

    public static void damage(EntityDamageS2CPacket packet) {
        AutoL module = active();
        if (module == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        // sourceCauseId is the owner for attributed projectiles; the direct source may be an arrow.
        if (packet.sourceCauseId() == mc.player.getId() || packet.sourceDirectId() == mc.player.getId()) {
            Entity target = mc.world.getEntityById(packet.entityId());
            module.recordDamage(target);
            module.checkDeath(target);
        }
    }

    public static void metadata(EntityTrackerUpdateS2CPacket packet) {
        AutoL module = active();
        if (module != null) module.checkDeath(MinecraftClient.getInstance().world.getEntityById(packet.id()));
    }
}
