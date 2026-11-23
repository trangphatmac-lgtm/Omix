package cn.omix.util.move;

import cn.omix.event.impl.MoveEvent;
import cn.omix.util.IMinecraft;
import cn.omix.util.player.MovementUtil;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Hop and friction state adapted from openadjust's RewoundSpeedMode. */
public final class RewoundSpeedMode implements IMinecraft {
    private double speed;
    private boolean started;
    private int airborneTicks;
    private double previousX;
    private double previousZ;
    private boolean hasPosition;
    private long damageAt;

    public void reset() {
        speed = 0.0;
        started = false;
        airborneTicks = 0;
        hasPosition = false;
        damageAt = 0L;
    }

    public void damaged() {
        damageAt = System.currentTimeMillis();
    }

    public void move(MoveEvent event, boolean restricted, boolean lowHop, boolean collisionPrediction,
                     double damageBoost) {
        if (restricted) {
            speed = 0.0;
            started = false;
            hasPosition = false;
            return;
        }
        if (!MovementUtil.isMoving()) {
            speed = 0.0;
            started = false;
            if (hasPosition && !overVoidAhead()) {
                Vec3d velocity = mc.player.getVelocity();
                if (voidAhead()) {
                    event.setX(0.0);
                    event.setY(0.0);
                    mc.player.setVelocity(0.0, 0.0, velocity.z);
                } else {
                    event.setX(event.getX() * 0.82);
                    event.setZ(event.getZ() * 0.82);
                    mc.player.setVelocity(velocity.x * 0.82, velocity.y, velocity.z * 0.82);
                }
            }
            samplePosition();
            return;
        }

        double lastDistance = hasPosition
                ? Math.hypot(mc.player.getX() - previousX, mc.player.getZ() - previousZ) : 0.0;
        samplePosition();
        airborneTicks = mc.player.isOnGround() ? 0 : airborneTicks + 1;
        // The source uses the global movement predictor history; player age is the host's join warmup.
        if (mc.player.age < 7) {
            started = false;
            return;
        }

        StatusEffectInstance speedEffect = mc.player.getStatusEffect(StatusEffects.SPEED);
        int level = speedEffect == null ? 0 : speedEffect.getAmplifier() + 1;
        double base = 0.2873 * (1.0 + 0.2 * level);
        double initial = speedEffect == null ? 0.377855 : base * 1.35 - 0.01;

        if (airborneTicks == 0) {
            if (!lowHop || started) {
                if (mc.player.getVelocity().y <= 0.0) {
                    mc.player.jump();
                    event.setY(mc.player.getVelocity().y);
                }
                speed = base * 2.149;
            } else {
                speed = initial;
                started = true;
            }
        } else if (airborneTicks == 1) {
            speed -= (speed - base) * 0.66;
            if (damageAt + 500 > System.currentTimeMillis()) speed *= damageBoost;
        } else {
            speed = calculateFriction(lastDistance, base);
            started = false;
        }

        speed = Math.max(speed, base);
        double direction = MovementUtil.getDirection();
        double x = -Math.sin(direction) * speed;
        double z = Math.cos(direction) * speed;
        event.setX(x);
        event.setZ(z);
        mc.player.setVelocity(x, mc.player.getVelocity().y, z);

        if (mc.player.age < 13 || mc.player.hasStatusEffect(StatusEffects.JUMP_BOOST)) return;
        if (lowHop && started && !shouldFullJump(collisionPrediction)) {
            if (airborneTicks == 1) {
                setVertical(event, -0.0784F);
                started = false;
            }
        } else if (airborneTicks == 4) {
            setVertical(event, event.getY() - 0.045);
        } else if (airborneTicks == 5) {
            setVertical(event, event.getY() - 0.175);
        }
    }

    private static double calculateFriction(double distance, double base) {
        return Math.max(Math.max(distance - distance / 160.0,
                distance - (distance - base) / 33.3), distance - (distance - base) * 0.02);
    }

    private void setVertical(MoveEvent event, double y) {
        Vec3d velocity = mc.player.getVelocity();
        mc.player.setVelocity(velocity.x, y, velocity.z);
        event.setY(y);
    }

    private void samplePosition() {
        previousX = mc.player.getX();
        previousZ = mc.player.getZ();
        hasPosition = true;
    }

    private boolean overVoidAhead() {
        return !mc.world.getBlockCollisions(mc.player,
                mc.player.getBoundingBox().stretch(0.0, -2.0, 0.0)).iterator().hasNext();
    }

    private boolean voidAhead() {
        Vec3d velocity = mc.player.getVelocity();
        double length = Math.max(0.5, Math.hypot(velocity.x, velocity.z));
        double x = velocity.x == 0 && velocity.z == 0 ? 0 : velocity.x / Math.hypot(velocity.x, velocity.z) * length;
        double z = velocity.x == 0 && velocity.z == 0 ? 0 : velocity.z / Math.hypot(velocity.x, velocity.z) * length;
        return !mc.world.getBlockCollisions(mc.player,
                mc.player.getBoundingBox().offset(x, 0.0, z).stretch(0.0, -2.0, 0.0)).iterator().hasNext();
    }

    private boolean shouldFullJump(boolean collisionPrediction) {
        if (!collisionPrediction) return false;
        if (mc.player.horizontalCollision) return true;
        double direction = MovementUtil.getDirection();
        for (int i = 1; i <= 4; i++) {
            double x = -Math.sin(direction) * speed * i;
            double z = Math.cos(direction) * speed * i;
            Box predicted = mc.player.getBoundingBox().offset(x, 0.0, z);
            if (!mc.world.isSpaceEmpty(mc.player, predicted)
                    || !mc.world.getBlockCollisions(mc.player, predicted.offset(0.0, -1.0, 0.0))
                    .iterator().hasNext()) return true;
        }
        return false;
    }
}
