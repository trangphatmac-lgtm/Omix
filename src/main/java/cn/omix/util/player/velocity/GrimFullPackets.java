package cn.omix.util.player.velocity;

import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;

/** Uses server-reported causes; a velocity packet alone does not identify its source. */
public final class GrimFullPackets {
    private static final int SOURCE_MATCH_TICKS = 5;
    private Object player;
    private int lastAge;
    private int impulseAge;
    private boolean pendingImpulse;

    public synchronized void reset() {
        player = null;
        lastAge = 0;
        impulseAge = 0;
        pendingImpulse = false;
    }

    /** Match a recent source to one self velocity packet; pings never consume the marker. */
    public synchronized boolean shouldPassVelocity(Object currentPlayer, int age, Packet<?> packet, int playerId) {
        if (currentPlayer == null) {
            reset();
            return false;
        }
        if (player != currentPlayer || age < lastAge) {
            reset();
            player = currentPlayer;
        }
        lastAge = age;
        if (pendingImpulse && (long) age - impulseAge >= SOURCE_MATCH_TICKS) pendingImpulse = false;

        if (isSpecialImpulse(packet, playerId)) {
            pendingImpulse = true;
            impulseAge = age;
        } else if (packet instanceof EntityDamageS2CPacket damage && damage.entityId() == playerId) {
            // Do not let an explosion without a following velocity exempt a later melee hit.
            pendingImpulse = false;
        }

        if (packet instanceof EntityVelocityUpdateS2CPacket velocity && velocity.getEntityId() == playerId) {
            boolean exempt = pendingImpulse;
            pendingImpulse = false;
            return exempt;
        }
        return false;
    }

    public static boolean isSpecialImpulse(Packet<?> packet, int playerId) {
        if (packet instanceof ExplosionS2CPacket explosion) {
            // Wind charges can cause knockback without a damage notification.
            return hasExplosionKnockback(explosion.playerKnockback());
        }
        if (packet instanceof EntityDamageS2CPacket damage && damage.entityId() == playerId) {
            var type = damage.sourceType();
            return type.matchesKey(DamageTypes.FIREBALL)
                    || type.matchesKey(DamageTypes.UNATTRIBUTED_FIREBALL)
                    || type.matchesKey(DamageTypes.EXPLOSION)
                    || type.matchesKey(DamageTypes.PLAYER_EXPLOSION)
                    || type.matchesKey(DamageTypes.WIND_CHARGE)
                    || type.matchesKey(DamageTypes.ENDER_PEARL);
        }
        return false;
    }

    static boolean hasExplosionKnockback(Optional<Vec3d> knockback) {
        return knockback.filter(velocity -> velocity.lengthSquared() > 0).isPresent();
    }
}
