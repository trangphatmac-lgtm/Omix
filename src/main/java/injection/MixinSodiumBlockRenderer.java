package injection;

import cn.remix.module.impl.render.Xray;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies Xray's translucent pass and opacity to Sodium terrain vertices. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer", remap = false)
public abstract class MixinSodiumBlockRenderer {

    @ModifyArg(
            method = "bufferQuad",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/api/util/ColorARGB;toABGR(I)I",
                    remap = false
            ),
            index = 0,
            remap = false
    )
    private int applyXrayOpacity(int color) {
        Xray xray = Xray.getActive();
        if (xray == null) return color;

        int alpha = Math.round(xray.getTerrainOpacity() * 255.0F);
        return color & 0x00FFFFFF | alpha << 24;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Inject(method = "attemptPassDowngrade", at = @At("HEAD"), cancellable = true, remap = false)
    private void keepTranslucentPass(CallbackInfoReturnable cir) {
        if (Xray.getActive() != null) {
            // Sodium otherwise downgrades opaque block textures from translucent
            // to cutout, which makes the Xray alpha ineffective.
            cir.setReturnValue(null);
        }
    }
}
