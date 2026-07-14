package injection;

import cn.remix.event.impl.RenderRotationEvent;
import cn.remix.module.impl.render.Chams;
import cn.remix.util.IMinecraft;
import cn.remix.util.render.LivingEntityRenderStateExtension;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.spongepowered.asm.mixin.injection.ModifyArgs;

@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer<T extends LivingEntity, S extends LivingEntityRenderState> implements IMinecraft {
    @Shadow
    public abstract Identifier getTexture(S state);

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V", at = @At("HEAD"))
    private void entity(T livingEntity, S livingEntityRenderState, float f, CallbackInfo ci) {
        RenderRotationEvent.currentEntity = livingEntity;
        ((LivingEntityRenderStateExtension) livingEntityRenderState).remix$setEntity(livingEntity);
    }

    @Inject(method = "getRenderLayer", at = @At("HEAD"), cancellable = true)
    private void getChamsRenderLayer(S state, boolean showBody, boolean translucent, boolean showOutline,
                                     CallbackInfoReturnable<RenderLayer> cir) {
        Chams chams = getChams(state);
        if (chams != null) {
            cir.setReturnValue(chams.getRenderLayer(this.getTexture(state)));
        }
    }

    @ModifyArgs(
            method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IIILnet/minecraft/client/texture/Sprite;ILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V")
    )
    private void applyChamsColor(Args args) {
        S state = args.get(1);
        Chams chams = getChams(state);
        if (chams == null) return;

        LivingEntity entity = ((LivingEntityRenderStateExtension) state).remix$getEntity();
        args.set(6, chams.getEntityColor(entity));
    }

    @Inject(method = "shouldRenderFeatures", at = @At("HEAD"), cancellable = true)
    private void hideFeaturesForFlatChams(S state, CallbackInfoReturnable<Boolean> cir) {
        Chams chams = getChams(state);
        if (chams != null && chams.getRenderMode().is("Flat")) {
            cir.setReturnValue(false);
        }
    }

    @Redirect(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;lerpAngleDegrees(FFF)F", ordinal = 0))
    private float yaw(float delta, float start, float end) {
        RenderRotationEvent event = new RenderRotationEvent(new float[]{end, 0}, new float[]{start, 0});
        if (RenderRotationEvent.currentEntity == mc.player) {
            instance.getEventManager().call(event);
        }

        return MathHelper.lerpAngleDegrees(delta, event.getLastRotation()[0], event.getRotation()[0]);
    }

    @Redirect(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getLerpedPitch(F)F"))
    private float pitch(LivingEntity entity, float delta) {
        RenderRotationEvent event = new RenderRotationEvent(new float[]{0, entity.getPitch()}, new float[]{0, entity.lastPitch});
        if (entity == mc.player) {
            instance.getEventManager().call(event);
        }

        return MathHelper.lerp(delta, event.getLastRotation()[1], event.getRotation()[1]);
    }

    private Chams getChams(S state) {
        Chams chams = Chams.getActive();
        if (chams == null) return null;

        LivingEntity entity = ((LivingEntityRenderStateExtension) state).remix$getEntity();
        return chams.shouldRender(entity) ? chams : null;
    }
}
