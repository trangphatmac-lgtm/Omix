package cn.omix.management.rotation;

import cn.omix.management.movement.MovementCorrection;

import java.util.Objects;

/** Immutable, client-thread rotation intent for one collection cycle. */
public record RotationRequest(String owner, float yaw, float pitch, double speed, int priority,
                              boolean silent, MovementCorrection movementCorrection,
                              Axes axes, boolean continuousYaw, boolean instant) {
    public enum Axes {
        BOTH,
        /** Rotate yaw while preserving the camera's pitch. */
        YAW_ONLY,
        /** Override pitch, inheriting yaw from the best request that supplies it. */
        PITCH_ONLY
    }

    public RotationRequest {
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException("Missing rotation owner");
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("Rotation angles must be finite");
        }
        if (!Double.isFinite(speed) || speed < 0) {
            throw new IllegalArgumentException("Rotation speed must be finite and non-negative");
        }
        Objects.requireNonNull(movementCorrection, "movementCorrection");
        Objects.requireNonNull(axes, "axes");
    }

    public static Builder builder(String owner, float[] rotations, int priority) {
        if (rotations == null || rotations.length != 2) {
            throw new IllegalArgumentException("Expected yaw and pitch");
        }
        return new Builder(owner, rotations[0], rotations[1], priority);
    }

    public static final class Builder {
        private final String owner;
        private final float yaw;
        private final float pitch;
        private final int priority;
        private double speed = 180.0;
        private boolean silent = true;
        private MovementCorrection movementCorrection = MovementCorrection.None;
        private Axes axes = Axes.BOTH;
        private boolean continuousYaw;
        private Boolean instant;

        private Builder(String owner, float yaw, float pitch, int priority) {
            this.owner = owner;
            this.yaw = yaw;
            this.pitch = pitch;
            this.priority = priority;
        }

        /** Zero defaults to instant application unless instant(false) is specified. */
        public Builder speed(double speed) {
            this.speed = speed;
            return this;
        }

        public Builder silent(boolean silent) {
            this.silent = silent;
            return this;
        }

        /** Explicitly select direct application or the legacy smoothing curve, even at speed zero. */
        public Builder instant(boolean instant) {
            this.instant = instant;
            return this;
        }

        public Builder movementCorrection(MovementCorrection correction) {
            this.movementCorrection = correction;
            return this;
        }

        public Builder axes(Axes axes) {
            this.axes = axes;
            return this;
        }

        public Builder continuousYaw(boolean continuousYaw) {
            this.continuousYaw = continuousYaw;
            return this;
        }

        public RotationRequest build() {
            return new RotationRequest(owner, yaw, pitch, speed, priority, silent,
                    movementCorrection, axes, continuousYaw, instant == null ? speed == 0.0 : instant);
        }
    }
}
