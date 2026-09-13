package injection;

import cn.omix.event.impl.AttackEvent;
import cn.omix.module.impl.move.KeepSprint;
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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class MixinClientPlayerInteractionManager implements IMinecraft {

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
