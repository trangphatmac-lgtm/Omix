package ai.backend;

import net.minecraft.util.math.Vec3d;

/** Aim and synchronization gate, independent of a running Minecraft client. */
record AiContainerRotation(float yaw, float pitch) {
    static AiContainerRotation toward(Vec3d eye, Vec3d target) {
        Vec3d delta = target.subtract(eye);
        return new AiContainerRotation((float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90,
                (float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
    }

    boolean ready(boolean movementCompleted, float sentYaw, float sentPitch) {
        double yawDifference = Math.IEEEremainder((double) sentYaw - yaw, 360);
        return movementCompleted && Math.abs(yawDifference) < 0.01 && Math.abs(sentPitch - pitch) < 0.01;
    }
}
