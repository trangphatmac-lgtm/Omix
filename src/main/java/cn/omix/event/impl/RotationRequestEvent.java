package cn.omix.event.impl;

import cn.omix.event.base.Event;
import cn.omix.management.rotation.RotationRequest;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Collected synchronously after modules calculate their LivingUpdate rotations. */
public final class RotationRequestEvent extends Event {
    private static final Comparator<RotationRequest> ORDER = Comparator
            .comparingInt(RotationRequest::priority).reversed()
            .thenComparing(RotationRequest::owner);
    private final Map<String, RotationRequest> requests = new HashMap<>();
    private boolean resolved;

    /** Highest priority wins; equal priorities use owner name, independent of listener order. */
    public void submit(RotationRequest request) {
        if (resolved) throw new IllegalStateException("Rotation collection has ended");
        Objects.requireNonNull(request, "request");
        requests.put(request.owner(), request);
    }

    public Selection resolve(float cameraYaw, float cameraPitch) {
        resolved = true;
        RotationRequest winner = requests.values().stream().min(ORDER).orElse(null);
        if (winner == null) return null;

        float yaw = winner.yaw();
        float pitch = winner.pitch();
        boolean continuousYaw = winner.continuousYaw();
        if (winner.axes() == RotationRequest.Axes.YAW_ONLY) {
            pitch = cameraPitch;
        } else if (winner.axes() == RotationRequest.Axes.PITCH_ONLY) {
            RotationRequest yawSource = requests.values().stream()
                    .filter(request -> request.axes() != RotationRequest.Axes.PITCH_ONLY)
                    .min(ORDER).orElse(null);
            yaw = yawSource == null ? cameraYaw : yawSource.yaw();
            continuousYaw |= yawSource != null && yawSource.continuousYaw();
        }
        return new Selection(winner, yaw, pitch, continuousYaw);
    }

    public record Selection(RotationRequest request, float yaw, float pitch, boolean continuousYaw) {
        public float[] rotations() {
            return new float[]{yaw, pitch};
        }
    }
}
