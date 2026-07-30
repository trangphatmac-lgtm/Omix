package cn.omix.util.player.pathfinder;

import cn.omix.util.IMinecraft;
import net.minecraft.block.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

public final class PathFinder implements IMinecraft {
    private static final CompareHub COMPARE_HUB = new CompareHub();

    private final Vec3d start;
    private final Vec3d end;
    private final ArrayList<PathHub> explored = new ArrayList<>();
    private final ArrayList<PathHub> expansionBatch = new ArrayList<>(4);
    private final PriorityQueue<PathHub> working = new PriorityQueue<>(COMPARE_HUB);
    private final Map<Vec3d, PathHub> hubsByLocation = new HashMap<>();
    private ArrayList<Vec3d> path = new ArrayList<>();
    private long nextSequence;

    public PathFinder(Vec3d start, Vec3d end) {
        this.start = floor(start);
        this.end = floor(end);
    }

    public ArrayList<Vec3d> getPath() {
        return path;
    }

    public void compute() {
        compute(100, 4);
    }

    public void compute(int loops, int depth) {
        path.clear();
        explored.clear();
        expansionBatch.clear();
        working.clear();
        hubsByLocation.clear();
        nextSequence = 0L;

        if (loops <= 0 || depth <= 0 || mc.world == null || !isFinite(start) || !isFinite(end)) return;

        PathHub startHub = new PathHub(
                start,
                null,
                start.squaredDistanceTo(end),
                0.0,
                0.0,
                nextSequence++
        );
        working.add(startHub);
        hubsByLocation.put(start, startHub);

        for (int i = 0; i < loops; i++) {
            if (working.isEmpty()) break;

            int nodesToExpand = Math.min(depth, working.size());
            for (int j = 0; j < nodesToExpand; j++) {
                PathHub hub = working.poll();
                if (hub == null) break;
                expansionBatch.add(hub);
            }

            for (PathHub hub : expansionBatch) {
                explored.add(hub);
                if (tryDirection(hub, 1, 0, 0)
                        || tryDirection(hub, -1, 0, 0)
                        || tryDirection(hub, 0, 0, 1)
                        || tryDirection(hub, 0, 0, -1)
                        || tryDirection(hub, 0, 1, 0)
                        || tryDirection(hub, 0, -1, 0)) {
                    return;
                }
            }
            expansionBatch.clear();
        }

        if (!explored.isEmpty()) {
            PathHub closest = explored.getFirst();
            for (int i = 1; i < explored.size(); i++) {
                PathHub candidate = explored.get(i);
                if (COMPARE_HUB.compare(candidate, closest) < 0) {
                    closest = candidate;
                }
            }
            path = closest.getPathway();
        }
    }

    private boolean tryDirection(PathHub parent, double x, double y, double z) {
        Vec3d location = floor(parent.getLocation().add(x, y, z));
        return isValid(location, false) && putHub(parent, location, 0.0);
    }

    private boolean putHub(PathHub parent, Vec3d location, double cost) {
        if (hubsByLocation.containsKey(location)) return false;

        double totalCost = cost + (parent == null ? 0.0 : parent.getMaxCost());
        if (location.squaredDistanceTo(end) <= 1.0) {
            PathHub destination = new PathHub(location, parent, 0.0, cost, totalCost, nextSequence++);
            path = destination.getPathway();
            return true;
        }

        PathHub hub = new PathHub(
                location,
                parent,
                location.squaredDistanceTo(end),
                cost,
                totalCost,
                nextSequence++
        );
        working.add(hub);
        hubsByLocation.put(location, hub);
        return false;
    }

    public static boolean isValid(Vec3d location, boolean checkGround) {
        return isValid((int) location.x, (int) location.y, (int) location.z, checkGround);
    }

    public static boolean isValid(int x, int y, int z, boolean checkGround) {
        if (mc.world == null) return false;

        BlockPos feet = new BlockPos(x, y, z);
        BlockPos head = feet.up();
        BlockPos ground = feet.down();
        return !isNotPassable(feet)
                && !isNotPassable(head)
                && (isNotPassable(ground) || !checkGround)
                && canWalkOn(ground);
    }

    private static boolean isNotPassable(BlockPos pos) {
        if (mc.world == null) return true;

        BlockState state = mc.world.getBlockState(pos);
        Block block = state.getBlock();
        return state.isSolidBlock(mc.world, pos)
                || block == Blocks.GLASS
                || block instanceof StainedGlassBlock
                || block instanceof PaneBlock
                || block instanceof AbstractSkullBlock
                || block instanceof SnowBlock
                || block == Blocks.GRASS_BLOCK
                || block == Blocks.MYCELIUM
                || block == Blocks.PODZOL
                || block instanceof DoorBlock
                || block instanceof LeavesBlock
                || block instanceof SlabBlock
                || block instanceof StairsBlock
                || block instanceof CactusBlock
                || block instanceof ChestBlock
                || block instanceof EnderChestBlock
                || block instanceof FenceBlock
                || block instanceof WallBlock
                || block instanceof TintedGlassBlock
                || block instanceof PistonBlock
                || block instanceof PistonExtensionBlock
                || block instanceof PistonHeadBlock
                || block instanceof TrapdoorBlock
                || block instanceof EndPortalBlock
                || block instanceof EndPortalFrameBlock
                || block instanceof BedBlock
                || block instanceof CobwebBlock
                || block instanceof BarrierBlock
                || block instanceof LadderBlock
                || block instanceof CarpetBlock;
    }

    private static boolean canWalkOn(BlockPos pos) {
        if (mc.world == null) return false;

        Block block = mc.world.getBlockState(pos).getBlock();
        return !(block instanceof FenceBlock)
                && !(block instanceof FenceGateBlock)
                && !(block instanceof WallBlock)
                && block != Blocks.BARRIER;
    }

    private static Vec3d floor(Vec3d vector) {
        return new Vec3d(Math.floor(vector.x), Math.floor(vector.y), Math.floor(vector.z));
    }

    private static boolean isFinite(Vec3d vector) {
        return vector != null
                && Double.isFinite(vector.x)
                && Double.isFinite(vector.y)
                && Double.isFinite(vector.z);
    }

    private static final class CompareHub implements Comparator<PathHub> {
        @Override
        public int compare(PathHub first, PathHub second) {
            int score = Double.compare(
                    first.getSquaredDistance() + first.getMaxCost(),
                    second.getSquaredDistance() + second.getMaxCost()
            );
            return score != 0 ? score : Long.compare(first.getSequence(), second.getSequence());
        }
    }
}
