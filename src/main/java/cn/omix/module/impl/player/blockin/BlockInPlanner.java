package cn.omix.module.impl.player.blockin;

import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.DoubleSupplier;

/** Geometry-only port of Raven's roof-first, enemy-facing block-in search. */
public final class BlockInPlanner {
    private static final Direction[] HORIZONTALS = {
            Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH
    };
    private final Environment environment;
    private final BlockPos feet;
    private final Vec3d eye;
    private final float yaw;
    private final float pitch;
    private final double reach;
    private final DoubleSupplier random;

    public interface Environment {
        boolean replaceable(BlockPos pos);
        boolean support(BlockPos pos, boolean roof);
        boolean canPlace(BlockHitResult hit);
        BlockHitResult raycast(float yaw, float pitch);
    }

    public record Aim(BlockHitResult hit, float yaw, float pitch) {
        public BlockPos goal() {
            return hit.getBlockPos().offset(hit.getSide());
        }
    }

    private record Candidate(double cost, float yaw, float pitch, BlockPos support, Direction face) {}

    public BlockInPlanner(Environment environment, BlockPos feet, Vec3d eye,
                          float yaw, float pitch, double reach, DoubleSupplier random) {
        this.environment = environment;
        this.feet = feet;
        this.eye = eye;
        this.yaw = yaw;
        this.pitch = pitch;
        this.reach = reach;
        this.random = random;
    }

    public Aim find(Vec3d closestPlayer) {
        Aim roof = roofAim();
        return roof != null ? roof : sidesAim(closestPlayer);
    }

    public static List<BlockPos> enclosure(BlockPos feet) {
        List<BlockPos> result = new ArrayList<>(9);
        result.add(feet.up(2));
        for (Direction direction : HORIZONTALS) {
            result.add(feet.offset(direction));
            result.add(feet.up().offset(direction));
        }
        return result;
    }

    public static boolean isDirect(BlockPos feet, BlockPos pos) {
        int dx = pos.getX() - feet.getX();
        int dy = pos.getY() - feet.getY();
        int dz = pos.getZ() - feet.getZ();
        return dx == 0 && dz == 0 && dy == 2
                || (dy == 0 || dy == 1) && Math.abs(dx) + Math.abs(dz) == 1;
    }

    private Aim roofAim() {
        if (!environment.replaceable(feet.up(2))) return null;
        Aim direct = forGoals(List.of(feet.up(2)));
        if (direct != null) return direct;
        int minY = MathHelper.floor(eye.y) + 1;
        List<BlockPos> supports = new ArrayList<>();
        for (int y = minY; y <= MathHelper.floor(eye.y + reach); y++) {
            for (int x = MathHelper.floor(eye.x - reach); x <= MathHelper.floor(eye.x + reach); x++) {
                for (int z = MathHelper.floor(eye.z - reach); z <= MathHelper.floor(eye.z + reach); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (distanceSquared(eye, pos) <= reach * reach && environment.support(pos, true)) {
                        supports.add(pos);
                    }
                }
            }
        }
        supports.sort(Comparator.comparingDouble(pos -> distanceSquared(eye, pos)));
        for (BlockPos support : supports) {
            List<Candidate> candidates = new ArrayList<>();
            BlockHitResult current = environment.raycast(yaw, pitch);
            if (matches(current, support, null) && roofFaceAllowed(support, current.getSide(), minY)
                    && environment.canPlace(current)) return new Aim(current, yaw, pitch);

            Direction[] faces = {
                    eye.y > support.getY() + 0.5 ? Direction.UP : Direction.DOWN,
                    eye.z > support.getZ() + 0.5 ? Direction.SOUTH : Direction.NORTH,
                    eye.x > support.getX() + 0.5 ? Direction.EAST : Direction.WEST
            };
            for (Direction face : faces) {
                if (roofFaceAllowed(support, face, minY)) addFace(candidates, support, face);
            }
            Aim result = best(candidates);
            if (result != null) return result;
        }
        return null;
    }

    private boolean roofFaceAllowed(BlockPos support, Direction face, int minY) {
        return !(face == Direction.DOWN && support.getY() == minY)
                && validGoal(support.offset(face));
    }

    private Aim sidesAim(Vec3d closestPlayer) {
        List<BlockPos> goals = new ArrayList<>(8);
        for (BlockPos pos : enclosure(feet).subList(1, 9)) {
            if (validGoal(pos) && hasAirNeighbor(pos)) goals.add(pos);
        }
        // An open floor may have no overhead support even after all eight side cells
        // are filled. Keep a roof frontier so a temporary third-layer block can cap it.
        if (goals.isEmpty() && validGoal(feet.up(2))) goals.add(feet.up(2));
        if (goals.isEmpty()) return null;

        if (closestPlayer != null) {
            List<BlockPos> ordered = new ArrayList<>(enclosure(feet).subList(1, 9));
            ordered.sort(Comparator.comparingDouble(pos -> Vec3d.ofCenter(pos).squaredDistanceTo(closestPlayer)));
            int tried = 0;
            for (BlockPos pos : ordered) {
                if (!goals.contains(pos)) continue;
                Aim aim = forGoals(List.of(pos));
                if (aim != null) return aim;
                if (++tried == 3) break;
            }
        }
        Aim direct = forGoals(goals);
        if (direct != null) return direct;

        List<BlockPos> frontier = goals;
        Set<BlockPos> seen = new HashSet<>(goals);
        // As in the original, allow up to five layers of temporary support blocks.
        for (int depth = 0; depth < 5 && !frontier.isEmpty(); depth++) {
            List<BlockPos> layer = new ArrayList<>();
            for (BlockPos goal : frontier) {
                for (Direction direction : Direction.values()) {
                    BlockPos next = goal.offset(direction);
                    if (validGoal(next) && distanceSquared(eye, next) <= (reach + 1) * (reach + 1)
                            && seen.add(next)) layer.add(next);
                }
            }
            Aim aim = forGoals(layer);
            if (aim != null) return aim;
            frontier = layer;
        }
        return null;
    }

    private boolean hasAirNeighbor(BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (validGoal(pos.offset(direction))) return true;
        }
        return false;
    }

    private boolean validGoal(BlockPos pos) {
        return !pos.equals(feet) && !pos.equals(feet.up()) && environment.replaceable(pos);
    }

    private Aim forGoals(List<BlockPos> goals) {
        if (goals.isEmpty()) return null;
        BlockHitResult current = environment.raycast(yaw, pitch);
        if (current != null && current.getType() == HitResult.Type.BLOCK
                && goals.contains(current.getBlockPos().offset(current.getSide()))
                && environment.support(current.getBlockPos(), false) && environment.canPlace(current)) {
            return new Aim(current, yaw, pitch);
        }
        List<Candidate> candidates = new ArrayList<>();
        for (BlockPos goal : goals) {
            for (Direction offset : Direction.values()) {
                BlockPos support = goal.offset(offset);
                if (distanceSquared(eye, support) > reach * reach || !environment.support(support, false)) continue;
                // The clicked face points FROM the support TO the goal.
                addFace(candidates, support, offset.getOpposite());
            }
        }
        return best(candidates);
    }

    private void addFace(List<Candidate> candidates, BlockPos support, Direction face) {
        for (int row = 0; row <= 5; row++) {
            double v = sample(row);
            for (int col = 0; col <= 5; col++) {
                double u = sample(col);
                double x = support.getX(), y = support.getY(), z = support.getZ();
                Vec3d point = switch (face) {
                    case UP -> new Vec3d(x + u, y + 0.949, z + v);
                    case DOWN -> new Vec3d(x + u, y + 0.051, z + v);
                    case SOUTH -> new Vec3d(x + u, y + v, z + 0.949);
                    case NORTH -> new Vec3d(x + u, y + v, z + 0.051);
                    case EAST -> new Vec3d(x + 0.949, y + v, z + u);
                    case WEST -> new Vec3d(x + 0.051, y + v, z + u);
                };
                Vec3d delta = point.subtract(eye);
                float targetYaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90;
                float targetPitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z)));
                double cost = Math.abs(MathHelper.wrapDegrees(targetYaw - yaw)) + Math.abs(targetPitch - pitch);
                candidates.add(new Candidate(cost, targetYaw, targetPitch, support, face));
            }
        }
    }

    private double sample(int index) {
        return MathHelper.clamp(index * 0.2 + (random.getAsDouble() * 2 - 1) * 0.02, 0.001, 0.999);
    }

    private Aim best(List<Candidate> candidates) {
        candidates.sort(Comparator.comparingDouble(Candidate::cost));
        for (Candidate candidate : candidates) {
            BlockHitResult hit = environment.raycast(candidate.yaw, candidate.pitch);
            if (matches(hit, candidate.support, candidate.face) && environment.canPlace(hit)) {
                return new Aim(hit, candidate.yaw, candidate.pitch);
            }
        }
        return null;
    }

    private static boolean matches(BlockHitResult hit, BlockPos support, Direction face) {
        return hit != null && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(support)
                && (face == null || hit.getSide() == face);
    }

    private static double distanceSquared(Vec3d eye, BlockPos pos) {
        double dx = eye.x - MathHelper.clamp(eye.x, pos.getX(), pos.getX() + 1);
        double dy = eye.y - MathHelper.clamp(eye.y, pos.getY(), pos.getY() + 1);
        double dz = eye.z - MathHelper.clamp(eye.z, pos.getZ(), pos.getZ() + 1);
        return dx * dx + dy * dy + dz * dz;
    }

    /** Preserve the old 1..30 speed curve and 0..100% step/proximity variation. */
    public static float[] smooth(float yaw, float pitch, float targetYaw, float targetPitch,
                                 float speed, float randomization, double random) {
        float dy = MathHelper.wrapDegrees(targetYaw - yaw);
        float dp = targetPitch - pitch;
        float magnitude = (float) Math.hypot(dy, dp);
        if (speed <= 0) return new float[]{yaw, MathHelper.clamp(pitch, -90, 90)};
        if (speed >= 30 || magnitude < 0.001) {
            return new float[]{yaw + dy, MathHelper.clamp(targetPitch, -90, 90)};
        }
        float amount = randomization / 100;
        double step = Math.pow(speed / 30, 2) * 180 * (1 - 0.3 * amount + random * 0.6 * amount);
        double proximity = Math.pow(Math.min(1, magnitude / 180), 0.7);
        step *= Math.max(0.8, 1 - amount * (1 - proximity));
        float scale = (float) Math.min(1, step / magnitude);
        return new float[]{yaw + dy * scale, MathHelper.clamp(pitch + dp * scale, -90, 90)};
    }
}
