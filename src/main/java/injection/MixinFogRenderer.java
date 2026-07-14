package injection;

import cn.remix.module.impl.render.NoFog;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.render.fog.FogRenderer;
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
}
