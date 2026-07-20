package cn.remix.util.player.pathfinder;

import cn.remix.util.IMinecraft;
import net.minecraft.block.AbstractSignBlock;
import net.minecraft.block.AbstractSkullBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.BushBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;

public final class MainPathFinder implements IMinecraft {
    private MainPathFinder() {}

    public static ArrayList<Vec3d> computePath(Vec3d from, Vec3d to) {
        if (!isFinite(from) || !isFinite(to) || mc.world == null) return new ArrayList<>();

        if (cn.remix.module.impl.exploits.PathFinder.mode.is("Linear")) {
            return LinearPathFinder.INSTANCE.getPaths(
                    BlockPos.ofFloored(from),
                    BlockPos.ofFloored(to),
                    cn.remix.module.impl.exploits.PathFinder.linearSteps.getValue().intValue(),
                    4
            );
        }

        if (cn.remix.module.impl.exploits.PathFinder.verticalPassThrough.getValue()) {
            ArrayList<Vec3d> verticalPath = getVerticalPassThroughPath(from, to);
            if (verticalPath != null) return verticalPath;
        }

        PathFinder pathFinder = new PathFinder(from, to);
        pathFinder.compute();

        int index = 0;
        Vec3d lastLocation = null;
        Vec3d lastDashLocation = null;
        ArrayList<Vec3d> path = new ArrayList<>();
        ArrayList<Vec3d> rawPath = pathFinder.getPath();

        for (Vec3d pathElement : rawPath) {
            if (index == 0 || index == rawPath.size() - 1) {
                if (lastLocation != null) {
                    path.add(lastLocation.add(0.6, 0.0, 0.6));
                }
                path.add(pathElement.add(0.6, 0.0, 0.6));
                lastDashLocation = pathElement;
            } else {
                boolean canContinue = pathElement.squaredDistanceTo(lastDashLocation) <= 25.0;
                if (canContinue) {
                    search:
                    for (int x = Math.min((int) lastDashLocation.x, (int) pathElement.x);
                         x <= Math.max((int) lastDashLocation.x, (int) pathElement.x); x++) {
                        for (int y = Math.min((int) lastDashLocation.y, (int) pathElement.y);
                             y <= Math.max((int) lastDashLocation.y, (int) pathElement.y); y++) {
                            for (int z = Math.min((int) lastDashLocation.z, (int) pathElement.z);
                                 z <= Math.max((int) lastDashLocation.z, (int) pathElement.z); z++) {
                                if (!PathFinder.isValid(x, y, z, false)) {
                                    canContinue = false;
                                    break search;
                                }
                            }
                        }
                    }
                }

                if (!canContinue) {
                    path.add(lastLocation.add(0.6, 0.0, 0.6));
                    lastDashLocation = lastLocation;
                }
            }
            lastLocation = pathElement;
            index++;
        }

        return path;
    }

    public static boolean canPassThrough(BlockPos pos) {
        if (mc.world == null) return false;

        Block block = mc.world.getBlockState(pos).getBlock();
        Block blockBelow = mc.world.getBlockState(pos.down()).getBlock();
        return mc.world.getBlockState(pos).isAir()
                || blockBelow instanceof AbstractSkullBlock
                || block instanceof BushBlock
                || block instanceof AbstractSignBlock
                || block == Blocks.LADDER
                || block == Blocks.VINE
                || block == Blocks.SCAFFOLDING
                || block == Blocks.WATER;
    }

    private static ArrayList<Vec3d> getVerticalPassThroughPath(Vec3d from, Vec3d to) {
        int fromX = (int) Math.floor(from.x);
        int fromZ = (int) Math.floor(from.z);
        int toX = (int) Math.floor(to.x);
        int toZ = (int) Math.floor(to.z);
        double yDistance = Math.abs(to.y - from.y);

        if (fromX != toX
                || fromZ != toZ
                || yDistance <= 0.0
                || yDistance > cn.remix.module.impl.exploits.PathFinder.maxVerticalRange.getValue()) {
            return null;
        }

        ArrayList<Vec3d> path = new ArrayList<>();
        int steps = Math.max(1, (int) Math.ceil(yDistance));
        for (int i = 1; i <= steps; i++) {
            double progress = (double) i / steps;
            path.add(new Vec3d(from.x, from.y + (to.y - from.y) * progress, from.z));
        }
        return path;
    }

    private static boolean isFinite(Vec3d vector) {
        return vector != null
                && Double.isFinite(vector.x)
                && Double.isFinite(vector.y)
                && Double.isFinite(vector.z);
    }
}
