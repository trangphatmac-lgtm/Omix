package cn.omix.util.player;

import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.PlantBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SnowBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.function.Function;

/** Scaffold collision-box DDA adapted from OpenSSNG's ClientRayTraceUtil. */
public final class ClientRayTraceUtil {
    private ClientRayTraceUtil() {
    }

    public static BlockHitResult raycastBlock(float yaw, float pitch, double range) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            return null;
        }
        var world = mc.world;
        ShapeContext context = ShapeContext.of(mc.player);
        // Read the eyes for each cast instead of retaining a stale global eyePos.
        return trace(mc.player.getEyePos(), Vec3d.fromPolar(pitch, yaw), range, pos -> {
            BlockState state = world.getBlockState(pos);
            if (isIgnoredBlock(state)) return List.of();
            return state.getCollisionShape(world, pos, context).getBoundingBoxes();
        });
    }

    private static boolean isIgnoredBlock(BlockState state) {
        return state.isAir() || state.getBlock() instanceof PlantBlock
                || state.getBlock() instanceof SnowBlock || state.getBlock() instanceof FluidBlock;
    }

    // The provider supplies block-local collision boxes; separating the traversal
    // allows boundary cases to be checked without starting a client or world.
    static BlockHitResult trace(Vec3d start, Vec3d direction, double range,
                               Function<BlockPos, List<Box>> boxesAt) {
        if (!Double.isFinite(range) || range <= 0 || !Double.isFinite(direction.lengthSquared())
                || direction.lengthSquared() < 1.0E-12
                || !Double.isFinite(start.x) || !Double.isFinite(start.y) || !Double.isFinite(start.z)) {
            return null;
        }
        Vec3d ray = direction.normalize();
        Vec3d end = start.add(ray.multiply(range));
        BlockPos pos = BlockPos.ofFloored(start);
        int stepX = (int) Math.signum(ray.x);
        int stepY = (int) Math.signum(ray.y);
        int stepZ = (int) Math.signum(ray.z);
        double nextX = nextBoundary(start.x, ray.x, pos.getX(), stepX);
        double nextY = nextBoundary(start.y, ray.y, pos.getY(), stepY);
        double nextZ = nextBoundary(start.z, ray.z, pos.getZ(), stepZ);
        double deltaX = stepX == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(ray.x);
        double deltaY = stepY == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(ray.y);
        double deltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(ray.z);
        BlockHitResult nearest = null;
        double nearestDistanceSq = Double.POSITIVE_INFINITY;
        double enteredAt = 0;

        // Stop by distance along the ray, not distance to a voxel's center:
        // a face may be in reach even when its block center is outside reach.
        while (enteredAt <= range) {
            for (Box localBox : boxesAt.apply(pos)) {
                Box box = localBox.offset(pos);
                var intercept = box.raycast(start, end);
                if (intercept.isEmpty()) continue;
                Vec3d hit = intercept.get();
                double distanceSq = start.squaredDistanceTo(hit);
                if (distanceSq <= range * range && distanceSq < nearestDistanceSq) {
                    nearestDistanceSq = distanceSq;
                    nearest = new BlockHitResult(hit, hitFace(hit, box), pos, box.contains(start));
                }
            }
            if (nextX < nextY && nextX < nextZ) {
                enteredAt = nextX;
                pos = pos.add(stepX, 0, 0);
                nextX += deltaX;
            } else if (nextY < nextZ) {
                enteredAt = nextY;
                pos = pos.add(0, stepY, 0);
                nextY += deltaY;
            } else {
                enteredAt = nextZ;
                pos = pos.add(0, 0, stepZ);
                nextZ += deltaZ;
            }
        }
        return nearest;
    }

    private static double nextBoundary(double origin, double direction, int cell, int step) {
        if (step == 0) return Double.POSITIVE_INFINITY;
        return ((step > 0 ? cell + 1.0 : cell) - origin) / direction;
    }

    private static Direction hitFace(Vec3d hit, Box box) {
        double[] distances = {
                Math.abs(hit.x - box.minX), Math.abs(hit.x - box.maxX),
                Math.abs(hit.y - box.minY), Math.abs(hit.y - box.maxY),
                Math.abs(hit.z - box.minZ), Math.abs(hit.z - box.maxZ)
        };
        Direction[] faces = {Direction.WEST, Direction.EAST, Direction.DOWN,
                Direction.UP, Direction.NORTH, Direction.SOUTH};
        int closest = 0;
        for (int i = 1; i < distances.length; i++) {
            if (distances[i] < distances[closest]) closest = i;
        }
        return faces[closest];
    }
}
