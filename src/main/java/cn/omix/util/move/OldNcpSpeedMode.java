package cn.omix.util.move;

import cn.omix.event.impl.MoveEvent;
import cn.omix.util.IMinecraft;
import cn.omix.util.player.MovementUtil;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.Vec3d;

/** Movement and recovery state adapted from LiquidBounce's SpeedRewound. */
public final class OldNcpSpeedMode implements IMinecraft {
    private enum Phase { RECOVER, BOOST, AIR }

    private Phase phase = Phase.AIR;
    private int phaseTicks = 2;
    private boolean collided;
    private int stairTicks;
    private double hopLoad;
    private boolean slowHop;
    private boolean slowAfterSetback;
    private long lastSetback;
    private double previousY;
    private boolean previousGround;
    private boolean hasPosition;

    public void reset() {
        phase = Phase.AIR;
        phaseTicks = 2;
        collided = mc.player != null && mc.player.horizontalCollision;
        stairTicks = 0;
        hopLoad = 0.0;
        slowHop = false;
        slowAfterSetback = false;
        hasPosition = false;
    }

    public void setback() {
        recover(4);
        lastSetback = System.currentTimeMillis();
    }

    /** The host has no step-success event; observe the completed move on the next tick. */
    public void observeStep() {
        if (mc.player == null) return;
        double y = mc.player.getY();
        boolean ground = mc.player.isOnGround();
        if (hasPosition && previousGround && ground) {
            double height = y - previousY;
            if (height > 0.7) hopLoad = 0.0;
            if (Math.abs(height - 0.5) < 1.0E-4) stairTicks = 3;
        }
        previousY = y;
        previousGround = ground;
        hasPosition = true;
    }

    public boolean move(MoveEvent event) {
        collided |= mc.player.horizontalCollision;
        if (collided) recover(1);

        stairTicks = Math.max(0, stairTicks - 1);
        hopLoad = Math.max(0.0, hopLoad - (hopLoad > 1.0 ? 0.12 : 0.11));
        if (!MovementUtil.isMoving()) return !collided;

        double y = event.getY();
        if (!mc.player.isTouchingWater() && !mc.player.isInLava() && isGroundWithin()) {
            collided = mc.player.horizontalCollision;
            if (phase != Phase.RECOVER || collided) {
                phase = Phase.BOOST;
                if (stairTicks == 0) {
                    y = 0.407 + effectLevel(StatusEffects.JUMP_BOOST) * 0.1;
                    Vec3d velocity = mc.player.getVelocity();
                    mc.player.setVelocity(velocity.x, y, velocity.z);
                }
                hopLoad++;
                slowHop = hopLoad > 1.0 && !slowHop;
                hopLoad = Math.min(hopLoad, 1.12);
            }
        }

        if (phase == Phase.BOOST) slowAfterSetback = !setbackElapsed();
        double direction = MovementUtil.getDirection();
        double speed = moveSpeed();
        event.setX(-Math.sin(direction) * speed);
        event.setY(y);
        event.setZ(Math.cos(direction) * speed);
        advance();
        return !collided;
    }

    private boolean isGroundWithin() {
        return mc.player.isOnGround() || mc.world.getBlockCollisions(mc.player,
                mc.player.getBoundingBox().offset(0.0, -0.01, 0.0)).iterator().hasNext();
    }

    private void recover(int ticks) {
        phase = Phase.RECOVER;
        phaseTicks = ticks;
    }

    private void advance() {
        switch (phase) {
            case RECOVER -> { if (--phaseTicks == 0) phase = Phase.BOOST; }
            case BOOST -> { phase = Phase.AIR; phaseTicks = 1; }
            case AIR -> phaseTicks++;
        }
    }

    private double moveSpeed() {
        if (mc.player.isTouchingWater() || mc.player.isInLava()) return 0.12;
        int speedLevel = effectLevel(StatusEffects.SPEED);
        double speed = phase == Phase.RECOVER ? baseSpeed()
                : (hopSpeed(speedLevel) + 0.0331) * 0.91
                * (stairTicks > 0 ? 0.7 - speedLevel * 0.1 : 1.0);
        return slowHop ? speed * 0.95 : speed;
    }

    private double hopSpeed(int level) {
        if (slowAfterSetback) return phase == Phase.BOOST ? 0.0 : 0.2;
        double floor = baseSpeed() + 0.028 * level;
        if (!setbackElapsed() || collided) return floor;
        double first = 0.4145 + level / 12.5;
        double speed = phase == Phase.BOOST ? 0.64 + (level + 0.028 * level) * 0.134
                : phaseTicks == 1 ? first : first - phaseTicks / 250.0;
        return Math.max(speed, floor);
    }

    private double baseSpeed() {
        return 0.2873 * (1.0 + 0.2 * effectLevel(StatusEffects.SPEED))
                / (1.0 + 0.2 * effectLevel(StatusEffects.SLOWNESS));
    }

    private boolean setbackElapsed() {
        return lastSetback + 500 < System.currentTimeMillis();
    }

    private int effectLevel(RegistryEntry<StatusEffect> effect) {
        StatusEffectInstance instance = mc.player.getStatusEffect(effect);
        return instance == null ? 0 : instance.getAmplifier() + 1;
    }
}
