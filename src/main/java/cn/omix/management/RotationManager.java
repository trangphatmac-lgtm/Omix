package cn.omix.management;

import cn.omix.event.base.annotation.EventPriority;
import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.*;
import cn.omix.management.movement.MovementCorrection;
import cn.omix.management.rotation.RotationRequest;
import injection.accessor.ClientPlayerEntityAccessor;
import cn.omix.util.IMinecraft;
import cn.omix.util.player.MovementUtil;
import cn.omix.util.player.RotationUtil;

/**
 * RotationManager
 * @author DSJ
 */
public class RotationManager implements IMinecraft {
    public static float[] currentRotations;
    public static float[] targetRotations;
    public static float[] lastRotations;

    public static MovementCorrection correctMovement;
    private static boolean enabled;
    private static RotationRequest activeRequest;
    private boolean continuousYawSelected;
    private boolean previousMotionUsedContinuousYaw;

    public RotationManager() {
        instance.getEventManager().register(this);
    }

    @EventTarget
    @EventPriority(999)
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (mc.player == null || mc.world == null) {
            reset();
            return;
        }

        RotationRequestEvent requests = new RotationRequestEvent();
        instance.getEventManager().call(requests);
        RotationRequestEvent.Selection selection = requests.resolve(mc.player.getYaw(), mc.player.getPitch());
        activeRequest = selection == null ? null : selection.request();
        enabled = activeRequest != null;
        continuousYawSelected = selection != null && selection.continuousYaw();
        if (enabled) correctMovement = activeRequest.movementCorrection();

        if (currentRotations == null) {
            currentRotations = new float[]{mc.player.getYaw(), mc.player.getPitch()};
        }
        lastRotations = currentRotations.clone();
        if (enabled) {
            targetRotations = selection.rotations();
            // Yaw-only modules may have sampled pitch earlier in the tick. Preserve
            // that input to the legacy smoothing curve before releasing the pitch axis.
            if (activeRequest.axes() == RotationRequest.Axes.YAW_ONLY) {
                targetRotations[1] = activeRequest.pitch();
            }
            currentRotations = activeRequest.instant()
                    ? targetRotations.clone()
                    : RotationUtil.getSmoothRotation(lastRotations, targetRotations, activeRequest.speed() + Math.random());
            if (activeRequest.axes() == RotationRequest.Axes.YAW_ONLY) {
                currentRotations[1] = mc.player.getPitch();
                lastRotations[1] = mc.player.lastPitch;
                targetRotations[1] = mc.player.getPitch();
            }
            if (continuousYawSelected) {
                float sentYaw = ((ClientPlayerEntityAccessor) mc.player).getLastYaw();
                currentRotations[0] = nearestYaw(sentYaw, currentRotations[0]);
            }
            if (!activeRequest.silent()) {
                // Pitch-only requests inherit a server yaw, not a camera yaw.
                if (activeRequest.axes() != RotationRequest.Axes.PITCH_ONLY) mc.player.setYaw(currentRotations[0]);
                if (activeRequest.axes() != RotationRequest.Axes.YAW_ONLY) mc.player.setPitch(currentRotations[1]);
            }
        }
        // Preserve the last applied cache until pre-motion, as before. Some placement
        // consumers read it between living update and the movement packet.
        mc.gameRenderer.updateCrosshairTarget(1.0f);
    }

    @EventTarget
    @EventPriority(999)
    public void onLook(LookEvent e) {
        if (mc.player == null) return;

        if (canRotation() && correctMovement != MovementCorrection.Prediction) {
            e.setRotation(currentRotations);
            e.setLastRotation(lastRotations);
        }
    }

    @EventTarget
    @EventPriority(999)
    public void onStrafe(StrafeEvent e) {
        if (mc.player == null) return;

        if (canRotation() && correctMovement != MovementCorrection.None) {
            e.setYaw(currentRotations[0]);
        }
    }

    @EventTarget
    @EventPriority(999)
    public void onJump(JumpEvent e) {
        if (mc.player == null) return;

        if (canRotation()
                && correctMovement != MovementCorrection.None
                && correctMovement != MovementCorrection.Prediction) {
            e.setYaw(currentRotations[0]);
        }
    }

    @EventTarget
    @EventPriority(999)
    public void onMotion(MotionEvent e) {
        if (mc.player == null) return;

        if (e.isPre()) {
            if (!enabled || currentRotations == null || lastRotations == null || targetRotations == null) {
                currentRotations = targetRotations = lastRotations = new float[]{mc.player.getYaw(), mc.player.getPitch()};
            }

            if (canRotation()) {
                e.setYaw(currentRotations[0]);
                e.setPitch(currentRotations[1]);
            }
            if (continuousYawSelected || previousMotionUsedContinuousYaw) {
                float yaw = nearestYaw(
                        ((ClientPlayerEntityAccessor) mc.player).getLastYaw(), e.getYaw());
                e.setYaw(yaw);
                if (!enabled) {
                    // Keep the camera's equivalent full-turn representation on release,
                    // otherwise the next vanilla packet would undo the continuity fix.
                    float offset = yaw - mc.player.getYaw();
                    mc.player.setYaw(yaw);
                    mc.player.lastYaw += offset;
                }
            }
            previousMotionUsedContinuousYaw = continuousYawSelected;
        }
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        reset();
    }

    @EventTarget
    @EventPriority(999)
    public void onMoveInput(MoveInputEvent e) {
        if (canRotation() && correctMovement == MovementCorrection.Silent) {
            MovementUtil.fixMovement(e, currentRotations[0]);
        }
    }

    @EventTarget
    @EventPriority(999)
    public void onRotation(RenderRotationEvent e) {
        if (mc.player == null) return;

        if (canRotation()) {
            e.setRotation(currentRotations);
            e.setLastRotation(lastRotations);
        }
    }

    private void reset() {
        enabled = false;
        activeRequest = null;
        currentRotations = targetRotations = lastRotations = null;
        correctMovement = MovementCorrection.None;
        continuousYawSelected = previousMotionUsedContinuousYaw = false;
    }

    /** Keep the nearest equivalent full-turn representation on acquisition and release. */
    private static float nearestYaw(float reference, float yaw) {
        float delta = (yaw - reference) % 360.0F;
        if (delta >= 180.0F) delta -= 360.0F;
        if (delta < -180.0F) delta += 360.0F;
        return reference + delta;
    }

    public static RotationRequest getActiveRequest() {
        return activeRequest;
    }

    /** Release only this exact request; retain angle caches until normal pre-motion. */
    public static void release(RotationRequest request) {
        if (request != null && activeRequest == request) {
            activeRequest = null;
            enabled = false;
        }
    }

    public static boolean isOwner(String owner) {
        return isRotating() && activeRequest != null && activeRequest.owner().equals(owner);
    }

    private boolean canRotation() {
        return enabled && currentRotations != null && lastRotations != null && targetRotations != null;
    }

    public static boolean isRotating() {
        return enabled && currentRotations != null && lastRotations != null && targetRotations != null;
    }

    public static float getAppliedYaw(float fallback) {
        return isRotating() ? currentRotations[0] : fallback;
    }
}
