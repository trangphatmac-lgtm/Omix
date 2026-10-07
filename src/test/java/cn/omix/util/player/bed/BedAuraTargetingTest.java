package cn.omix.util.player.bed;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BedAuraTargetingTest {
    @Test void outsideSphereDoesNotHideAnotherReachableBed() {
        World world = new World();
        world.bed(new BlockPos(-4, 0, -4), Direction.WEST);
        BlockPos reachable = new BlockPos(2, 0, 0);
        world.bed(reachable, Direction.EAST);
        var target = BedAuraTargeting.find(world, new Vec3d(.5, .5, .5), 4.5, false, null);
        assertEquals(reachable, target.bed());
        assertEquals(reachable, target.block());
    }

    @Test void fractionalRangeIncludesEdgeCellsAndRetainsCurrentBed() {
        World world = new World();
        BlockPos bed = new BlockPos(5, 0, 0);
        world.bed(bed, Direction.EAST);
        Vec3d player = new Vec3d(.9, .5, .5);
        assertEquals(bed, BedAuraTargeting.find(world, player, 5, false, null).bed());
        world.bed(new BlockPos(1, 0, 0), Direction.NORTH);
        assertEquals(bed, BedAuraTargeting.find(world, player, 5, false, bed).bed());
        world.beds.remove(bed);
        assertNotEquals(bed, BedAuraTargeting.find(world, player, 5, false, bed).bed());
    }

    @Test void coveredBedSelectsActualRoofThenBedAfterRoofRemoval() {
        World world = new World();
        BlockPos bed = BlockPos.ORIGIN;
        world.bed(bed, Direction.EAST);
        world.cover(bed);
        Vec3d player = new Vec3d(.5, .5, -.5);
        var roof = BedAuraTargeting.find(world, player, 5, true, bed);
        assertEquals(bed.up(), roof.block());
        world.solid.remove(bed.up());
        assertEquals(bed, BedAuraTargeting.find(world, player, 5, true, bed).block());
    }

    @Test void openingBesideEitherHalfAllowsDirectBreaking() {
        World world = new World();
        BlockPos bed = BlockPos.ORIGIN;
        world.bed(bed, Direction.EAST);
        world.cover(bed);
        world.solid.remove(bed.east().south());
        assertEquals(bed, BedAuraTargeting.find(world, new Vec3d(.5, .5, .5), 5, true, bed).block());
    }

    @Test void roofMustAlsoBeInRangeAndBreakable() {
        World world = new World();
        BlockPos bed = BlockPos.ORIGIN;
        world.bed(bed, Direction.EAST);
        world.cover(bed);
        assertNull(BedAuraTargeting.find(world, new Vec3d(.5, -.5, .5), 1, true, bed));
        world.unbreakable.add(bed.up());
        world.unbreakable.add(bed.east().up());
        assertNull(BedAuraTargeting.find(world, new Vec3d(.5, .5, .5), 5, true, bed));
        assertEquals(bed, BedAuraTargeting.find(world, new Vec3d(.5, .5, .5), 5, false, bed).block());
    }

    private static final class World implements BedAuraTargeting.World {
        final Map<BlockPos, Direction> beds = new HashMap<>();
        final Set<BlockPos> solid = new HashSet<>();
        final Set<BlockPos> unbreakable = new HashSet<>();

        void bed(BlockPos pos, Direction facing) {
            beds.put(pos, facing);
            beds.put(pos.offset(facing), facing.getOpposite());
            solid.addAll(beds.keySet());
        }

        void cover(BlockPos pos) {
            for (BlockPos half : new BlockPos[]{pos, pos.offset(beds.get(pos))}) {
                solid.add(half.up());
                for (Direction direction : Direction.Type.HORIZONTAL) solid.add(half.offset(direction));
            }
        }

        public Direction partner(BlockPos pos) { return beds.get(pos); }
        public boolean air(BlockPos pos) { return !solid.contains(pos); }
        public boolean breakable(BlockPos pos) { return !unbreakable.contains(pos); }
    }
}
