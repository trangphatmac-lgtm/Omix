package cn.remix.management;

import cn.remix.event.base.annotation.EventPriority;
import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.*;
import cn.remix.management.movement.MovementCorrection;
import cn.remix.module.impl.combat.Aura;
import cn.remix.module.impl.move.Derp;
import cn.remix.module.impl.move.NoFall;
import cn.remix.module.impl.move.Speed;
import cn.remix.module.impl.player.AntiLava;
import cn.remix.module.impl.world.ScaffoldX;
import cn.remix.module.impl.world.Scaffold;
import cn.remix.util.IMinecraft;
import cn.remix.util.player.MovementUtil;
import cn.remix.util.player.RotationUtil;

/**
 * RotationManager
 * @author DSJ
 */
public class RotationManager implements IMinecraft {
    public static float[] currentRotations;
    public static float[] targetRotations;
    public static float[] lastRotations;

    public static MovementCorrection correctMovement;
    private static double rotationSpeed;
    private static boolean enabled;

    public RotationManager() {
        instance.getEventManager().register(this);
    }

    public static void setRotations(float[] rotations, double rotationSpeed, MovementCorrection correctMovement) {
        RotationManager.targetRotations = rotations;
        RotationManager.rotationSpeed = rotationSpeed;
        RotationManager.correctMovement = correctMovement;

        enabled = true;
    }

    @EventTarget
    @EventPriority(999)
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (mc.player == null) return;

        Aura aura = instance.getModuleManager().getModule(Aura.class);
        Derp derp = instance.getModuleManager().getModule(Derp.class);
        Speed speed = instance.getModuleManager().getModule(Speed.class);
        AntiLava antiLava = instance.getModuleManager().getModule(AntiLava.class);
        ScaffoldX scaffoldX = instance.getModuleManager().getModule(ScaffoldX.class);
        Scaffold scaffold = instance.getModuleManager().getModule(Scaffold.class);
        NoFall noFall = instance.getModuleManager().getModule(NoFall.class);
        boolean derpActive = derp.isEnabled() && derp.getRotations() != null;
        boolean instantRotation = false;

        if (derpActive) {
            setRotations(derp.getRotations(), 0.0, MovementCorrection.None);
            instantRotation = true;
        } else if (antiLava.isEnabled() && antiLava.getRotations() != null) {
            setRotations(antiLava.getRotations(), 180, antiLava.getMovementFix().getValue() ? MovementCorrection.Silent : MovementCorrection.None);
        } else if (scaffoldX.isEnabled() && scaffoldX.isCanRotation() && scaffoldX.getRotations() != null) {
            setRotations(scaffoldX.getRotations(), scaffoldX.getRotationSpeed().getValue(), scaffoldX.getMovementFix().getValue() ? MovementCorrection.Silent : MovementCorrection.None);
        } else if (scaffold.isEnabled() && scaffold.isCanRotation() && scaffold.getRotations() != null) {
            setRotations(scaffold.getRotations(), scaffold.getRotationSpeed(), scaffold.getMovementFix().getValue() ? MovementCorrection.Silent : MovementCorrection.None);
        } else if (aura.isEnabled() && aura.getTarget() != null && aura.getRotations() != null) {
            setRotations(aura.getRotations(), aura.getRotationSpeed().getValue(), aura.getMovementFixMode().is("None") ? MovementCorrection.None : (aura.getMovementFixMode().is("Silent") ? MovementCorrection.Silent : MovementCorrection.Strict));
        } else if (speed.isPredictionRotationActive()) {
            // Myau's `2` is a rotation priority, not a two-degrees-per-tick
            // smoothing speed. Prediction requires movement correction and the
            // applied yaw to use the exact same value in the current tick.
            setRotations(new float[]{speed.getPredictionRotationYaw(), mc.player.getPitch()}, 0.0, MovementCorrection.Prediction);
            instantRotation = true;
        } else {
            enabled = false;
        }

        if (!derpActive && noFall.isGrimSilentRotationActive()) {
            float yaw = enabled && targetRotations != null ? targetRotations[0] : mc.player.getYaw();
            setRotations(new float[]{yaw, 90.0F}, 0.0, MovementCorrection.None);
            instantRotation = true;
        }

        if (currentRotations == null) {
            currentRotations = new float[]{mc.player.getYaw(), mc.player.getPitch()};
        }
        lastRotations = currentRotations.clone();
        if (instantRotation) {
            currentRotations = targetRotations.clone();
        } else if (enabled && targetRotations != null) {
            currentRotations = RotationUtil.getSmoothRotation(lastRotations, targetRotations, rotationSpeed + Math.random());
        }
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
        }
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
