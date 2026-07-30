package injection;

import cn.omix.module.impl.render.Xray;
import net.fabricmc.fabric.api.util.TriState;
import net.fabricmc.fabric.impl.client.indigo.renderer.render.BlockRenderInfo;
import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockRenderInfo.class, remap = false)
public abstract class MixinIndigoBlockRenderInfo {

    @Inject(method = "effectiveAo", at = @At("HEAD"), cancellable = true)
    private void forceAmbientOcclusion(TriState aoMode, CallbackInfoReturnable<Boolean> cir) {
        if (Xray.getActive() != null) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "effectiveRenderLayer", at = @At("HEAD"), cancellable = true)
    private void applyXrayLayer(BlockRenderLayer quadLayer, CallbackInfoReturnable<BlockRenderLayer> cir) {
        Xray xray = Xray.getActive();
        BlockRenderInfo self = (BlockRenderInfo) (Object) this;
        if (xray != null && xray.shouldMakeTranslucent(self.blockState.getBlock())) {
            cir.setReturnValue(BlockRenderLayer.TRANSLUCENT);
        }
    }

    @Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true)
    private void shouldDrawSide(Direction side, CallbackInfoReturnable<Boolean> cir) {
        Xray xray = Xray.getActive();
        BlockRenderInfo self = (BlockRenderInfo) (Object) this;
        if (side != null
                && xray != null
                && xray.isFullMode()
                && xray.shouldRenderSide(self.blockState.getBlock())
                && xray.checkBlock(self.blockView, self.blockPos)) {
            cir.setReturnValue(true);
        }
    }
}
