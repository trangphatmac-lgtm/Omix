package injection;

import cn.omix.util.ai.AiContainerTools;
import cn.omix.event.impl.PlayerPositionLookEvent;
import cn.omix.module.impl.exploits.Disabler;
import cn.omix.module.impl.player.SpectatorTP;
import cn.omix.util.IMinecraft;
import cn.omix.util.world.AutoLSignals;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerAbilitiesS2CPacket;
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

    @Inject(method = "onEntity", at = @At("TAIL"))
    private void omix$adjustMove(net.minecraft.network.packet.s2c.play.EntityS2CPacket packet, CallbackInfo ci) {
        cn.omix.util.world.adjustdetect.AdjustDetectorSignals.applied(packet);
    }

    @Inject(method = "onEntityPosition", at = @At("TAIL"))
    private void omix$adjustPosition(net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket packet, CallbackInfo ci) {
        cn.omix.util.world.adjustdetect.AdjustDetectorSignals.applied(packet);
    }

    @Inject(method = "onEntityPositionSync", at = @At("TAIL"))
    private void omix$adjustPositionSync(net.minecraft.network.packet.s2c.play.EntityPositionSyncS2CPacket packet, CallbackInfo ci) {
        cn.omix.util.world.adjustdetect.AdjustDetectorSignals.applied(packet);
    }

    @Inject(method = "onEntityAnimation", at = @At("TAIL"))
    private void omix$adjustAnimation(net.minecraft.network.packet.s2c.play.EntityAnimationS2CPacket packet, CallbackInfo ci) {
        cn.omix.util.world.adjustdetect.AdjustDetectorSignals.applied(packet);
    }

    @Inject(method = "onEntityEquipmentUpdate", at = @At("TAIL"))
    private void omix$adjustEquipment(net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket packet, CallbackInfo ci) {
        cn.omix.util.world.adjustdetect.AdjustDetectorSignals.applied(packet);
    }

    @Inject(method = "onBlockBreakingProgress", at = @At("TAIL"))
    private void omix$adjustBreak(net.minecraft.network.packet.s2c.play.BlockBreakingProgressS2CPacket packet, CallbackInfo ci) {
        cn.omix.util.world.adjustdetect.AdjustDetectorSignals.applied(packet);
    }

    @Inject(method = "onBlockUpdate", at = @At("TAIL"))
    private void omix$adjustBlock(net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket packet, CallbackInfo ci) {
        cn.omix.util.world.adjustdetect.AdjustDetectorSignals.applied(packet);
    }

    @Inject(method = "onEntitySpawn", at = @At("TAIL"))
    private void omix$adjustSpawn(net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket packet, CallbackInfo ci) {
        cn.omix.util.world.adjustdetect.AdjustDetectorSignals.applied(packet);
    }

    @Inject(method = "onPlayerPositionLook", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/network/PacketApplyBatcher;)V",
            shift = At.Shift.AFTER), cancellable = true)
    private void omix$fullMovementCorrection(PlayerPositionLookS2CPacket packet, CallbackInfo ci) {
        if (instance == null || instance.getModuleManager() == null) return;
        SpectatorTP spectatorTP = instance.getModuleManager().getModule(SpectatorTP.class);
        if (spectatorTP != null && spectatorTP.handlePacket(packet)) {
            ci.cancel();
            return;
        }
        Disabler disabler = instance.getModuleManager().getModule(Disabler.class);
        if (disabler != null && disabler.handleFullMovementCorrection(packet)) ci.cancel();
    }

    @Inject(method = "onGameStateChange", at = @At("TAIL"))
    private void omix$spectatorGameMode(GameStateChangeS2CPacket packet, CallbackInfo ci) {
        if (instance == null || instance.getModuleManager() == null) return;
        SpectatorTP spectatorTP = instance.getModuleManager().getModule(SpectatorTP.class);
        if (spectatorTP != null) spectatorTP.handlePacket(packet);
    }

    @Inject(method = "onPlayerAbilities", at = @At("TAIL"))
    private void omix$spectatorAbilities(PlayerAbilitiesS2CPacket packet, CallbackInfo ci) {
        if (instance == null || instance.getModuleManager() == null) return;
        SpectatorTP spectatorTP = instance.getModuleManager().getModule(SpectatorTP.class);
        if (spectatorTP != null) spectatorTP.handlePacket(packet);
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
        cn.omix.util.world.adjustdetect.AdjustDetectorSignals.applied(packet);
        AutoLSignals.status(packet);
        if (mc.world != null && packet.getStatus() == net.minecraft.entity.EntityStatuses.PLAY_DEATH_SOUND_OR_ADD_PROJECTILE_HIT_PARTICLES
                && cn.omix.util.opai.OpaiHud.enabled(cn.omix.util.opai.OpaiHud.Widget.SESSION_HUD))
            cn.omix.util.opai.OpaiHud.SessionHud.SessionTracker.death(packet.getEntity(mc.world));
    }

    @Inject(method = "onEntityDamage", at = @At("TAIL"))
    private void omix$autoLDamage(EntityDamageS2CPacket packet, CallbackInfo ci) {
        cn.omix.util.world.adjustdetect.AdjustDetectorSignals.applied(packet);
        AutoLSignals.damage(packet);
    }

    @Inject(method = "onEntityTrackerUpdate", at = @At("TAIL"))
    private void omix$autoLMetadata(EntityTrackerUpdateS2CPacket packet, CallbackInfo ci) {
        cn.omix.util.world.adjustdetect.AdjustDetectorSignals.applied(packet);
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
