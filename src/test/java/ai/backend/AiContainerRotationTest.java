package ai.backend;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AiContainerRotationTest {
    @Test
    void aimsAtHorizontalAndVerticalTargets() {
        assertAim(new Vec3d(0, 0, 1), 0, 0);
        assertAim(new Vec3d(1, 0, 0), -90, 0);
        assertAim(new Vec3d(-1, 0, 0), 90, 0);
        assertAim(new Vec3d(0, 1, 1), 0, -45);
        assertAim(new Vec3d(0, -1, 1), 0, 45);
        assertEquals(-90, AiContainerRotation.toward(Vec3d.ZERO, new Vec3d(0, 1, 0)).pitch(), 0.001);
    }

    @Test
    void waitsForMovementEvenIfTheLastKnownRotationAlreadyMatches() {
        var rotation = new AiContainerRotation(30, -20);
        assertFalse(rotation.ready(false, 30, -20));
        assertTrue(rotation.ready(true, 30, -20));
    }

    @Test
    void rejectsUnsentOrOverriddenAnglesAndAcceptsWrappedYaw() {
        var rotation = new AiContainerRotation(30, -20);
        assertFalse(rotation.ready(true, 0, -20));
        assertFalse(rotation.ready(true, 30, 0));
        assertTrue(rotation.ready(true, 390, -20));
        assertTrue(rotation.ready(true, -330, -20));
        assertFalse(rotation.ready(true, Float.NaN, -20));
    }

    @Test
    void usesEyePositionRatherThanWorldOrigin() {
        var rotation = AiContainerRotation.toward(new Vec3d(198.5, 63.62, 195.5), new Vec3d(198.5, 63.62, 197.5));
        assertEquals(0, rotation.yaw(), 0.001);
        assertEquals(0, rotation.pitch(), 0.001);
    }

    private static void assertAim(Vec3d target, float yaw, float pitch) {
        var rotation = AiContainerRotation.toward(Vec3d.ZERO, target);
        assertEquals(yaw, rotation.yaw(), 0.001);
        assertEquals(pitch, rotation.pitch(), 0.001);
    }
}
