package injection;

import cn.remix.Client;
import cn.remix.event.BlockCollisionEventGuard;
import cn.remix.event.impl.BlockCollisionEvent;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockCollisionSpliterator;
import net.minecraft.world.CollisionView;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockCollisionSpliterator.class)
public abstract class MixinBlockCollisionSpliterator {
    @Shadow @Final
    private BlockPos.Mutable pos;

    @Shadow @Final
    private ShapeContext context;

    @Shadow @Final
    private CollisionView world;

    @Unique
    private BlockState remix$cachedState;
    @Unique
    private BlockPos remix$cachedPos;
    @Unique
    private boolean remix$cachedHasShape;

    @ModifyExpressionValue(
            method = "computeNext",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;shouldSuffocate(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;)Z")
    )
    private boolean keepDynamicShapeForSuffocation(boolean original, @Local BlockState state) {
        return original || hasDynamicCollisionShape(state);
    }

    @ModifyExpressionValue(
            method = "computeNext",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;exceedsCube()Z")
    )
    private boolean keepDynamicShapeAtEdge(boolean original, @Local BlockState state) {
        return original || hasDynamicCollisionShape(state);
    }

    @ModifyExpressionValue(
            method = "computeNext",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;isOf(Lnet/minecraft/block/Block;)Z")
    )
    private boolean keepDynamicShapeAtCorner(boolean original, @Local BlockState state) {
        return original || hasDynamicCollisionShape(state);
    }

    @Unique
    private boolean hasDynamicCollisionShape(BlockState state) {
        if (Client.instance == null
                || Client.instance.getEventManager() == null
                || !Client.instance.getEventManager().hasListeners(BlockCollisionEvent.class)) {
            return false;
        }

        if (state == remix$cachedState && remix$cachedPos != null && remix$cachedPos.equals(pos)) {
            return remix$cachedHasShape;
        }

        VoxelShape originalShape = BlockCollisionEventGuard.getOriginalShape(context, state, world, pos);
        BlockCollisionEvent event = new BlockCollisionEvent(state, pos.toImmutable(), originalShape);
        Client.instance.getEventManager().call(event);

        remix$cachedState = state;
        remix$cachedPos = pos.toImmutable();
        remix$cachedHasShape = !event.isCancelled()
                && event.getShape() != null
                && event.getShape() != originalShape
                && !event.getShape().isEmpty();
        return remix$cachedHasShape;
    }
}
