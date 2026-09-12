package cn.omix.util.player;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ClientRayTraceUtilTest {
    private static final Box CUBE = new Box(0, 0, 0, 1, 1, 1);

    @Test
    void zeroYawAndPitchCanHitAReachableFaceBeyondTheCenterLimit() {
        BlockPos target = new BlockPos(0, 0, 5);
        var hit = ClientRayTraceUtil.trace(new Vec3d(0.5, 0.5, 0.6), Vec3d.fromPolar(0, 0), 4.5,
                pos -> pos.equals(target) ? List.of(CUBE) : List.of());
        assertNotNull(hit);
        assertEquals(target, hit.getBlockPos());
        assertEquals(Direction.NORTH, hit.getSide());
        assertEquals(5.0, hit.getPos().z, 1.0E-6);
    }

    @Test
    void choosesNearestBoxRegardlessOfShapeBoxOrder() {
        var hit = ClientRayTraceUtil.trace(new Vec3d(0.5, 0.5, 0.5), new Vec3d(0, 0, 1), 4.5,
                pos -> pos.equals(new BlockPos(0, 0, 2)) ? List.of(
                        new Box(0, 0, 0.7, 1, 1, 1), new Box(0, 0, 0, 1, 1, 0.3)) : List.of());
        assertNotNull(hit);
        assertEquals(2.0, hit.getPos().z, 1.0E-9);
    }

    @Test
    void occluderWinsOverTarget() {
        Map<BlockPos, List<Box>> blocks = Map.of(
                new BlockPos(0, 0, 1), List.of(CUBE), new BlockPos(0, 0, 3), List.of(CUBE));
        var hit = ClientRayTraceUtil.trace(new Vec3d(0.5, 0.5, 0.5), new Vec3d(0, 0, 1), 4.5,
                pos -> blocks.getOrDefault(pos, List.of()));
        assertNotNull(hit);
        assertEquals(new BlockPos(0, 0, 1), hit.getBlockPos());
    }

    @Test
    void handlesNegativeCoordinatesAndAxisAlignedDownwardRay() {
        var target = new BlockPos(-2, -2, -2);
        var hit = ClientRayTraceUtil.trace(new Vec3d(-1.5, 1.5, -1.5), new Vec3d(0, -1, 0), 4.5,
                pos -> pos.equals(target) ? List.of(CUBE) : List.of());
        assertNotNull(hit);
        assertEquals(target, hit.getBlockPos());
        assertEquals(Direction.UP, hit.getSide());
    }

    @Test
    void missesOutsideReachAndAboveSlab() {
        assertNull(ClientRayTraceUtil.trace(new Vec3d(0.5, 0.5, 0.4), new Vec3d(0, 0, 1), 4.5,
                pos -> pos.equals(new BlockPos(0, 0, 5)) ? List.of(CUBE) : List.of()));
        assertNull(ClientRayTraceUtil.trace(new Vec3d(0.5, 0.75, 0.5), new Vec3d(0, 0, 1), 4.5,
                pos -> pos.equals(new BlockPos(0, 0, 2)) ? List.of(new Box(0, 0, 0, 1, 0.5, 1)) : List.of()));
    }
}
