package injection;

import cn.remix.module.impl.render.AntiDebuff;
import cn.remix.module.impl.render.NoFog;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldRenderer.class)
public class MixinWorldRenderer {

    @Inject(method = "hasBlindnessOrDarkness", at = @At("HEAD"), cancellable = true)
    private void hasBlindnessOrDarkness(Camera camera, CallbackInfoReturnable<Boolean> cir) {
        if (AntiDebuff.isActive()) {
            cir.setReturnValue(false);
        }
    }

    @Redirect(
            method = "renderBlockLayers",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/chunk/ChunkBuilder$BuiltChunk;method_76298(J)F")
    )
    private float removeChunkFog(ChunkBuilder.BuiltChunk chunk, long time) {
        // Terrain mixes newly visible sections toward FogColor before applying the normal fog UBO.
        return NoFog.isActive() ? 1.0F : chunk.method_76298(time);
    }
}
