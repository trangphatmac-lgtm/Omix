package cn.remix.event;

import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.CollisionView;

public final class BlockCollisionEventGuard {
    private static final ThreadLocal<Boolean> BYPASS_EVENT = ThreadLocal.withInitial(() -> false);

    private BlockCollisionEventGuard() {
    }

    public static boolean isBypassingEvent() {
        return BYPASS_EVENT.get();
    }

    public static VoxelShape getOriginalShape(ShapeContext context, BlockState state,
                                              CollisionView world, BlockPos pos) {
        boolean previous = BYPASS_EVENT.get();
        BYPASS_EVENT.set(true);
        try {
            return context.getCollisionShape(state, world, pos);
        } finally {
            BYPASS_EVENT.set(previous);
        }
    }
}
