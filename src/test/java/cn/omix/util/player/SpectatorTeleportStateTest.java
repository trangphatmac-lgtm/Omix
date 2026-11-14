package cn.omix.util.player;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpectatorTeleportStateTest {
    private final SpectatorTeleportState state = new SpectatorTeleportState();
    private final Vec3d first = new Vec3d(12.5, 65, -8.5);
    private final Vec3d second = new Vec3d(-20.5, 80.5, 42.5);

    @Test
    void selectionWaitsForSpectatorAndUsesTheLatestClick() {
        assertTrue(state.selectTarget(first));
        assertNull(state.correctionTarget());
        assertNull(state.leaveSpectator());
        assertTrue(state.enterSpectator());
        assertTrue(state.selectTarget(second));
        assertEquals(second, state.leaveSpectator());
        assertTrue(state.isInGrace());
        assertNull(state.leaveSpectator(), "Repeated mode changes must not restart grace");
    }

    @Test
    void missingTargetAllowsVanillaCorrectionsAndExitingDoesNotArmALaterClick() {
        state.enterSpectator();
        assertNull(state.correctionTarget(), "A missing target must not consume a teleport confirmation");
        assertNull(state.leaveSpectator());
        state.selectTarget(first);
        assertNull(state.correctionTarget());
    }

    @Test
    void correctionCanTriggerTeleportBeforeTheGameModeExitPacket() {
        state.enterSpectator();
        state.selectTarget(first);
        assertEquals(first, state.correctionTarget());
        assertNull(state.leaveSpectator());
        assertEquals(first, state.correctionTarget());
    }

    @Test
    void graceLocksTheDestinationForExactlyFiveTicksAndCorrectionsDoNotExtendIt() {
        state.selectTarget(first);
        state.enterSpectator();
        state.leaveSpectator();
        assertFalse(state.selectTarget(second));
        for (int tick = 0; tick < 4; tick++) {
            assertEquals(first, state.correctionTarget());
            assertFalse(state.tickGrace());
            assertTrue(state.isInGrace());
        }
        assertTrue(state.tickGrace());
        assertFalse(state.isInGrace());
        assertNull(state.getTeleportTarget());
        assertNull(state.correctionTarget());
        assertFalse(state.tickGrace());
    }

    @Test
    void repeatedFlyingAbilitiesCannotRearmTheCompletedCycle() {
        state.selectTarget(first);
        assertTrue(state.observeFlying(true));
        assertFalse(state.observeFlying(true));
        state.correctionTarget();
        for (int tick = 0; tick < 5; tick++) state.tickGrace();
        assertFalse(state.observeFlying(true));
        assertNull(state.correctionTarget());
        assertFalse(state.observeFlying(false));
        assertTrue(state.observeFlying(true));
        assertEquals(first, state.correctionTarget());
    }

    @Test
    void anotherSpectatorCycleCanReuseTheTargetWithAutoDisableOff() {
        state.selectTarget(first);
        state.enterSpectator();
        state.leaveSpectator();
        for (int tick = 0; tick < 5; tick++) state.tickGrace();
        assertTrue(state.enterSpectator());
        assertEquals(first, state.leaveSpectator());
    }

    @Test
    void playerReplacementResetsGraceAndWorldOrDisableClearsTheTarget() {
        state.selectTarget(first);
        state.enterSpectator();
        state.correctionTarget();
        state.resetCycle(false, false);
        assertFalse(state.isInGrace());
        assertNull(state.correctionTarget());
        assertEquals(first, state.getTarget());
        state.resetCycle(true, true);
        assertEquals(first, state.correctionTarget());
        state.clear();
        assertNull(state.getTarget());
        assertNull(state.getTeleportTarget());
        assertFalse(state.isInGrace());
        assertNull(state.correctionTarget());
    }
}
