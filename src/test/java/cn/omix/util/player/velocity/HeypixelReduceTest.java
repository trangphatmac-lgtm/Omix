package cn.omix.util.player.velocity;

import cn.omix.module.impl.combat.Velocity;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class HeypixelReduceTest {
    @ParameterizedTest
    @CsvSource({"0,0", "999.999,0", "1000,3", "1999.999,3", "2000,4", "9999.999,4", "10000,5", "20000,5"})
    void automaticCountPreservesReferenceThresholds(double strength, int count) {
        assertEquals(count, HeypixelReduce.attackCount(strength, true, 20));
    }

    @Test
    void strengthUsesVerticalComponentAndIgnoresZ() {
        assertEquals(2000, HeypixelReduce.velocityStrength(new Vec3d(0, 0.25, 0)));
        assertEquals(0, HeypixelReduce.velocityStrength(new Vec3d(0, 0, 2)));
        assertEquals(4000, HeypixelReduce.velocityStrength(new Vec3d(-0.3, -0.4, 99)), 1.0E-9);
    }

    @Test
    void manualCountOverridesBothWeakAndStrongVelocity() {
        assertEquals(0, HeypixelReduce.attackCount(20000, false, 0));
        assertEquals(4, HeypixelReduce.attackCount(0, false, 4));
        assertEquals(20, HeypixelReduce.attackCount(20000, false, 20));
    }

    @Test
    void newModePreservesDefaultsAndHidesItsSettingsInExistingModes() {
        Velocity velocity = new Velocity();
        assertEquals("Normal", velocity.getMode().getValue());
        assertArrayEquals(new String[]{"Normal", "Packet", "Reduce", "Grim Full", "Heypixel Reduce"}, velocity.getMode().getModes());
        assertFalse(velocity.getAutoAttackCount().isVisible());
        velocity.getMode().setValue("Heypixel Reduce");
        assertTrue(velocity.getAutoAttackCount().getValue());
        assertTrue(velocity.getAutoAttackCount().isVisible());
        assertFalse(velocity.getAttackCount().isVisible());
        assertEquals(4F, velocity.getAttackCount().getValue());
        assertEquals("PerTick", velocity.getAttackMode().getValue());
        assertEquals(10F, velocity.getAlinkTargetRange().getValue());
        assertEquals(60F, velocity.getAlinkMaxDelay().getValue());
        assertFalse(velocity.getRequireKillAura().getValue());
        assertFalse(velocity.getDebug().getValue());
        velocity.getAutoAttackCount().setValue(false);
        assertTrue(velocity.getAttackCount().isVisible());
        velocity.getMode().setValue("Packet");
        assertFalse(velocity.getAttackCount().isVisible());
        assertFalse(velocity.getAttackMode().isVisible());
        assertFalse(velocity.getAlinkTargetRange().isVisible());
        assertFalse(velocity.getAlinkMaxDelay().isVisible());
        assertFalse(velocity.getRequireKillAura().isVisible());
        assertFalse(velocity.getDebug().isVisible());
        assertTrue(velocity.getHorizontal().isVisible());
        assertTrue(velocity.getVertical().isVisible());
    }

    @Test
    void idleLifecycleCanBeResetWithoutAWorld() {
        Velocity velocity = new Velocity();
        velocity.getMode().setValue("Heypixel Reduce");
        velocity.onEnable();
        assertEquals("Heypixel Reduce", velocity.getSuffix());
        assertFalse(velocity.isAttacking());
        assertEquals(0, velocity.getHitSelectSkips());
        assertFalse(velocity.consumeHitSelectSkip());
        velocity.onDisable();
        assertFalse(velocity.isAttacking());
        velocity.getMode().setValue("Reduce");
        assertEquals(0, velocity.getHitSelectSkips());
        assertFalse(velocity.consumeHitSelectSkip());
    }
}
