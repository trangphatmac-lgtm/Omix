package cn.remix.module.impl.move;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.LivingUpdateEvent;
import cn.remix.event.impl.MotionEvent;
import cn.remix.event.impl.MoveEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.impl.combat.TargetStrafe;
import cn.remix.module.impl.exploits.Disabler;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.player.MovementUtil;

import java.util.concurrent.ThreadLocalRandom;

public final class Fly extends Module {
    public final ModeValue mode = new ModeValue("Mode", "Vanilla", "Vanilla", "SentinelA", "SentinelC");
    private final NumberValue horizontalSpeed = new NumberValue("Horizontal Speed", 3.5, .1, 10, .1);
    private final NumberValue verticalSpeed = new NumberValue("Vertical Speed", .7, .1, 5, .1);
    private int tick;
    private int sentinel2Cooldown;

    public Fly() {
        super("Fly", Category.Move);
    }

    @Override
    public void onEnable() {
        tick = 0;
        sentinel2Cooldown = 0;
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

            case "Sentinel" -> {
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
        if (mc.player == null || !mode.is("Sentinel2")) return;

        if (sentinel2Cooldown > 0) {
            sentinel2Cooldown--;
        }

        if (mc.player.isOnGround() || sentinel2Cooldown > 0) return;

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
        sentinel2Cooldown = 6;
    }

    @EventTarget
    public void onMove(MoveEvent event) {
        if (mc.player == null || !mode.is("Sentinel2")) return;

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
