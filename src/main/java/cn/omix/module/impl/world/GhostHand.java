package cn.omix.module.impl.world;

import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.module.value.impl.NumberValue;
import net.minecraft.block.AbstractSignBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BellBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.CakeBlock;
import net.minecraft.block.ComposterBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.NoteBlock;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;

public final class GhostHand extends Module {
    private final BoolValue throughWall = new BoolValue("Through Wall", false);
    private final NumberValue distance = new NumberValue("Distance", 4.5F, 4.5F, 6.0F, 0.1F);

    public GhostHand() {
        super("GhostHand", Category.World);
    }

    public boolean canReachThroughWalls() {
        return isEnabled() && throughWall.getValue();
    }

    public double getInteractionDistance(double vanillaDistance) {
        return isEnabled() ? Math.max(vanillaDistance, distance.getValue()) : vanillaDistance;
    }

    public BlockHitResult findThroughWallTarget(Entity cameraEntity, float tickProgress) {
        if (!canReachThroughWalls() || mc.world == null) return null;

        Vec3d start = cameraEntity.getCameraPosVec(tickProgress);
        double interactionDistance = mc.player == null
                ? getInteractionDistance(4.5)
                : mc.player.getBlockInteractionRange();
        Vec3d end = start.add(cameraEntity.getRotationVec(tickProgress).multiply(interactionDistance));
        RaycastContext context = new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                cameraEntity
        ) {
            @Override
            public VoxelShape getBlockShape(BlockState state, BlockView world, BlockPos pos) {
                return isInteractable(state, pos)
                        ? state.getOutlineShape(world, pos)
                        : VoxelShapes.empty();
            }
        };

        BlockHitResult result = mc.world.raycast(context);
        return result.getType() == BlockHitResult.Type.BLOCK ? result : null;
    }

    public boolean isInteractable(BlockState state, BlockPos pos) {
        if (mc.world == null || state.isAir()) return false;

        if (state.hasBlockEntity() || state.createScreenHandlerFactory(mc.world, pos) != null) {
            return true;
        }

        return state.getBlock() instanceof AbstractSignBlock
                || state.getBlock() instanceof BedBlock
                || state.getBlock() instanceof BellBlock
                || state.getBlock() instanceof ButtonBlock
                || state.getBlock() instanceof CakeBlock
                || state.getBlock() instanceof ComposterBlock
                || state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof FenceGateBlock
                || state.getBlock() instanceof LeverBlock
                || state.getBlock() instanceof NoteBlock
                || state.getBlock() instanceof RespawnAnchorBlock
                || state.getBlock() instanceof TrapdoorBlock;
    }
}
