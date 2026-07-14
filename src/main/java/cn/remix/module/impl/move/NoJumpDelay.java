package cn.remix.module.impl.move;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.TickEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.NumberValue;
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
