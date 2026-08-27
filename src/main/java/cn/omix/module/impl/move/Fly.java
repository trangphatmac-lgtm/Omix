package cn.omix.module.impl.move;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.LivingUpdateEvent;
import cn.omix.event.impl.MotionEvent;
import cn.omix.event.impl.MoveEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.impl.combat.TPAura;
import cn.omix.module.impl.combat.TargetStrafe;
import cn.omix.module.impl.exploits.Disabler;
import cn.omix.module.value.impl.ModeValue;
import cn.omix.module.value.impl.NumberValue;
import cn.omix.util.player.MovementUtil;

import java.util.concurrent.ThreadLocalRandom;

public final class Fly extends Module {
    public final ModeValue mode = new ModeValue("Mode", "Vanilla", "Vanilla", "SentinelA", "SentinelC");
    private final NumberValue horizontalSpeed = new NumberValue("Horizontal Speed", 3.5, .1, 10, .1);
    private final NumberValue verticalSpeed = new NumberValue("Vertical Speed", .7, .1, 5, .1);
    private int tick;
    private int sentinelCCooldown;

    public Fly() {
        super("Fly", Category.Move);
    }

    @Override
    public void onEnable() {
        tick = 0;
        sentinelCCooldown = 0;
    }

    @Override
    public void onDisable() {
        instance.getPacketManager().getBlink().dispatch(this);
        MovementUtil.stop();
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (mc.player == null || check()) return;
        setSuffix(mode.getValue());

        TPAura tpAura = getModule(TPAura.class);
        if (mode.is("SentinelA") && tpAura != null && tpAura.isBlinkAttackActive()) {
            return;
        }

        TargetStrafe ts = getModule(TargetStrafe.class);
        boolean strafing = ts.isEnabled() && ts.getTarget() != null && (!ts.getSpace().getValue() || mc.options.jumpKey.isPressed());

        double targetY = 0.0;
        if (!strafing) {
            if (mc.options.jumpKey.isPressed()) {
                targetY = verticalSpeed.getValue().doubleValue();
            } else if (mc.options.sneakKey.isPressed()) {
                targetY = -verticalSpeed.getValue().doubleValue();
            }
        }

        switch (mode.getValue()) {
            case "Vanilla" -> {
                if (!strafing) {
                    mc.player.setVelocity(0.0, targetY, 0.0);
                } else {
                    mc.player.setVelocity(mc.player.getVelocity().x, 0.0, mc.player.getVelocity().z);
                }
                MovementUtil.strafe(horizontalSpeed.getValue().doubleValue());
            }

            case "SentinelA" -> {
                if (tick++ % 6 == 0) {
                    instance.getPacketManager().getBlink().start(this);
                    mc.player.setVelocity(strafing ? mc.player.getVelocity().x : 0.0, mc.player.getVelocity().y, strafing ? mc.player.getVelocity().z : 0.0);
                    MovementUtil.strafe(horizontalSpeed.getValue().doubleValue());
                } else if (!MovementUtil.isMoving()) {
                    mc.player.setVelocity(0.0, mc.player.getVelocity().y, 0.0);
                } else {
                    instance.getPacketManager().getBlink().dispatch(this);
                }
                mc.player.setVelocity(mc.player.getVelocity().x, targetY, mc.player.getVelocity().z);
            }
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (mc.player == null || !mode.is("SentinelC")) return;

        if (sentinelCCooldown > 0) {
            sentinelCCooldown--;
        }

        if (mc.player.isOnGround() || sentinelCCooldown > 0) return;

        double motionY;
        if (mc.options.sneakKey.isPressed()) {
            motionY = -0.4;
        } else if (mc.options.jumpKey.isPressed()) {
            motionY = 0.42;
        } else {
            motionY = 0.2;
        }

        mc.player.setVelocity(mc.player.getVelocity().x, motionY, mc.player.getVelocity().z);
        MovementUtil.strafe(ThreadLocalRandom.current().nextDouble(0.33, 0.34));
        sentinelCCooldown = 6;
    }

    @EventTarget
    public void onMove(MoveEvent event) {
        if (mc.player == null || !mode.is("SentinelC")) return;

        if (!MovementUtil.isMoving()) {
            event.setX(0.0);
            event.setZ(0.0);
            return;
        }

        double speed = Math.hypot(event.getX(), event.getZ());
        double direction = MovementUtil.getDirection();
        event.setX(-Math.sin(direction) * speed);
        event.setZ(Math.cos(direction) * speed);
    }

    private boolean check() {
        return getModule(Disabler.class).isWaiting();
    }
}
