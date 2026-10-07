package cn.omix.util.player.bed;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** World-independent bed/defense selection, shared by the module and regression tests. */
public final class BedAuraTargeting {
    public interface World {
        /** Direction from this bed half to its partner, or null for a non-bed. */
        Direction partner(BlockPos pos);
        boolean air(BlockPos pos);
        boolean breakable(BlockPos pos);
    }

    public record Target(BlockPos bed, BlockPos block) {}

    private BedAuraTargeting() {}

    public static Target find(World world, Vec3d player, double range, boolean surrounding, BlockPos previousBed) {
        // Keep a valid bed stable instead of restarting whenever the scan order changes.
        Target previous = target(world, player, range, surrounding, previousBed);
        if (previous != null) return previous;
        BlockPos origin = BlockPos.ofFloored(player);
        int radius = (int) Math.ceil(range);
        Target nearest = null;
        double distance = Double.POSITIVE_INFINITY;
        for (BlockPos pos : BlockPos.iterate(origin.add(-radius, -radius, -radius), origin.add(radius, radius, radius))) {
            Target candidate = target(world, player, range, surrounding, pos);
            if (candidate == null) continue;
            double squared = player.squaredDistanceTo(candidate.block().toCenterPos());
            if (squared < distance) {
                distance = squared;
                nearest = candidate;
            }
        }
        return nearest;
    }

    private static Target target(World world, Vec3d player, double range, boolean surrounding, BlockPos bed) {
        if (bed == null || !inRange(player, bed, range)) return null;
        Direction partner = world.partner(bed);
        if (partner == null) return null;
        BlockPos block = bed;
        if (surrounding) {
            boolean exposed = false;
            for (Direction side : Direction.Type.HORIZONTAL) {
                if (world.air(bed.offset(side)) || world.air(bed.offset(partner).offset(side))) {
                    exposed = true;
                    break;
                }
            }
            if (!exposed && !world.air(bed.up())) block = bed.up();
        }
        if (!inRange(player, block, range) || !world.breakable(block)) return null;
        return new Target(bed.toImmutable(), block.toImmutable());
    }

    public static boolean inRange(Vec3d player, BlockPos pos, double range) {
        return player.squaredDistanceTo(pos.toCenterPos()) <= range * range;
    }
}
