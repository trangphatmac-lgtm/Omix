package cn.omix.util.player.velocity;

import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryOwner;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class GrimFullPacketsTest {
    private static final int PLAYER_ID = 7;
    private final Object player = new Object();
    private final GrimFullState state = new GrimFullState();
    private final GrimFullPackets packets = new GrimFullPackets();

    static Stream<RegistryKey<DamageType>> specialSources() {
        return Stream.of(DamageTypes.FIREBALL, DamageTypes.UNATTRIBUTED_FIREBALL,
                DamageTypes.EXPLOSION, DamageTypes.PLAYER_EXPLOSION,
                DamageTypes.WIND_CHARGE, DamageTypes.ENDER_PEARL);
    }

    private static EntityDamageS2CPacket damage(int entityId, RegistryKey<DamageType> type) {
        var entry = RegistryEntry.Reference.standAlone(new RegistryEntryOwner<DamageType>() {}, type);
        return new EntityDamageS2CPacket(entityId, entry, -1, -1, Optional.empty());
    }

    private GrimFullState.Decision receive(Packet<?> packet, int age) {
        return state.decide(player, age, age * 50_000_000L, false,
                packets.shouldPassVelocity(player, age, packet, PLAYER_ID), false);
    }

    @ParameterizedTest
    @MethodSource("specialSources")
    void specialDamageExemptsOnlyTheFollowingSelfVelocity(RegistryKey<DamageType> type) {
        var ping = new CommonPingS2CPacket(-1);
        receive(ping, 0);
        assertTrue(receive(ping, 200).cancelPing());
        var cause = damage(PLAYER_ID, type);
        assertTrue(GrimFullPackets.isSpecialImpulse(cause, PLAYER_ID));
        assertTrue(receive(cause, 201).cancelPing());
        var velocity = new EntityVelocityUpdateS2CPacket(PLAYER_ID, new Vec3d(1, 1, 0));
        assertTrue(receive(ping, 202).cancelPing());
        receive(new EntityVelocityUpdateS2CPacket(PLAYER_ID + 1, Vec3d.ZERO), 202);
        assertFalse(receive(velocity, 202).cancelVelocity());
        assertTrue(receive(velocity, 202).cancelVelocity());
        assertFalse(GrimFullPackets.isSpecialImpulse(damage(PLAYER_ID + 1, type), PLAYER_ID));
    }

    @Test
    void windBurstWithoutDamageExemptsVelocityWithoutChangingPing() {
        receive(new CommonPingS2CPacket(1), 0);
        // Test the decoded field: constructing ExplosionS2CPacket requires the Fabric registry runtime.
        boolean burst = GrimFullPackets.hasExplosionKnockback(Optional.of(new Vec3d(0, 0.8, 0)));
        assertTrue(burst);
        var decision = state.decide(player, 200, 10_000_000_000L, false, burst, false);
        assertTrue(decision.cancelPing());
        assertFalse(decision.cancelVelocity());
        assertTrue(receive(new CommonPingS2CPacket(2), 201).cancelPing());
    }

    @Test
    void unrelatedPacketsDoNotOpenExemption() {
        assertFalse(GrimFullPackets.hasExplosionKnockback(Optional.empty()));
        assertFalse(GrimFullPackets.hasExplosionKnockback(Optional.of(Vec3d.ZERO)));
        for (Packet<?> packet : new Packet<?>[]{
                damage(PLAYER_ID, DamageTypes.PLAYER_ATTACK), damage(PLAYER_ID, DamageTypes.FALL),
                damage(PLAYER_ID, DamageTypes.ARROW), new CommonPingS2CPacket(1),
                new KeepAliveS2CPacket(1), new EntityVelocityUpdateS2CPacket(PLAYER_ID, Vec3d.ZERO)}) {
            assertFalse(GrimFullPackets.isSpecialImpulse(packet, PLAYER_ID));
        }
        receive(new CommonPingS2CPacket(1), 0);
        var result = receive(damage(PLAYER_ID, DamageTypes.PLAYER_ATTACK), 200);
        assertTrue(result.cancelPing());
        assertTrue(result.cancelVelocity());
    }

    @Test
    void unusedSourceExpiresAndOrdinaryDamageClearsIt() {
        var velocity = new EntityVelocityUpdateS2CPacket(PLAYER_ID, Vec3d.ZERO);
        receive(damage(PLAYER_ID, DamageTypes.EXPLOSION), 0);
        assertTrue(receive(velocity, 5).cancelVelocity());
        receive(damage(PLAYER_ID, DamageTypes.EXPLOSION), 10);
        receive(damage(PLAYER_ID, DamageTypes.PLAYER_ATTACK), 10);
        assertTrue(receive(velocity, 10).cancelVelocity());
        receive(damage(PLAYER_ID, DamageTypes.WIND_CHARGE), 20);
        assertFalse(receive(velocity, 24).cancelVelocity());
    }

    @Test
    void duplicateSourceNotificationsDoNotAccumulateExtraExemptions() {
        receive(damage(PLAYER_ID, DamageTypes.FIREBALL), 0);
        receive(damage(PLAYER_ID, DamageTypes.PLAYER_EXPLOSION), 0);
        var velocity = new EntityVelocityUpdateS2CPacket(PLAYER_ID, Vec3d.ZERO);
        assertFalse(receive(velocity, 0).cancelVelocity());
        assertTrue(receive(velocity, 0).cancelVelocity());
    }

    @Test
    void sourceMarkerDoesNotSurviveResetOrPlayerReplacement() {
        var cause = damage(PLAYER_ID, DamageTypes.ENDER_PEARL);
        var velocity = new EntityVelocityUpdateS2CPacket(PLAYER_ID, Vec3d.ZERO);
        receive(cause, 0);
        packets.reset();
        assertTrue(receive(velocity, 0).cancelVelocity());
        receive(cause, 0);
        assertFalse(packets.shouldPassVelocity(new Object(), 0, velocity, PLAYER_ID));
    }
}
