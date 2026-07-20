package injection;

import cn.remix.event.impl.Render3DEvent;
import cn.remix.module.impl.player.Freecam;
import cn.remix.module.impl.render.NoHurtCam;
import cn.remix.module.impl.render.Zoom;
import cn.remix.util.IMinecraft;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer implements IMinecraft {

    @Shadow
    public abstract Camera getCamera();

    @Shadow
    @Final
    private BufferBuilderStorage buffers;

    @Inject(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/util/memory/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V", shift = At.Shift.AFTER))
    private void renderWorld(RenderTickCounter renderTickCounter, CallbackInfo ci, @Local(ordinal = 0) Matrix4f projectionMatrix, @Local(ordinal = 1) Matrix4f modelViewMatrix) {
        MatrixStack matrixStack = new MatrixStack();
        matrixStack.peek().getPositionMatrix().set(modelViewMatrix);

        VertexConsumerProvider.Immediate consumers = this.buffers.getEntityVertexConsumers();
        Render3DEvent event = new Render3DEvent(matrixStack, consumers, renderTickCounter.getTickProgress(true), projectionMatrix, modelViewMatrix);
        instance.getEventManager().call(event);
        consumers.draw();
    }

    @Inject(at = @At("HEAD"), method = "tiltViewWhenHurt(Lnet/minecraft/client/util/math/MatrixStack;F)V", cancellable = true)
    private void tiltViewWhenHurt(MatrixStack matrices, float tickProgress, CallbackInfo ci) {
        if (instance.getModuleManager().getModule(NoHurtCam.class).isEnabled()) {
            ci.cancel();
        }
    }

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void getFov(Camera camera, float tickProgress, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        Zoom zoom = Zoom.getInstance();
        if (zoom != null) {
            cir.setReturnValue(zoom.applyFov(cir.getReturnValue()));
        }
    }

    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void renderHand(float tickProgress, boolean sleeping, Matrix4f positionMatrix, CallbackInfo ci) {
        Zoom zoom = Zoom.getInstance();
        if (zoom != null && zoom.shouldHideHand()) {
            ci.cancel();
        }
    }

    @Inject(at = @At("HEAD"), method = "bobView(Lnet/minecraft/client/util/math/MatrixStack;F)V", cancellable = true)
    private void remix$disableFreecamBobbing(MatrixStack matrices, float tickProgress, CallbackInfo ci) {
        if (Freecam.isActive()) {
            ci.cancel();
        }
    }
}
