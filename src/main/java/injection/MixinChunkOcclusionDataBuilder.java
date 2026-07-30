package injection;

import cn.omix.module.impl.player.Freecam;
import cn.omix.module.impl.render.Xray;
import net.minecraft.client.render.chunk.ChunkOcclusionData;
import net.minecraft.client.render.chunk.ChunkOcclusionDataBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkOcclusionDataBuilder.class)
public abstract class MixinChunkOcclusionDataBuilder {

    @Inject(method = "build", at = @At("HEAD"), cancellable = true)
    private void build(CallbackInfoReturnable<ChunkOcclusionData> cir) {
        if (Xray.getActive() == null && !Freecam.isActive()) return;

        ChunkOcclusionData data = new ChunkOcclusionData();
        data.fill(true);
        cir.setReturnValue(data);
    }
}
