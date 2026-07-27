package cn.omix.module.impl.move;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.TickEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.NumberValue;
import injection.accessor.LivingEntityAccessor;

public final class NoJumpDelay extends Module {
    private final NumberValue delay = new NumberValue("Delay", 3, 0, 8, 1);

    public NoJumpDelay() {
        super("NoJumpDelay", Category.Move);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null) return;

        int configuredDelay = delay.getValue().intValue();
        LivingEntityAccessor accessor = (LivingEntityAccessor) mc.player;
        accessor.setJumpingCooldown(Math.min(accessor.getJumpingCooldown(), configuredDelay + 1));
        setSuffix(String.valueOf(configuredDelay));
    }
}
