package cn.omix.util.player;

import cn.omix.util.IMinecraft;
import net.minecraft.block.BlockState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;

/** Shared LookTP-style block selection and landing-position resolution. */
public final class TeleportTargetUtil implements IMinecraft {
    private static final double RAYCAST_DISTANCE = 10_000.0;

    private TeleportTargetUtil() {
    }

    public static BlockHitResult raycastTarget() {
        if (mc.player == null || mc.world == null) return null;

        Vec3d eyePosition = mc.player.getEyePos();
        Vec3d targetPosition = eyePosition.add(mc.player.getRotationVec(0.1F).multiply(RAYCAST_DISTANCE));
        return mc.world.raycast(new RaycastContext(
                eyePosition, targetPosition, RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE, mc.player
        ));
    }

    public static Vec3d getTargetPosition(BlockHitResult hit, boolean alwaysTop) {
        if (mc.world == null || hit == null || hit.getType() != HitResult.Type.BLOCK
                || mc.world.getBlockState(hit.getBlockPos()).isAir()) {
            return null;
        }
        if (!alwaysTop) return hit.getPos();

        BlockPos blockPos = hit.getBlockPos();
        BlockState blockState = mc.world.getBlockState(blockPos);
        VoxelShape collisionShape = blockState.getCollisionShape(mc.world, blockPos);
        double topOffset = collisionShape.isEmpty() ? 1.0 : collisionShape.getMax(Direction.Axis.Y);

        BlockPos spaceAbove = blockPos.up();
        if (hasTwoFreeBlocks(spaceAbove)) {
            return new Vec3d(blockPos.getX() + 0.5, blockPos.getY() + topOffset, blockPos.getZ() + 0.5);
        }

        int highestFeetY = mc.world.getTopYInclusive() - 1;
        for (BlockPos candidate = spaceAbove.up(); candidate.getY() <= highestFeetY; candidate = candidate.up()) {
            if (hasTwoFreeBlocks(candidate)) {
                return new Vec3d(blockPos.getX() + 0.5, candidate.getY(), blockPos.getZ() + 0.5);
            }
        }
        return null;
    }

    private static boolean hasTwoFreeBlocks(BlockPos feetPosition) {
        return isFree(feetPosition) && isFree(feetPosition.up());
    }

    private static boolean isFree(BlockPos position) {
        BlockState state = mc.world.getBlockState(position);
        return state.getCollisionShape(mc.world, position).isEmpty();
    }
}
