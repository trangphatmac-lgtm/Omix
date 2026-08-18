package cn.omix.module.impl.move;

import cn.omix.event.base.annotation.EventPriority;
import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.AttackEvent;
import cn.omix.event.impl.LivingUpdateEvent;
import cn.omix.event.impl.TickEvent;
import cn.omix.event.impl.WorldEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.module.value.impl.ModeValue;
import cn.omix.module.value.impl.NumberValue;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;

public class KeepSprint extends Module {
    public final ModeValue mode = new ModeValue("Mode", "Vanilla", "Vanilla", "Legit", "Grim", "Buffer");

    // Kept as Motion instead of Slowdown so existing KeepSprint configs remain compatible.
    public final NumberValue motion = new NumberValue("Motion", 1, 0, 1, .1f, () -> mode.is("Vanilla"));
    public final BoolValue groundOnly = new BoolValue("Ground Only", false, () -> mode.is("Vanilla"));
    public final BoolValue reachOnly = new BoolValue("Reach Only", false, () -> mode.is("Vanilla"));

    public final BoolValue onHurt = new BoolValue("On Hurt", false, () -> mode.is("Legit") || mode.is("Buffer"));

    public final BoolValue autoFactor = new BoolValue("Auto Factor", true, () -> mode.is("Grim"));
    public final NumberValue offsetBudget = new NumberValue(
            "Offset Budget", 50, 0, 100, 1,
            () -> mode.is("Grim") && autoFactor.getValue()
    );
    public final NumberValue factor = new NumberValue(
            "Factor", 65, 0, 100, 1,
            () -> mode.is("Grim") && !autoFactor.getValue()
    );
    public final BoolValue grimGroundOnly = new BoolValue("Grim Ground Only", true, () -> mode.is("Grim"));

    private int disableSprintTicks;
    private Entity bufferedTarget;
    private int bufferDelayTicks;
    private boolean replayingBufferedAttack;

    public KeepSprint() {
        super("KeepSprint", Category.Move);
    }

    public boolean isBufferMode() {
        return mode.is("Buffer")
                && mc.player != null
                && (mc.player.hurtTime == 0 || onHurt.getValue());
    }

    public boolean shouldKeepSprint() {
        if (mc.player == null) return false;

        return switch (mode.getValue()) {
            case "Legit" -> false;
            case "Grim" -> !grimGroundOnly.getValue() || mc.player.isOnGround();
            case "Buffer" -> true;
            default -> (!groundOnly.getValue() || mc.player.isOnGround())
                    && (!reachOnly.getValue() || isOutsideVanillaReach());
        };
    }

    public boolean isAttackNoSlow() {
        return isEnabled() && shouldKeepSprint();
    }

    public boolean shouldOverrideHitSlowdown() {
        return isEnabled()
                && (mode.is("Vanilla") || mode.is("Grim"))
                && shouldKeepSprint();
    }

    public double getSlowFactor() {
        return switch (mode.getValue()) {
            case "Legit" -> 0.6;
            case "Grim" -> getGrimFactor();
            case "Buffer" -> 1.0;
            default -> 0.6 + 0.4 * motion.getValue();
        };
    }

    /**
     * Queues Buffer attacks for the next client tick. The interaction-manager mixin
     * calls this before it emits AttackEvent or sends the attack packet.
     */
    public boolean tryBufferAttack(Entity target) {
        if (!isEnabled() || !isBufferMode() || replayingBufferedAttack || target == null) {
            return false;
        }

        if (bufferedTarget == null) {
            bufferedTarget = target;
            bufferDelayTicks = 1;
            mc.options.sprintKey.setPressed(false);
            mc.player.setSprinting(false);
        }

        return true;
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (mode.is("Legit")) {
            disableSprintTicks = 3;
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (mc.player == null) return;

        updateSuffix();
        if (!mode.is("Legit") || disableSprintTicks < 0) return;

        if (onHurt.getValue() || mc.player.hurtTime == 0) {
            mc.options.sprintKey.setPressed(false);
            mc.player.setSprinting(false);
        }
        disableSprintTicks--;
    }

    @EventTarget
    @EventPriority(0)
    public void onTick(TickEvent event) {
        if (bufferedTarget == null || --bufferDelayTicks > 0) return;

        Entity target = bufferedTarget;
        clearBuffer();
        if (mc.player == null
                || mc.world == null
                || mc.interactionManager == null
                || !mode.is("Buffer")
                || !target.isAlive()
                || target.getEntityWorld() != mc.world) {
            return;
        }

        replayingBufferedAttack = true;
        try {
            mc.interactionManager.attackEntity(mc.player, target);
        } finally {
            replayingBufferedAttack = false;
        }
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        clearBuffer();
    }

    @Override
    public void onEnable() {
        disableSprintTicks = 0;
        clearBuffer();
    }

    @Override
    public void onDisable() {
        clearBuffer();
        if (mc.player != null && mode.is("Legit")) {
            int keyCode = InputUtil.fromTranslationKey(mc.options.sprintKey.getBoundKeyTranslationKey()).getCode();
            mc.options.sprintKey.setPressed(InputUtil.isKeyPressed(mc.getWindow(), keyCode));
        }
    }

    private boolean isOutsideVanillaReach() {
        HitResult target = mc.crosshairTarget;
        Entity camera = mc.getCameraEntity();
        return target != null
                && camera != null
                && target.getPos().distanceTo(camera.getCameraPosVec(1.0F)) > 3.0;
    }

    private double getGrimFactor() {
        if (!autoFactor.getValue() || mc.player == null) {
            return factor.getValue() / 100.0;
        }

        double speed = Math.hypot(mc.player.getVelocity().x, mc.player.getVelocity().z);
        if (speed <= 0.0) return 1.0;

        double budget = 0.001 * offsetBudget.getValue() / 100.0;
        double maxFactor = speed * 0.6 < 0.005
                ? budget / speed
                : 0.6 + budget / speed;
        return Math.min(1.0, maxFactor);
    }

    private void updateSuffix() {
        if (mode.is("Grim")) {
            double displayedFactor = autoFactor.getValue() ? getSlowFactor() * 100.0 : factor.getValue();
            setSuffix(String.format("Grim %.0f%%", displayedFactor));
        } else {
            setSuffix(mode.getValue());
        }
    }

    private void clearBuffer() {
        bufferedTarget = null;
        bufferDelayTicks = 0;
    }
}
