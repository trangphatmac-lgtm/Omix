package injection;

import cn.remix.module.impl.render.Xray;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.client.render.BlockRenderLayers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockRenderLayers.class)
public abstract class MixinBlockRenderLayers {

    @Inject(method = "getBlockLayer", at = @At("HEAD"), cancellable = true)
    private static void getBlockLayer(BlockState state, CallbackInfoReturnable<BlockRenderLayer> cir) {
        Xray xray = Xray.getActive();
        if (xray != null && xray.shouldMakeTranslucent(state.getBlock())) {
            cir.setReturnValue(BlockRenderLayer.TRANSLUCENT);
        }
    }
}
