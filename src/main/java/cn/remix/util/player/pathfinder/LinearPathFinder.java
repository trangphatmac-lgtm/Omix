package cn.remix.util.player.pathfinder;

import cn.remix.util.IMinecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;

public final class LinearPathFinder implements IMinecraft {
    public static final LinearPathFinder INSTANCE = new LinearPathFinder();

    private LinearPathFinder() {}

    public ArrayList<Vec3d> getPaths(BlockPos start, BlockPos end, int blocksPerStep, int maxSteps) {
        ArrayList<Vec3d> path = new ArrayList<>();
        if (mc.player == null || start == null || end == null || blocksPerStep <= 0 || maxSteps <= 0) {
            return path;
        }

        double deltaX = end.getX() - start.getX();
        double deltaY = end.getY() - start.getY();
        double deltaZ = end.getZ() - start.getZ();
        double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
        if (!Double.isFinite(distance) || distance == 0.0) return path;

        int totalSteps = Math.min((int) Math.ceil(distance / blocksPerStep), maxSteps);
        for (int i = 1; i <= totalSteps; i++) {
            double progress = (double) i / totalSteps;
            path.add(new Vec3d(
                    start.getX() + progress * deltaX,
                    start.getY() + progress * deltaY,
                    start.getZ() + progress * deltaZ
            ));
        }
        return path;
    }
}
