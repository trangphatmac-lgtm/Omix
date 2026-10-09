package cn.omix.util.world.bed;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/** The reference raycasts the target's shape directly; only Legit checks world occlusion. */
public final class BedBreakerAim {
    private BedBreakerAim() { }

    public static float[] find(ClientWorld world, ClientPlayerEntity player, BlockPos pos, double range, boolean legit) {
        Vec3d eyes = player.getEyePos();
        float[] best = null;
        double distance = Double.MAX_VALUE;
        for (double x = 0; x <= 1; x += .5) {
            for (double y = 0; y <= 1; y += .5) {
                for (double z = 0; z <= 1; z += .5) {
                    Vec3d point = new Vec3d(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
                    float[] rotation = rotation(eyes, point);
                    if (raycast(world, player, pos, rotation[0], rotation[1], range, legit) == null) continue;
                    double candidateDistance = eyes.squaredDistanceTo(point);
                    if (candidateDistance < distance) { distance = candidateDistance; best = rotation; }
                }
            }
        }
        return best != null ? best : rotation(eyes, pos.toCenterPos());
    }

    public static BlockHitResult raycast(ClientWorld world, ClientPlayerEntity player, BlockPos pos,
                                         float yaw, float pitch, double range, boolean legit) {
        Vec3d eyes = player.getEyePos();
        Vec3d end = eyes.add(Vec3d.fromPolar(pitch, yaw).multiply(range));
        if (legit) {
            BlockHitResult hit = world.raycast(new RaycastContext(eyes, end, RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE, player));
            return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos) ? hit : null;
        }
        var state = world.getBlockState(pos);
        var shape = state.getCollisionShape(world, pos);
        if (shape.isEmpty()) shape = state.getOutlineShape(world, pos);
        return shape.raycast(eyes, end, pos);
    }

    private static float[] rotation(Vec3d eyes, Vec3d point) {
        Vec3d delta = point.subtract(eyes);
        return new float[]{(float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90,
                (float) -Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)))};
    }
}
