package injection;

import cn.omix.event.impl.JumpEvent;
import cn.omix.event.impl.MoveMathEvent;
import cn.omix.event.impl.RenderRotationEvent;
import cn.omix.module.impl.render.AntiDebuff;
import cn.omix.module.impl.render.Animation;
import cn.omix.util.IMinecraft;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity implements IMinecraft {
    @Inject(method = "getEffectFadeFactor", at = @At("HEAD"), cancellable = true)
    private void getEffectFadeFactor(RegistryEntry<StatusEffect> effect, float tickDelta,
                                     CallbackInfoReturnable<Float> cir) {
        if (AntiDebuff.suppresses(effect)) {
            cir.setReturnValue(0.0F);
        }
    }

    @Inject(method = "getHandSwingDuration", at = @At("HEAD"), cancellable = true)
    public void getHandSwingDuration(CallbackInfoReturnable<Integer> cir) {
        Animation animation = instance.getModuleManager().getModule(Animation.class);

        if (animation.isEnabled()) {
            cir.setReturnValue(6 + animation.swingSpeed.getValue().intValue());
        }
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void travel(Vec3d movementInput, CallbackInfo ci) {
        if (mc.player == null || mc.world == null) return;

        if (((Object) this) instanceof ClientPlayerEntity) {
            MoveMathEvent event = new MoveMathEvent();
            instance.getEventManager().call(event);

            if (event.isCancelled()) {
                ci.cancel();
            }
        }
    }

    @ModifyVariable(method = "jump", at = @At("STORE"), index = 3)
    private float modifyJumpYaw(float yawRadians) {
        if (mc.player == null || mc.world == null || (Object) this != mc.player) return yawRadians;

        // Modify the computed sprint-jump angle instead of redirecting getYaw().
        // Baritone redirects that same call to apply its pathing rotation, so using
        // the local value lets both transformations compose without a Mixin conflict.
        JumpEvent event = new JumpEvent((float) Math.toDegrees(yawRadians));
        instance.getEventManager().call(event);
        return (float) Math.toRadians(event.getYaw());
    }

    @Redirect(method = "turnHead", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getYaw()F"))
    private float turnHead(LivingEntity entity) {
        if (entity == mc.player) {
            RenderRotationEvent event = new RenderRotationEvent(new float[]{entity.getYaw(), 0}, new float[]{0, 0});
            instance.getEventManager().call(event);
            return event.getRotation()[0];
        }

        return entity.getYaw();
    }
}
