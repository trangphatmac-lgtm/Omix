package cn.omix.util.player;

import net.minecraft.entity.EntityPosition;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.util.math.Vec3d;

import java.util.Set;

/** Keeps the local view while letting vanilla apply position and velocity corrections. */
public final class NoRotateCorrection {
    private NoRotateCorrection() {
    }

    public static boolean isNearby(EntityPosition change, Set<PositionFlag> relatives, Vec3d currentPosition) {
        Vec3d position = change.position();
        double x = position.x + (relatives.contains(PositionFlag.X) ? currentPosition.x : 0);
        double y = position.y + (relatives.contains(PositionFlag.Y) ? currentPosition.y : 0);
        double z = position.z + (relatives.contains(PositionFlag.Z) ? currentPosition.z : 0);
        return currentPosition.squaredDistanceTo(x, y, z) < 100.0;
    }

    public static EntityPosition preserveLook(EntityPosition change, Set<PositionFlag> relatives,
                                              EntityPosition current) {
        EntityPosition adjusted = change.withRotation(
                preservedAngle(current.yaw(), relatives.contains(PositionFlag.Y_ROT)),
                preservedAngle(current.pitch(), relatives.contains(PositionFlag.X_ROT)));
        if (!relatives.contains(PositionFlag.ROTATE_DELTA)) return adjusted;

        // ROTATE_DELTA uses the resolved angle to rotate existing velocity. Keep the
        // vanilla result even though the applied angle is replaced for the camera.
        Vec3d vanillaVelocity = EntityPosition.apply(current, change, relatives).deltaMovement();
        Vec3d adjustedVelocity = EntityPosition.apply(current, adjusted, relatives).deltaMovement();
        return new EntityPosition(change.position(),
                change.deltaMovement().add(vanillaVelocity.subtract(adjustedVelocity)),
                adjusted.yaw(), adjusted.pitch());
    }

    public static float preservedAngle(float currentAngle, boolean relative) {
        return relative ? 0.0F : currentAngle;
    }
}
