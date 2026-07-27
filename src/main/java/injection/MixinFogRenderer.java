package injection;

import cn.omix.module.impl.render.NoFog;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.FogData;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public abstract class MixinFogRenderer {
    @Shadow
    @Final
    private GpuBuffer emptyBuffer;

    @Inject(method = "getFogBuffer", at = @At("HEAD"), cancellable = true)
    private void removeFog(FogRenderer.FogType fogType, CallbackInfoReturnable<GpuBufferSlice> cir) {
        if (NoFog.isActive()) {
            cir.setReturnValue(emptyBuffer.slice(0L, FogRenderer.FOG_UBO_SIZE));
        }
    }

    @Inject(
            method = "applyFog(Lnet/minecraft/client/render/Camera;ILnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderSystem;getDevice()Lcom/mojang/blaze3d/systems/GpuDevice;"
            )
    )
    private void removeTerrainFog(Camera camera, int viewDistance, RenderTickCounter tickCounter,
                                  float skyDarkness, ClientWorld world,
                                  CallbackInfoReturnable<Vector4f> cir, @Local FogData fogData) {
        if (!NoFog.isActive()) return;

        // Sodium reads FogData directly instead of the vanilla fog UBO. Updating the shared
        // source values keeps both renderers fog-free without targeting Sodium-replaced methods.
        fogData.environmentalStart = Float.MAX_VALUE;
        fogData.environmentalEnd = Float.MAX_VALUE;
        fogData.renderDistanceStart = Float.MAX_VALUE;
        fogData.renderDistanceEnd = Float.MAX_VALUE;
        fogData.skyEnd = Float.MAX_VALUE;
        fogData.cloudEnd = Float.MAX_VALUE;
    }
}
