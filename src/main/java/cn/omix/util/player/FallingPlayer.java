package cn.omix.util.player;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Lightweight airborne prediction used by ScaffoldX's clutch logic.
 * Adapted for Omix from OpenSSNGScaffoldAndClutch (MIT, Copyright 2026 Un4nown).
 */
public final class FallingPlayer {
    private double x;
    private double y;
    private double z;
    private Vec3d velocity;
    private final float yaw;
    private final float strafe;
    private final float forward;
    private final float acceleration;
    private final float eyeHeight;

    public FallingPlayer(ClientPlayerEntity player) {
        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();
        this.velocity = player.getVelocity();
        this.yaw = player.getYaw();
        this.strafe = player.input == null ? 0.0F : player.input.getMovementInput().x;
        this.forward = player.input == null ? 0.0F : player.input.getMovementInput().y;
        this.acceleration = player.isSprinting() ? 0.026F : 0.02F;
        this.eyeHeight = player.getEyeHeight(player.getPose());
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
        velocity = velocity.add(0.0, -0.08, 0.0).multiply(0.91, 0.98, 0.91);
    }

    public Vec3d getPos() {
        return new Vec3d(x, y, z);
    }

    public Vec3d getEyePos() {
        return new Vec3d(x, y + eyeHeight, z);
    }
}
