package cn.omix.util.world.bed;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Keeps the bed anchor separate from the defense currently being mined. */
public final class BedBreakerTargeting {
    public enum Mode { Instant, Hypixel, Legit }
    public interface World {
        Direction partner(BlockPos bed);
        int bedColor(BlockPos bed);
        boolean air(BlockPos pos);
        boolean fullCube(BlockPos pos);
        float breakingDelta(BlockPos pos);
        BlockPos raycast(BlockPos bed);
    }

    private static final Direction[] DEFENSE_SIDES = {
            Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    private BedBreakerTargeting() { }

    public static boolean inRange(Vec3d player, BlockPos pos, double range) {
        return player.squaredDistanceTo(pos.toCenterPos()) <= range * range;
    }

    public static BlockPos findBed(World world, Vec3d player, double range, Mode mode,
                                  int teamColor, Predicate<BlockPos> recentlyBroken) {
        int radius = (int) (range + 3);
        BlockPos origin = BlockPos.ofFloored(player);
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (BlockPos cursor : BlockPos.iterate(origin.add(-radius, -radius, -radius), origin.add(radius, radius, radius))) {
            if (world.partner(cursor) == null || recentlyBroken.test(cursor)
                    || BedBreakerTeams.sameTeam(world.bedColor(cursor), teamColor) || !inRange(player, cursor, range)) continue;
            if (mode == Mode.Instant && !hittable(world, cursor)) continue;
            double distance = player.squaredDistanceTo(cursor.toCenterPos());
            if (distance < nearestDistance) { nearestDistance = distance; nearest = cursor.toImmutable(); }
        }
        return nearest;
    }

    public static BlockPos selectBlock(World world, Vec3d player, double range, Mode mode, BlockPos bed) {
        if (world.partner(bed) == null || !inRange(player, bed, range)) return null;
        if (mode == Mode.Instant) return breakable(world, player, range, bed) ? bed : null;
        if (mode == Mode.Legit) {
            BlockPos hit = world.raycast(bed);
            return hit != null && breakable(world, player, range, hit) ? hit : null;
        }

        Set<BlockPos> defenses = new LinkedHashSet<>();
        for (BlockPos part : new BlockPos[]{bed, bed.offset(world.partner(bed))}) {
            for (Direction side : DEFENSE_SIDES) {
                BlockPos adjacent = part.offset(side);
                if (world.air(adjacent)) return breakable(world, player, range, bed) ? bed : null;
                if (world.partner(adjacent) == null) defenses.add(adjacent);
            }
        }
        BlockPos best = null;
        double bestTime = Double.MAX_VALUE;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos defense : defenses) {
            if (!breakable(world, player, range, defense)) continue;
            double time = 1.0 / world.breakingDelta(defense);
            double distance = player.squaredDistanceTo(defense.toCenterPos());
            if (time < bestTime || Math.abs(time - bestTime) < 1.0E-5 && distance < bestDistance) {
                best = defense;
                bestTime = time;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static boolean breakable(World world, Vec3d player, double range, BlockPos pos) {
        return !world.air(pos) && inRange(player, pos, range) && world.breakingDelta(pos) > 0;
    }

    public static boolean hittable(World world, BlockPos bed) {
        for (Direction side : Direction.values()) if (!world.fullCube(bed.offset(side))) return true;
        return false;
    }

    public static final class RecentBeds {
        private final Map<BlockPos, Long> expires = new HashMap<>();

        public void mark(BlockPos bed, Direction partner, long now) {
            expires.put(bed.toImmutable(), now + 1000);
            if (partner != null) expires.put(bed.offset(partner), now + 1000);
        }

        public boolean contains(BlockPos pos, long now) {
            Long end = expires.get(pos);
            return end != null && now < end;
        }

        public void prune(long now) { expires.values().removeIf(end -> now >= end); }
        public void clear() { expires.clear(); }
    }
}
