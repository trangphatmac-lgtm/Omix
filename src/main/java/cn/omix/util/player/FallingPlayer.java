package cn.omix.util.player;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.Objects;

public final class FallingPlayer {
    private static final double GRAVITY = 0.08D;
    private static final double HORIZONTAL_DRAG = 0.91D;
    private static final double VERTICAL_DRAG = 0.98D;
    private static final double GROUND_CHECK_DISTANCE = 0.2D;
    private static final double MIN_RAY_LENGTH_SQUARED = 1.0E-14D;
    private static final double EDGE_INSET = 1.0E-4D;
    private static final double[][] FOOTPRINT_OFFSETS = {
            {0.0D, 0.0D},
            {1.0D, 1.0D},
            {-1.0D, 1.0D},
            {1.0D, -1.0D},
            {-1.0D, -1.0D},
            {1.0D, 0.0D},
            {-1.0D, 0.0D},
            {0.0D, 1.0D},
            {0.0D, -1.0D}
    };

    private final ClientPlayerEntity player;
    private final World world;
    private final float eyeHeight;
    private final double footprintRadius;
    private double x;
    private double y;
    private double z;
    private Vec3d velocity;
    private float yaw;
    private float strafe;
    private float forward;
    private float acceleration;
    private boolean onGround;

    public FallingPlayer(ClientPlayerEntity player) {
        this.player = Objects.requireNonNull(player, "player");
        this.world = player.getEntityWorld();
        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();
        this.velocity = player.getVelocity();
        this.yaw = player.getYaw();
        this.strafe = player.input == null ? 0.0F : player.input.getMovementInput().x;
        this.forward = player.input == null ? 0.0F : player.input.getMovementInput().y;
        this.acceleration = player.isSprinting() ? 0.026F : 0.02F;
        this.eyeHeight = player.getEyeHeight(player.getPose());
        this.footprintRadius = Math.max(0.0D, player.getWidth() * 0.5D - EDGE_INSET);
        this.onGround = player.isOnGround();
    }

    public void calculate(int ticks) {
        for (int i = 0; i < ticks; i++) {
            calculateTick();
        }
    }

    private void calculateTick() {
        Vec3d inputVelocity = MovementUtil.movementInputToVelocity(
                new Vec3d(strafe, 0.0, forward),
                acceleration,
                yaw
        );
        velocity = velocity.add(inputVelocity);
        x += velocity.x;
        y += velocity.y;
        z += velocity.z;
        updateGroundState();
        velocity = velocity.add(0.0D, -GRAVITY, 0.0D)
                .multiply(HORIZONTAL_DRAG, VERTICAL_DRAG, HORIZONTAL_DRAG);
    }

    private void updateGroundState() {
        Vec3d position = getPos();
        onGround = findTopCollision(position, position.add(0.0D, -GROUND_CHECK_DISTANCE, 0.0D)) != null;
    }

    /**
     * Advances this prediction and returns the first block top crossed by the
     * player's footprint. Like {@link #calculate(int)}, this method mutates the
     * prediction, so create another instance when the original state is needed.
     */
    public BlockPos findCollision(int ticks) {
        BlockHitResult collision = findCollisionHit(ticks);
        return collision == null ? null : collision.getBlockPos();
    }

    /**
     * Same as {@link #findCollision(int)}, but preserves the complete raycast
     * result for callers that also need the hit position or face.
     */
    public BlockHitResult findCollisionHit(int ticks) {
        for (int i = 0; i < ticks; i++) {
            Vec3d start = getPos();
            calculateTick();
            BlockHitResult collision = findTopCollision(start, getPos());
            if (collision != null) {
                return collision;
            }
        }
        return null;
    }

    private BlockHitResult findTopCollision(Vec3d start, Vec3d end) {
        BlockHitResult nearest = null;
        double nearestDistanceSquared = Double.POSITIVE_INFINITY;

        for (double[] offset : FOOTPRINT_OFFSETS) {
            Vec3d offsetVector = new Vec3d(
                    offset[0] * footprintRadius,
                    0.0D,
                    offset[1] * footprintRadius
            );
            Vec3d rayStart = start.add(offsetVector);
            Vec3d rayEnd = end.add(offsetVector);
            BlockHitResult hit = rayTrace(rayStart, rayEnd);
            if (hit == null || hit.getSide() != Direction.UP) {
                continue;
            }

            double distanceSquared = rayStart.squaredDistanceTo(hit.getPos());
            if (distanceSquared < nearestDistanceSquared) {
                nearest = hit;
                nearestDistanceSquared = distanceSquared;
            }
        }
        return nearest;
    }

    private BlockHitResult rayTrace(Vec3d start, Vec3d end) {
        if (start.squaredDistanceTo(end) < MIN_RAY_LENGTH_SQUARED) {
            return null;
        }

        BlockHitResult result = world.raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));
        return result.getType() == HitResult.Type.BLOCK ? result : null;
    }

    public Vec3d getPos() {
        return new Vec3d(x, y, z);
    }

    public Vec3d getEyePos() {
        return new Vec3d(x, y + eyeHeight, z);
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public void setPosition(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        updateGroundState();
    }

    public Vec3d getVelocity() {
        return velocity;
    }

    public void setVelocity(Vec3d velocity) {
        this.velocity = Objects.requireNonNull(velocity, "velocity");
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public float getStrafe() {
        return strafe;
    }

    public void setStrafe(float strafe) {
        this.strafe = strafe;
    }

    public float getForward() {
        return forward;
    }

    public void setForward(float forward) {
        this.forward = forward;
    }

    public float getAcceleration() {
        return acceleration;
    }

    public void setAcceleration(float acceleration) {
        this.acceleration = acceleration;
    }

    public boolean isOnGround() {
        return onGround;
    }

    public void setOnGround(boolean onGround) {
        this.onGround = onGround;
    }
}
