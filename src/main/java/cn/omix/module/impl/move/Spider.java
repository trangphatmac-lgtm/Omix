package cn.omix.module.impl.move;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.UpdateEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.module.value.impl.ModeValue;
import cn.omix.module.value.impl.NumberValue;
import cn.omix.util.misc.TimerSpeedUtil;
import cn.omix.util.player.MovementUtil;
import net.minecraft.util.math.Vec3d;

public final class Spider extends Module {
    private final ModeValue mode = new ModeValue("Mode", "Vanilla", "Vanilla", "Timer", "Pulse");
    private final NumberValue speed = new NumberValue(
            "Speed", 0.32F, 0.1F, 1.0F, 0.01F, () -> !mode.is("Timer")
    );
    private final NumberValue timerSpeed = new NumberValue(
            "Timer Speed", 1.7F, 1.1F, 4.0F, 0.1F, () -> mode.is("Timer")
    );
    private final BoolValue onlyMoving = new BoolValue("Only Moving", true);

    private boolean timered;
    private int pulseTicks;

    public Spider() {
        super("Spider", Category.Move);
    }

    @Override
    public void onEnable() {
        timered = false;
        pulseTicks = 0;
    }

    @Override
    public void onDisable() {
        resetTimer();
        pulseTicks = 0;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        setSuffix(mode.getValue());

        if (mc.player == null) {
            resetState();
            return;
        }

        boolean climbing = mc.player.horizontalCollision
                && !mc.player.isClimbing()
                && (!onlyMoving.getValue() || MovementUtil.isMoving());
        if (!climbing) {
            resetState();
            return;
        }

        if (!mode.is("Timer")) resetTimer();
        if (!mode.is("Pulse")) pulseTicks = 0;

        switch (mode.getValue()) {
            case "Timer" -> {
                TimerSpeedUtil.setTimerSpeed(timerSpeed.getValue());
                timered = true;
                if (mc.player.getVelocity().y < 0.0) {
                    setVelocityY(0.0);
                }
            }
            case "Pulse" -> {
                pulseTicks++;
                if (pulseTicks >= 3) {
                    setVelocityY(speed.getValue());
                    pulseTicks = 0;
                } else if (mc.player.getVelocity().y < 0.0) {
                    setVelocityY(0.0);
                }
            }
            default -> setVelocityY(speed.getValue());
        }
    }

    private void setVelocityY(double y) {
        Vec3d velocity = mc.player.getVelocity();
        mc.player.setVelocity(velocity.x, y, velocity.z);
    }

    private void resetState() {
        resetTimer();
        pulseTicks = 0;
    }

    private void resetTimer() {
        if (!timered) return;

        TimerSpeedUtil.reset();
        timered = false;
    }
}
