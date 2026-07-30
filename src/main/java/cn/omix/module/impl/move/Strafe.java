package cn.omix.module.impl.move;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.MotionEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.util.player.MovementUtil;

public class Strafe extends Module {

    public Strafe() {
        super("Strafe", Category.Move);
    }

    @EventTarget
    public void onUpdate(MotionEvent event) {
        if (event.isPre()) {
            MovementUtil.strafe();
        }
    }
}
