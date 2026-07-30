package injection;

import cn.omix.module.impl.render.Xray;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockModelRenderer.class)
public abstract class MixinBlockModelRenderer {

    @Inject(method = "shouldDrawFace", at = @At("HEAD"), cancellable = true)
    private static void shouldDrawFace(BlockRenderView world, BlockState state, boolean cull, Direction side,
                                       BlockPos neighborPos, CallbackInfoReturnable<Boolean> cir) {
        Xray xray = Xray.getActive();
        BlockPos pos = neighborPos.offset(side.getOpposite());
        if (xray != null
                && xray.isFullMode()
                && xray.shouldRenderSide(state.getBlock())
                && xray.checkBlock(world, pos)) {
            cir.setReturnValue(true);
        }
    }

    @ModifyVariable(method = "render", at = @At("STORE"), index = 9)
    private boolean forceAmbientOcclusion(boolean original) {
        return Xray.getActive() != null || original;
    }

    @ModifyArg(
            method = "renderQuad",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/VertexConsumer;quad(Lnet/minecraft/client/util/math/MatrixStack$Entry;Lnet/minecraft/client/render/model/BakedQuad;[FFFFF[II)V"
            ),
            index = 6
    )
    private float applyXrayOpacity(float original) {
        Xray xray = Xray.getActive();
        return xray != null ? xray.getTerrainOpacity() : original;
    }
}
