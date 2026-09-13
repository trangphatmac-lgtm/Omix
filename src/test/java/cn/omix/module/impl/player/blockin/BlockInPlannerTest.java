package cn.omix.module.impl.player.blockin;

import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BlockInPlannerTest {
    private static final BlockPos FEET = BlockPos.ORIGIN;
    private static final Vec3d EYE = new Vec3d(0.5, 1.62, 0.5);

    @Test
    void enclosureHasNineUniqueCellsAndExcludesThePlayer() {
        var cells = BlockInPlanner.enclosure(new BlockPos(-5, 64, -8));
        assertEquals(9, new HashSet<>(cells).size());
        assertTrue(cells.contains(new BlockPos(-5, 66, -8)));
        assertFalse(cells.contains(new BlockPos(-5, 64, -8)));
        assertFalse(cells.contains(new BlockPos(-5, 65, -8)));
        for (BlockPos cell : cells) assertTrue(BlockInPlanner.isDirect(new BlockPos(-5, 64, -8), cell));
        assertFalse(BlockInPlanner.isDirect(FEET, new BlockPos(1, 2, 0)));
    }

    @Test
    void buildsEightSidesOnOpenFloorAndCompletesRoofWhenSupportBecomesReachable() {
        TestWorld world = new TestWorld();
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) world.solid.add(new BlockPos(x, -1, z));
        }
        float yaw = 0, pitch = 0;
        for (int step = 0; step < 20; step++) {
            var aim = world.planner(yaw, pitch).find(null);
            if (aim == null) break;
            assertTrue(world.canPlace(aim.hit()));
            assertEquals(aim.goal(), aim.hit().getBlockPos().offset(aim.hit().getSide()));
            assertTrue(world.solid.add(aim.goal()), "Must make progress every placement");
            yaw = aim.yaw();
            pitch = aim.pitch();
        }
        assertTrue(world.solid.containsAll(BlockInPlanner.enclosure(FEET).subList(1, 9)));
        assertFalse(world.solid.contains(FEET.up(2)), "A grounded eye cannot click the top of a two-high wall");
        assertNull(world.planner(yaw, pitch).find(null));

        // Briefly jumping exposes a wall's top face. The roof itself still intersects
        // the player, so only a temporary block beside it is allowed at this moment.
        world.eye = new Vec3d(0.5, 2.04, 0.5);
        world.allowedGoal = new BlockPos(1, 2, 0);
        var temporary = world.planner(yaw, pitch).find(null);
        assertNotNull(temporary);
        assertEquals(world.allowedGoal, temporary.goal());
        world.solid.add(temporary.goal());
        world.eye = EYE;
        world.allowedGoal = null;
        var roof = world.planner(temporary.yaw(), temporary.pitch()).find(null);
        assertNotNull(roof);
        assertEquals(FEET.up(2), roof.goal());
        world.solid.add(roof.goal());
        assertTrue(world.solid.containsAll(BlockInPlanner.enclosure(FEET)));
        assertNull(world.planner(roof.yaw(), roof.pitch()).find(null));
    }

    @Test
    void roofTakesPriorityOverNearbyEnemySide() {
        TestWorld world = new TestWorld();
        world.solid.add(new BlockPos(0, 3, 0));
        world.solid.add(new BlockPos(1, -1, 0));
        var aim = world.planner(0, 0).find(new Vec3d(5, 0, 0));
        assertNotNull(aim);
        assertEquals(FEET.up(2), aim.goal());
        assertEquals(Direction.DOWN, aim.hit().getSide());
    }

    @Test
    void prefersTheClosestPlayersSideEvenWhenFacingTheOtherWay() {
        TestWorld world = new TestWorld();
        world.solid.add(FEET.up(2));
        world.solid.add(new BlockPos(1, -1, 0));
        world.solid.add(new BlockPos(-1, -1, 0));
        var aim = world.planner(90, 45).find(new Vec3d(5, 0, 0));
        assertNotNull(aim);
        assertEquals(new BlockPos(1, 0, 0), aim.goal());
    }

    @Test
    void horizontalSupportsClickTheirOppositeFaceInEveryDirection() {
        for (Direction direction : new Direction[]{Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH}) {
            TestWorld world = new TestWorld();
            world.solid.add(FEET.up(2));
            BlockPos goal = FEET.offset(direction);
            world.allowedGoal = goal;
            world.solid.add(goal.offset(direction));
            var aim = world.planner(0, 0).find(null);
            assertNotNull(aim, direction.toString());
            assertEquals(goal, aim.goal());
            assertEquals(direction.getOpposite(), aim.hit().getSide());
        }
    }

    @Test
    void rejectsBlockedOrUnplaceableTargetsAndOutOfReachSupports() {
        TestWorld world = new TestWorld();
        world.solid.add(new BlockPos(0, 3, 0));
        world.rejectAll = true;
        assertNull(world.planner(0, 0).find(null));
        world.rejectAll = false;
        world.solid.clear();
        world.solid.add(new BlockPos(0, 8, 0));
        assertNull(world.planner(0, 0).find(null));
    }

    @Test
    void smoothsAcrossYawWrapUsingTheShortPathWithoutOvershooting() {
        float[] smoothed = BlockInPlanner.smooth(179, 0, -179, 0, 10, 0, 0.5);
        assertEquals(181, smoothed[0], 0.0001);
        assertEquals(0, smoothed[1], 0.0001);
        float[] fast = BlockInPlanner.smooth(-179, 0, 179, 95, 30, 100, 1);
        assertEquals(-181, fast[0], 0.0001);
        assertEquals(90, fast[1], 0.0001);
    }

    @Test
    void preservesLegacySpeedCurveAndRandomizationBounds() {
        float[] normal = BlockInPlanner.smooth(0, 0, 90, 0, 10, 0, 0.5);
        assertEquals(20, normal[0], 0.0001);
        float[] slower = BlockInPlanner.smooth(0, 0, 90, 0, 10, 100, 0);
        float[] faster = BlockInPlanner.smooth(0, 0, 90, 0, 10, 100, 1);
        assertTrue(slower[0] >= 11.19 && slower[0] < normal[0]);
        assertTrue(faster[0] > normal[0] && faster[0] <= 26);
        assertArrayEquals(new float[]{15, 20}, BlockInPlanner.smooth(15, 20, 90, 50, 0, 100, 1));
    }

    private static final class TestWorld implements BlockInPlanner.Environment {
        private final Set<BlockPos> solid = new HashSet<>();
        private Vec3d eye = EYE;
        private BlockPos allowedGoal;
        private boolean rejectAll;

        BlockInPlanner planner(float yaw, float pitch) {
            return new BlockInPlanner(this, FEET, eye, yaw, pitch, 4.5, () -> 0.5);
        }

        public boolean replaceable(BlockPos pos) { return !solid.contains(pos); }
        public boolean support(BlockPos pos, boolean roof) { return solid.contains(pos); }

        public boolean canPlace(BlockHitResult hit) {
            BlockPos goal = hit.getBlockPos().offset(hit.getSide());
            return !rejectAll && replaceable(goal) && !goal.equals(FEET) && !goal.equals(FEET.up())
                    && (allowedGoal == null || allowedGoal.equals(goal));
        }

        public BlockHitResult raycast(float yaw, float pitch) {
            Vec3d end = eye.add(Vec3d.fromPolar(pitch, yaw).multiply(4.5));
            BlockHitResult best = null;
            double distance = Double.MAX_VALUE;
            for (BlockPos pos : solid) {
                var intersection = new Box(pos).raycast(eye, end);
                if (intersection.isEmpty()) continue;
                Vec3d point = intersection.get();
                double current = eye.squaredDistanceTo(point);
                if (current >= distance) continue;
                Direction face = null;
                double closest = Double.MAX_VALUE;
                for (Direction direction : Direction.values()) {
                    Vec3d center = Vec3d.ofCenter(pos).add(Vec3d.of(direction.getVector()).multiply(0.5));
                    double planeDistance = Math.abs(point.subtract(center).dotProduct(Vec3d.of(direction.getVector())));
                    if (planeDistance < closest) {
                        closest = planeDistance;
                        face = direction;
                    }
                }
                best = new BlockHitResult(point, face, pos, false);
                distance = current;
            }
            return best;
        }
    }
}
