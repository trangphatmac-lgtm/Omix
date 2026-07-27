package cn.omix.module.impl.move;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.SlowEvent;
import cn.omix.event.impl.UpdateEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.module.value.impl.ModeValue;

public class NoSlowDown extends Module {
    private final ModeValue mode = new ModeValue("Mode", "Vanilla", "Vanilla");
    private final BoolValue keepSprint = new BoolValue("Keep Sprint", true);

    public NoSlowDown() {
        super("NoSlowDown", Category.Move);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.world == null) return;

        setSuffix(mode.getValue());
    }

    @EventTarget
    public void onSlow(SlowEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (mc.player.isUsingItem() && !mc.player.getActiveItem().isEmpty()) {
            if (mode.is("Vanilla")) {
                event.setCancelled(true);
            }

            if (keepSprint.getValue()) {
                mc.player.setSprinting(true);
            }
        }
    }
}