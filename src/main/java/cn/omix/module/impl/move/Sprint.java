package cn.omix.module.impl.move;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.MotionEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.BoolValue;

public class Sprint extends Module {
    private final BoolValue omniSprint = new BoolValue("OmniSprint", false);

    public Sprint() {
        super("Sprint", Category.Move);
        setEnabled(true);
    }

    @Override
    public void onDisable() {
        if (mc.player == null) return;

        mc.options.sprintKey.setPressed(false);
        mc.player.setSprinting(false);
    }

    @EventTarget
    public void onMotion(MotionEvent e) {
        if (mc.player == null) return;

        boolean moving = omniSprint.getValue()
                ? mc.player.input != null && mc.player.input.getMovementInput().lengthSquared() > 1.0E-10F
                : mc.player.forwardSpeed > 0;
        if (mc.player.getHungerManager().getFoodLevel() > 6 && moving && !mc.player.horizontalCollision) {
            mc.options.sprintKey.setPressed(true);
        }
    }

    public boolean isOmniSprintActive() {
        return isNativeBehaviorActive() && omniSprint.getValue();
    }
}
