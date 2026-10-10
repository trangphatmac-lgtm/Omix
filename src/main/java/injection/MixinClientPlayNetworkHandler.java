package injection;

import cn.omix.util.ai.AiContainerTools;
import cn.omix.event.impl.PlayerPositionLookEvent;
import cn.omix.module.impl.exploits.Disabler;
import cn.omix.util.IMinecraft;
import cn.omix.util.world.AutoLSignals;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class MixinClientPlayNetworkHandler implements IMinecraft {

    @Inject(method = "onPlayerPositionLook", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/network/PacketApplyBatcher;)V",
            shift = At.Shift.AFTER), cancellable = true)
    private void omix$fullMovementCorrection(PlayerPositionLookS2CPacket packet, CallbackInfo ci) {
        if (instance == null || instance.getModuleManager() == null) return;
        Disabler disabler = instance.getModuleManager().getModule(Disabler.class);
        if (disabler != null && disabler.handleFullMovementCorrection(packet)) ci.cancel();
    }

    @Inject(method = "onEntityVelocityUpdate", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/network/PacketApplyBatcher;)V",
            shift = At.Shift.AFTER))
    private void omix$fullMovementVelocity(EntityVelocityUpdateS2CPacket packet, CallbackInfo ci) {
        if (instance == null || instance.getModuleManager() == null) return;
        Disabler disabler = instance.getModuleManager().getModule(Disabler.class);
        if (disabler != null) disabler.handleFullMovementVelocity(packet);
    }

    // TAIL runs after forceMainThread and before the next packet can remove/reset the entity.
    @Inject(method = "onEntityStatus", at = @At("TAIL"))
    private void omix$autoLStatus(EntityStatusS2CPacket packet, CallbackInfo ci) {
        AutoLSignals.status(packet);
        if (mc.world != null && packet.getStatus() == net.minecraft.entity.EntityStatuses.PLAY_DEATH_SOUND_OR_ADD_PROJECTILE_HIT_PARTICLES
                && cn.omix.util.opai.OpaiHud.enabled(cn.omix.util.opai.OpaiHud.Widget.SESSION_HUD))
            cn.omix.util.opai.OpaiHud.SessionHud.SessionTracker.death(packet.getEntity(mc.world));
    }

    @Inject(method = "onEntityDamage", at = @At("TAIL"))
    private void omix$autoLDamage(EntityDamageS2CPacket packet, CallbackInfo ci) {
        AutoLSignals.damage(packet);
    }

    @Inject(method = "onEntityTrackerUpdate", at = @At("TAIL"))
    private void omix$autoLMetadata(EntityTrackerUpdateS2CPacket packet, CallbackInfo ci) {
        AutoLSignals.metadata(packet);
    }

    @Inject(method = "onInventory", at = @At("RETURN"))
    private void afterContainerInventoryApplied(InventoryS2CPacket packet, CallbackInfo ci) {
        AiContainerTools.inventorySynchronized(mc, packet.syncId());
    }

    @Inject(
            method = "onPlayerPositionLook",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/ClientConnection;send(Lnet/minecraft/network/packet/Packet;)V",
                    ordinal = 1,
                    shift = At.Shift.BEFORE
            )
    )
    private void afterPlayerPositionApplied(PlayerPositionLookS2CPacket packet, CallbackInfo ci) {
        if (mc.player == null) return;

        Vec3d position = new Vec3d(
                mc.player.getX(),
                mc.player.getY(),
                mc.player.getZ()
        );
        instance.getEventManager().call(new PlayerPositionLookEvent(position));
    }
}
