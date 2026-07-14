package injection;

import cn.remix.module.impl.render.Xray;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.VertexSorter;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.chunk.BlockBufferAllocatorStorage;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.client.render.chunk.SectionBuilder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SectionBuilder.class)
public abstract class MixinSectionBuilder {

    @Inject(
            method = "build",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;isOpaqueFullCube()Z")
    )
    private void trackXrayBlocks(ChunkSectionPos sectionPos, ChunkRendererRegion renderRegion,
                                 VertexSorter vertexSorter, BlockBufferAllocatorStorage allocatorStorage,
                                 CallbackInfoReturnable<SectionBuilder.RenderData> cir,
                                 @Local(ordinal = 2) BlockPos pos, @Local BlockState state) {
        Xray xray = Xray.getActive();
        if (xray != null && xray.isXrayBlock(state.getBlock())) {
            xray.trackBlock(renderRegion, pos, state);
        }
    }
}
