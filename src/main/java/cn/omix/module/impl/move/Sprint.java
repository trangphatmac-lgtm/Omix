package cn.omix.module.impl.move;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.MotionEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;

public class Sprint extends Module {

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

        if (mc.player.getHungerManager().getFoodLevel() > 6 && mc.player.forwardSpeed > 0 && !mc.player.horizontalCollision) {
            mc.options.sprintKey.setPressed(true);
        }
    }
}