package injection;

import cn.omix.event.impl.AttackEvent;
import cn.omix.module.impl.combat.Reach;
import cn.omix.module.impl.move.KeepSprint;
import cn.omix.module.impl.move.NoSlowDown;
import cn.omix.module.impl.player.ChestArua;
import cn.omix.util.IMinecraft;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class MixinClientPlayerInteractionManager implements IMinecraft {

    @Shadow private int lastSelectedSlot;

    @Inject(method = "syncSelectedSlot", at = @At("HEAD"))
    private void omix$noSlowSlot(CallbackInfo ci) {
        var grim = NoSlowDown.activeGrim();
        if (grim != null && grim.lockSlot()) lastSelectedSlot = mc.player.getInventory().getSelectedSlot();
    }

    // Let the initial selected slot reach the server before PREPARING locks it.
    @Inject(method = "interactItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;syncSelectedSlot()V",
            shift = At.Shift.AFTER), cancellable = true)
    private void omix$noSlowUse(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        var grim = NoSlowDown.activeGrim();
        if (player == mc.player && grim != null && grim.beforeUse(hand)) cir.setReturnValue(ActionResult.PASS);
    }

    @Inject(method = "interactBlock", at = @At("HEAD"))
    private void omix$restoreNoSlowBeforeBlock(ClientPlayerEntity player, Hand hand, BlockHitResult hit,
                                               CallbackInfoReturnable<ActionResult> cir) {
        var grim = NoSlowDown.activeGrim();
        if (player == mc.player && grim != null) grim.abort();
    }

    @Inject(method = "clickSlot", at = @At("HEAD"))
    private void omix$restoreNoSlowBeforeClick(int syncId, int slotId, int button,
            net.minecraft.screen.slot.SlotActionType action, PlayerEntity player, CallbackInfo ci) {
        var grim = NoSlowDown.activeGrim();
        if (player == mc.player && grim != null) grim.abort();
    }

    @Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
    private void omix$chestAruaExclusiveUse(ClientPlayerEntity player, Hand hand, BlockHitResult hit,
                                           CallbackInfoReturnable<ActionResult> cir) {
        if (instance == null || instance.getModuleManager() == null) return;
        ChestArua chestArua = instance.getModuleManager().getModule(ChestArua.class);
        if (chestArua != null && chestArua.shouldBlockOtherInteraction()) {
            cir.setReturnValue(ActionResult.PASS);
        }
    }

    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
    private void attackEntity(PlayerEntity player, Entity target, CallbackInfo ci) {
        if (mc.player == null || mc.world == null) return;

        Reach reach = instance.getModuleManager().getModule(Reach.class);
        if (player == mc.player && reach != null && reach.shouldBlockAttack(target)) {
            ci.cancel();
            return;
        }

        KeepSprint keepSprint = instance.getModuleManager().getModule(KeepSprint.class);
        if (keepSprint != null && keepSprint.tryBufferAttack(target)) {
            ci.cancel();
            return;
        }

        AttackEvent event = new AttackEvent(target);
        instance.getEventManager().call(event);

        if (event.isCancelled()) {
            ci.cancel();
        }
    }
}
