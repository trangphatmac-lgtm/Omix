package injection;

import cn.remix.module.impl.render.Xray;
import net.fabricmc.fabric.impl.client.indigo.renderer.mesh.MutableQuadViewImpl;
import net.fabricmc.fabric.impl.client.indigo.renderer.render.AbstractTerrainRenderContext;
import net.minecraft.util.math.ColorHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AbstractTerrainRenderContext.class, remap = false)
public abstract class MixinIndigoTerrainRenderContext {

    @Inject(
            method = "bufferQuad",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/fabricmc/fabric/impl/client/indigo/renderer/render/AbstractTerrainRenderContext;shadeQuad(Lnet/fabricmc/fabric/impl/client/indigo/renderer/mesh/MutableQuadViewImpl;ZZZ)V",
                    shift = At.Shift.AFTER,
                    remap = false
            )
    )
    private void applyXrayOpacity(MutableQuadViewImpl quad, CallbackInfo ci) {
        Xray xray = Xray.getActive();
        if (xray == null) return;

        int alpha = (int) (xray.getTerrainOpacity() * 255.0F);
        for (int i = 0; i < 4; i++) {
            quad.color(i, ColorHelper.withAlpha(alpha, quad.color(i)));
        }
    }
}
