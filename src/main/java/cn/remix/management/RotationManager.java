package cn.remix.management;

import cn.remix.event.base.annotation.EventPriority;
import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.*;
import cn.remix.management.movement.MovementCorrection;
import cn.remix.module.impl.combat.Aura;
import cn.remix.module.impl.world.ScaffoldOld;
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
        ScaffoldOld scaffoldOld = instance.getModuleManager().getModule(ScaffoldOld.class);

        if (scaffoldOld.isEnabled() && scaffoldOld.isCanRotation() && scaffoldOld.getRotations() != null) {
            setRotations(scaffoldOld.getRotations(), scaffoldOld.getRotationSpeed().getValue(), scaffoldOld.getMovementFix().getValue() ? MovementCorrection.Silent : MovementCorrection.None);
        } else if (aura.isEnabled() && aura.getTarget() != null && aura.getRotations() != null) {
            setRotations(aura.getRotations(), aura.getRotationSpeed().getValue(), aura.getMovementFixMode().is("None") ? MovementCorrection.None : (aura.getMovementFixMode().is("Silent") ? MovementCorrection.Silent : MovementCorrection.Strict));
        } else {
            enabled = false;
        }


        lastRotations = currentRotations;
        currentRotations = RotationUtil.getSmoothRotation(lastRotations, targetRotations, rotationSpeed + Math.random());
        mc.gameRenderer.updateCrosshairTarget(1.0f);
    }

    @EventTarget
    @EventPriority(999)
    public void onLook(LookEvent e) {
        if (mc.player == null) return;

        if (canRotation()) {
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

        if (canRotation() && correctMovement != MovementCorrection.None) {
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
}
