package cn.omix.util.world.bed;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static cn.omix.util.world.bed.BedBreakerTargeting.Mode.*;
import static org.junit.jupiter.api.Assertions.*;

class BedBreakerTargetingTest {
    private final BlockPos bed = new BlockPos(2, 0, 0);
    private final Vec3d player = new Vec3d(.5, .5, .5);
    private final World world = new World();

    @Test void findsNearestPartAndExcludesOwnTeamAndOutOfRangeBeds() {
        world.bed(bed, 14);
        world.bed(new BlockPos(0, 0, 3), 11);
        assertEquals(bed, find(Hypixel, -1));
        assertEquals(new BlockPos(0, 0, 3), find(Hypixel, 14));
        assertNull(BedBreakerTargeting.findBed(world, player, 1, Hypixel, -1, pos -> false));
    }

    @Test void instantMinesBedDirectlyEvenWhenDefensesEncloseThePair() {
        world.enclose(bed);
        // The partner bed is itself non-full, satisfying the original exposure check.
        assertEquals(bed, find(Instant));
        assertEquals(bed, select(Instant));
        world.blocks.remove(bed.down());
        assertEquals(bed, find(Instant));
        assertEquals(bed, select(Instant));
    }

    @Test void hypixelChecksBothPartsAndIgnoresAirBelowBed() {
        world.enclose(bed);
        world.blocks.remove(bed.down());
        assertNotEquals(bed, select(Hypixel));
        world.blocks.remove(bed.east().north());
        assertEquals(bed, select(Hypixel));
    }

    @Test void hypixelChoosesFastestDefenseIncludingOtherBedPart() {
        world.enclose(bed);
        world.blocks.put(bed.up(), .01F);
        world.blocks.put(bed.east().up(), .5F);
        assertEquals(bed.east().up(), select(Hypixel));
    }

    @Test void equalSpeedPrefersClosestDefense() {
        world.enclose(bed);
        assertEquals(bed.west(), select(Hypixel));
    }

    @Test void unbreakableAndOutOfRangeDefensesAreExcluded() {
        world.enclose(bed);
        world.blocks.replaceAll((pos, delta) -> 0F);
        world.blocks.put(bed.east().east(), 1F);
        assertNull(BedBreakerTargeting.selectBlock(world, player, 2, Hypixel, bed));
        assertEquals(bed.east().east(), select(Hypixel));
        world.blocks.put(bed.east().east(), 0F);
        assertNull(select(Hypixel));
    }

    @Test void legitSelectsFirstRaycastObstacleThenBedAfterRemoval() {
        world.bed(bed, 14);
        world.blocks.put(bed.west(), .1F);
        world.ray = bed.west();
        assertEquals(bed.west(), select(Legit));
        world.blocks.remove(bed.west());
        world.ray = bed;
        assertEquals(bed, select(Legit));
        world.ray = null;
        assertNull(select(Legit));
    }

    @Test void brokenBedSuppressesBothPartsForExactlyOneSecond() {
        world.bed(bed, 14);
        var recent = new BedBreakerTargeting.RecentBeds();
        recent.mark(bed, Direction.EAST, 500);
        assertTrue(recent.contains(bed, 1499));
        assertTrue(recent.contains(bed.east(), 1499));
        assertNull(BedBreakerTargeting.findBed(world, player, 5, Hypixel, -1, pos -> recent.contains(pos, 1499)));
        assertFalse(recent.contains(bed, 1500));
        recent.prune(1500);
        assertEquals(bed, BedBreakerTargeting.findBed(world, player, 5, Hypixel, -1, pos -> recent.contains(pos, 1500)));
        recent.mark(bed, Direction.EAST, 2000);
        recent.clear();
        assertFalse(recent.contains(bed, 2001));
    }

    @Test void recentPartsAreImmutableWhenScanCursorIsReused() {
        var recent = new BedBreakerTargeting.RecentBeds();
        var cursor = bed.mutableCopy();
        recent.mark(cursor, Direction.EAST, 0);
        cursor.set(100, 100, 100);
        assertTrue(recent.contains(bed, 1));
        assertFalse(recent.contains(cursor, 1));
    }

    @Test void rangeUsesPlayerFeetAndBlockCenter() {
        assertTrue(BedBreakerTargeting.inRange(player, bed, 2));
        assertFalse(BedBreakerTargeting.inRange(player, bed, 1.99));
    }

    private BlockPos find(BedBreakerTargeting.Mode mode) { return find(mode, -1); }
    private BlockPos find(BedBreakerTargeting.Mode mode, int team) {
        return BedBreakerTargeting.findBed(world, player, 5, mode, team, pos -> false);
    }
    private BlockPos select(BedBreakerTargeting.Mode mode) {
        return BedBreakerTargeting.selectBlock(world, player, 5, mode, bed);
    }

    private static final class World implements BedBreakerTargeting.World {
        final Map<BlockPos, Direction> beds = new HashMap<>();
        final Map<BlockPos, Integer> colors = new HashMap<>();
        final Map<BlockPos, Float> blocks = new HashMap<>();
        BlockPos ray;

        void bed(BlockPos pos, int color) {
            beds.put(pos, Direction.EAST);
            beds.put(pos.east(), Direction.WEST);
            colors.put(pos, color);
            colors.put(pos.east(), color);
        }
        void enclose(BlockPos bed) {
            bed(bed, 14);
            for (BlockPos part : new BlockPos[]{bed, bed.east()})
                for (Direction side : Direction.values())
                    if (!beds.containsKey(part.offset(side))) blocks.put(part.offset(side), .1F);
        }
        public Direction partner(BlockPos pos) { return beds.get(pos); }
        public int bedColor(BlockPos pos) { return colors.getOrDefault(pos, -1); }
        public boolean air(BlockPos pos) { return !beds.containsKey(pos) && !blocks.containsKey(pos); }
        public boolean fullCube(BlockPos pos) { return blocks.containsKey(pos); }
        public float breakingDelta(BlockPos pos) { return beds.containsKey(pos) ? .2F : blocks.getOrDefault(pos, 0F); }
        public BlockPos raycast(BlockPos bed) { return ray; }
    }
}
