package injection;

import cn.omix.Client;
import cn.omix.event.BlockCollisionEventGuard;
import cn.omix.event.impl.BlockCollisionEvent;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class MixinAbstractBlockState {
    @Shadow
    protected abstract BlockState asBlockState();

    @Inject(
            method = "getCollisionShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/ShapeContext;)Lnet/minecraft/util/shape/VoxelShape;",
            at = @At("RETURN"),
            cancellable = true
    )
    private void onGetCollisionShape(BlockView world, BlockPos pos, ShapeContext context,
                                     CallbackInfoReturnable<VoxelShape> cir) {
        if (Client.instance == null
                || Client.instance.getEventManager() == null
                || BlockCollisionEventGuard.isBypassingEvent()
                || !Client.instance.getEventManager().hasListeners(BlockCollisionEvent.class)) {
            return;
        }

        BlockCollisionEvent event = new BlockCollisionEvent(asBlockState(), pos.toImmutable(), cir.getReturnValue());
        Client.instance.getEventManager().call(event);
        cir.setReturnValue(event.isCancelled() || event.getShape() == null
                ? VoxelShapes.empty()
                : event.getShape());
    }
}
