package cn.omix.util.move;

import cn.omix.event.impl.MoveEvent;
import cn.omix.util.IMinecraft;
import cn.omix.util.player.MovementUtil;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.Vec3d;

/** Movement state for the Vanilla and Rewound LongJump modes. */
public final class LongJumpMovementMode implements IMinecraft {
    public enum Result { CONTINUE, LANDED, MISSING_SPEED }

    private boolean jumped;
    private int stage;
    private double speed;
    private double previousX;
    private double previousZ;
    private boolean hasPosition;
    private int airborneTicks;

    public void reset() {
        jumped = false;
        stage = 0;
        speed = 0.0;
        hasPosition = false;
        airborneTicks = 0;
    }

    public Result vanilla(MoveEvent event) {
        if (jumped) {
            if (mc.player.isOnGround()) return Result.LANDED;
            setHorizontal(event, Math.hypot(mc.player.getVelocity().x, mc.player.getVelocity().z));
            if (mc.player.getVelocity().y < 0.0) {
                double y = mc.player.getVelocity().y / 2.0;
                mc.player.setVelocity(mc.player.getVelocity().x, y, mc.player.getVelocity().z);
                event.setY(y);
            }
        } else if (mc.player.isOnGround()) {
            mc.player.jump();
            event.setY(mc.player.getVelocity().y);
            setHorizontal(event, 1.0);
            jumped = true;
        }
        return Result.CONTINUE;
    }

    public Result rewound(MoveEvent event) {
        StatusEffectInstance speedEffect = mc.player.getStatusEffect(StatusEffects.SPEED);
        if (stage == 0 && speedEffect == null) return Result.MISSING_SPEED;

        int speedLevel = speedEffect == null ? 0 : speedEffect.getAmplifier() + 1;
        double baseSpeed = 0.2873 * (1.0 + 0.2 * speedLevel);
        double initialSpeed = (baseSpeed * 1.38 - 0.01) / 1.6;
        double lastDistance = hasPosition
                ? Math.hypot(mc.player.getX() - previousX, mc.player.getZ() - previousZ) : 0.0;
        previousX = mc.player.getX();
        previousZ = mc.player.getZ();
        hasPosition = true;

        switch (stage) {
            case 0 -> {
                if (mc.player.isOnGround()) stage = 1;
            }
            case 1 -> {
                speed = initialSpeed;
                stage = 2;
            }
            case 2 -> {
                mc.player.jump();
                event.setY(mc.player.getVelocity().y);
                speed *= 2.149;
                stage = 3;
                airborneTicks = 0;
            }
            case 3 -> {
                if (mc.player.isOnGround()) return Result.LANDED;
                airborneTicks++;
                if (airborneTicks == 1) {
                    speed = lastDistance - (lastDistance - baseSpeed) * 0.66;
                    StatusEffectInstance jumpEffect = mc.player.getStatusEffect(StatusEffects.JUMP_BOOST);
                    double boost = jumpEffect == null ? 2.2 : 2.2 - (jumpEffect.getAmplifier() + 1) * 0.1;
                    speed *= boost;
                } else {
                    double friction = mc.player.getVelocity().y < -0.1 ? 25.0 : 159.0;
                    speed = lastDistance - lastDistance / friction;
                }
            }
            default -> throw new IllegalStateException("Invalid LongJump stage: " + stage);
        }

        if (stage >= 1) {
            speed = Math.max(speed, baseSpeed);
            setHorizontal(event, speed);
        }
        return Result.CONTINUE;
    }

    private void setHorizontal(MoveEvent event, double horizontalSpeed) {
        double x = 0.0;
        double z = 0.0;
        if (MovementUtil.isMoving()) {
            double direction = MovementUtil.getDirection();
            x = -Math.sin(direction) * horizontalSpeed;
            z = Math.cos(direction) * horizontalSpeed;
        }
        Vec3d velocity = mc.player.getVelocity();
        mc.player.setVelocity(x, velocity.y, z);
        event.setX(x);
        event.setZ(z);
    }
}
