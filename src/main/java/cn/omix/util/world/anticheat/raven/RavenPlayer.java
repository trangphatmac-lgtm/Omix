package cn.omix.util.world.anticheat.raven;

import java.util.List;
import java.util.UUID;

/** Client-thread snapshot. All players share one observation tick clock. */
public final class RavenPlayer {
    public final Object identity;
    public final UUID uuid;
    public final String name;
    public String displayName;
    public RavenVec3 position = new RavenVec3(0, 0, 0), lastPosition = position;
    public float yaw, pitch, prevYaw, prevPitch, health = 20, absorption;
    public int ticks, swing, hurtTime, maxHurtTime = 10;
    public boolean sneaking, sprinting, using, blocking, collided, dead, burning, edge,
            ground, water, lava, ladder, creative, invisible, suspect, bot, teammate, discontinuity, consumed;
    public double groundDistance;
    public RavenPlayer riding;
    public RavenItem heldItem;
    public List<Object[]> effects = List.of();
    public RavenPlayer(Object identity, UUID uuid, String name) {
        this.identity = identity; this.uuid = uuid; this.name = name; this.displayName = name;
    }
    public int getTicksExisted() { return ticks; }
    public boolean isSneaking() { return sneaking; }
    public boolean isSprinting() { return sprinting; }
    public boolean isUsingItem() { return using; }
    public int getSwingProgress() { return swing; }
    public boolean isCollided() { return collided; }
    public boolean isDead() { return dead; }
    public boolean isBurning() { return burning; }
    public boolean isOnEdge() { return edge; }
    public RavenVec3 getPosition() { return position; }
    public RavenVec3 getLastPosition() { return lastPosition; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public float getPrevYaw() { return prevYaw; }
    public float getPrevPitch() { return prevPitch; }
    public boolean onGround() { return ground; }
    public String getName() { return name; }
    public String getUUID() { return uuid.toString(); }
    public String getDisplayName() { return displayName; }
    public int getHurtTime() { return hurtTime; }
    public int getMaxHurtTime() { return maxHurtTime; }
    public RavenPlayer getRidingEntity() { return riding; }
    public RavenItem getHeldItem() { return heldItem; }
    public boolean isInWater() { return water; }
    public boolean isInLava() { return lava; }
    public boolean isOnLadder() { return ladder; }
    public boolean isCreative() { return creative; }
    public boolean isInvisible() { return invisible; }
    public float getHealth() { return health; }
    public float getAbsorption() { return absorption; }
    public List<Object[]> getPotionEffects() { return effects; }
    public double distanceToGround() { return groundDistance; }
}
