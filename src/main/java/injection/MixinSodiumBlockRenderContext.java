package injection;

import cn.remix.module.impl.render.Xray;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sodium replaces the vanilla block model renderer and section builder. Keep the
 * Xray visibility and block-discovery hooks on Sodium's equivalent render context.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext", remap = false)
public abstract class MixinSodiumBlockRenderContext {

    @Shadow(remap = false)
    protected BlockRenderView level;

    @Shadow(remap = false)
    protected BlockState state;

    @Shadow(remap = false)
    protected BlockPos pos;

    @Inject(method = "prepareCulling", at = @At("HEAD"), remap = false)
    private void trackXrayBlock(boolean enabled, CallbackInfo ci) {
        Xray xray = Xray.getActive();
        if (xray != null && state != null && pos != null && level != null) {
            xray.trackBlock(level, pos, state);
        }
    }

    @Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true, remap = false)
    private void showXrayBlockSides(Direction side, CallbackInfoReturnable<Boolean> cir) {
        Xray xray = Xray.getActive();
        if (xray != null
                && xray.isFullMode()
                && state != null
                && pos != null
                && level != null
                && xray.shouldRenderSide(state.getBlock())
                && xray.checkBlock(level, pos)) {
            cir.setReturnValue(true);
        }
    }
}
