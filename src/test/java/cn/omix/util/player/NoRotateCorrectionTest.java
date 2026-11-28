package cn.omix.util.player;

import net.minecraft.entity.EntityPosition;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NoRotateCorrectionTest {
    @Test
    void preservesBothAbsoluteAndRelativeAnglesWithoutChangingMovement() {
        EntityPosition change = new EntityPosition(new Vec3d(2, 65, -3),
                new Vec3d(0.2, 0.4, 0.6), 125, -45);
        Set<PositionFlag> relatives = Set.of(PositionFlag.X, PositionFlag.Y_ROT);

        EntityPosition current = new EntityPosition(new Vec3d(10, 64, 5), Vec3d.ZERO, 30, 20);
        EntityPosition adjusted = NoRotateCorrection.preserveLook(change, relatives, current);
        EntityPosition applied = EntityPosition.apply(current, adjusted, relatives);

        assertEquals(change.position(), adjusted.position());
        assertEquals(change.deltaMovement(), adjusted.deltaMovement());
        assertEquals(new Vec3d(12, 65, -3), applied.position());
        assertEquals(change.deltaMovement(), applied.deltaMovement());
        assertEquals(30, applied.yaw());
        assertEquals(20, applied.pitch());
        assertEquals(30, NoRotateCorrection.preservedAngle(30, false));
        assertEquals(0, NoRotateCorrection.preservedAngle(30, true));
    }

    @Test
    void rotatedRelativeVelocityStillMatchesVanilla() {
        EntityPosition current = new EntityPosition(Vec3d.ZERO, new Vec3d(1, 2, 3), 20, 10);
        EntityPosition change = new EntityPosition(new Vec3d(5, 6, 7),
                new Vec3d(0.3, 0.4, 0.5), 80, -30);
        Set<PositionFlag> relatives = Set.of(PositionFlag.ROTATE_DELTA,
                PositionFlag.DELTA_X, PositionFlag.DELTA_Z, PositionFlag.Y_ROT);

        EntityPosition vanilla = EntityPosition.apply(current, change, relatives);
        EntityPosition preserved = EntityPosition.apply(current,
                NoRotateCorrection.preserveLook(change, relatives, current), relatives);

        assertEquals(vanilla.position(), preserved.position());
        assertEquals(vanilla.deltaMovement().x, preserved.deltaMovement().x, 1e-6);
        assertEquals(vanilla.deltaMovement().y, preserved.deltaMovement().y, 1e-6);
        assertEquals(vanilla.deltaMovement().z, preserved.deltaMovement().z, 1e-6);
        assertEquals(current.yaw(), preserved.yaw());
        assertEquals(current.pitch(), preserved.pitch());
    }

    @Test
    void flagOnlyUsesResolvedPositionAndStrictTenBlockRadius() {
        Vec3d current = new Vec3d(100, 64, 100);
        Set<PositionFlag> relatives = Set.of(PositionFlag.X, PositionFlag.Z);
        assertTrue(NoRotateCorrection.isNearby(
                new EntityPosition(new Vec3d(3, 64, 4), Vec3d.ZERO, 0, 0),
                relatives, current));
        assertFalse(NoRotateCorrection.isNearby(
                new EntityPosition(new Vec3d(6, 64, 8), Vec3d.ZERO, 0, 0),
                relatives, current));
        assertFalse(NoRotateCorrection.isNearby(
                new EntityPosition(new Vec3d(200, 64, 100), Vec3d.ZERO, 0, 0),
                Set.of(), current));
    }
}
