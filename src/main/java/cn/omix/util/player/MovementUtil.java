package cn.omix.util.player;

import cn.omix.event.impl.MoveInputEvent;
import cn.omix.management.RotationManager;
import cn.omix.util.IMinecraft;
import lombok.experimental.UtilityClass;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

@UtilityClass
public class MovementUtil implements IMinecraft {

    public void strafe() {
        strafe(getSpeed());
    }

    public void strafe(double speed) {
        if (mc.player == null || !isMoving()) return;

        double yaw = getDirection();
        mc.player.setVelocity(-MathHelper.sin((float) yaw) * speed, mc.player.getVelocity().y, MathHelper.cos((float) yaw) * speed);
    }

    public void stop() {
        if (mc.player == null) return;
        mc.player.setVelocity(0, 0, 0);
    }

    public boolean isMoving() {
        if (mc.player == null) return false;
        return mc.player.input.getMovementInput().y != 0.0f || mc.player.input.getMovementInput().x != 0.0f;
    }

    public boolean isForwardPressed() {
        if (mc.player == null || mc.player.input == null) return false;

        var input = mc.player.input.playerInput;
        return input.forward() != input.backward() || input.left() != input.right();
    }

    public double getDirection() {
        if (mc.player == null || mc.player.input == null) return 0;
        return getDirection(mc.player.getYaw(), mc.player.input.getMovementInput().y, mc.player.input.getMovementInput().x);
    }

    public double getDirection(float rotationYaw, float moveForward, float moveStrafing) {
        if (moveForward < 0F) rotationYaw += 180F;

        float forward = 1F;
        if (moveForward < 0F) forward = -0.5F;
        else if (moveForward > 0F) forward = 0.5F;

        if (moveStrafing > 0F) rotationYaw -= 90F * forward;
        if (moveStrafing < 0F) rotationYaw += 90F * forward;

        return Math.toRadians(rotationYaw);
    }

    public double getSpeed() {
        if (mc.player == null) return 0;

        double x = mc.player.getVelocity().x;
        double z = mc.player.getVelocity().z;

        return Math.sqrt(x * x + z * z);
    }

    public double getJumpMotion() {
        int speedLevel = 0;
        StatusEffectInstance speed = mc.player == null ? null : mc.player.getStatusEffect(StatusEffects.SPEED);
        if (speed != null && speed.getDuration() > 0) {
            speedLevel = speed.getAmplifier() + 1;
        }

        if (speedLevel == 1) return 0.49720000000000003;
        if (speedLevel >= 2) return 0.452 * 1.2;
        return 0.452;
    }

    public float getMoveYaw() {
        if (mc.player == null || mc.player.input == null) return 0.0F;

        float yaw = RotationManager.isRotating() ? RotationManager.currentRotations[0] : mc.player.getYaw();
        return MathHelper.wrapDegrees((float) Math.toDegrees(getDirection(
                yaw,
                mc.player.input.getMovementInput().y,
                mc.player.input.getMovementInput().x
        )));
    }

    public float getDirectionYaw() {
        if (mc.player == null) return 0.0F;

        Vec3d velocity = mc.player.getVelocity();
        if (Math.hypot(velocity.x, velocity.z) == 0.0) {
            return MathHelper.wrapDegrees(mc.player.getYaw());
        }
        return MathHelper.wrapDegrees((float) Math.toDegrees(Math.atan2(velocity.z, velocity.x)) - 90.0F);
    }

    public void setSpeed(double speed, float yaw) {
        if (mc.player == null) return;

        double radians = Math.toRadians(yaw);
        mc.player.setVelocity(
                -MathHelper.sin((float) radians) * speed,
                mc.player.getVelocity().y,
                MathHelper.cos((float) radians) * speed
        );
    }

    public void addSpeed(double speed, float yaw) {
        if (mc.player == null) return;

        double radians = Math.toRadians(yaw);
        Vec3d velocity = mc.player.getVelocity();
        mc.player.setVelocity(
                velocity.x - MathHelper.sin((float) radians) * speed,
                velocity.y,
                velocity.z + MathHelper.cos((float) radians) * speed
        );
    }

    public void fixMovement(MoveInputEvent event, float yaw) {
        if (mc.player == null) return;

        float forward = event.getForward();
        float strafe = event.getStrafe();
        double angle = MathHelper.wrapDegrees(Math.toDegrees(getDirection(mc.player.getYaw(), forward, strafe)));

        if (forward == 0 && strafe == 0) return;

        float closestForward = 0, closestStrafe = 0;
        float closestDifference = Float.MAX_VALUE;

        for (float predictedForward = -1F; predictedForward <= 1F; predictedForward += 1F) {
            for (float predictedStrafe = -1F; predictedStrafe <= 1F; predictedStrafe += 1F) {
                if (predictedStrafe == 0 && predictedForward == 0) continue;

                double predictedAngle = MathHelper.wrapDegrees(Math.toDegrees(getDirection(yaw, predictedForward, predictedStrafe)));
                double difference = Math.abs(MathHelper.wrapDegrees(angle - predictedAngle));

                if (difference < closestDifference) {
                    closestDifference = (float) difference;
                    closestForward = predictedForward;
                    closestStrafe = predictedStrafe;
                }
            }
        }

        event.setForward(closestForward);
        event.setStrafe(closestStrafe);
    }

    public Vec3d movementInputToVelocity(Vec3d movementInput, float speed, float yaw) {
        double d = movementInput.lengthSquared();
        if (d < 1.0E-7) {
            return Vec3d.ZERO;
        } else {
            Vec3d vec3d = (d > 1.0 ? movementInput.normalize() : movementInput).multiply(speed);
            float f = MathHelper.sin(yaw * ((float) Math.PI / 180F));
            float g = MathHelper.cos(yaw * ((float) Math.PI / 180F));
            return new Vec3d(vec3d.x * g - vec3d.z * f, vec3d.y, vec3d.z * g + vec3d.x * f);
        }
    }

    public static boolean movementInput() {
        return mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed() || mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed();
    }

    public double getBPS() {
        if (mc.player == null) return 0.0;

        double bps = Math.hypot(mc.player.getX() - mc.player.lastX, mc.player.getZ() - mc.player.lastZ) * 20;
        return Math.round(bps * 100.0) / 100.0;
    }
}
