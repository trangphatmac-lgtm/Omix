package cn.omix.module.impl.player;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.MotionEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.module.value.impl.NumberValue;
import cn.omix.util.misc.TimerUtil;
import cn.omix.util.player.ClickSlotUtil;
import cn.omix.util.player.ItemUtil;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;

public class ChestStealer extends Module {
    private final BoolValue onlyBest = new BoolValue("Only Best", true);

    private final NumberValue delay = new NumberValue("Delay", 50, 0, 500, 10);
    private final NumberValue openDelay = new NumberValue("Open Delay", 50, 0, 500, 10);

    private final BoolValue autoClose = new BoolValue("Auto Close", true);

    private final TimerUtil clickTimer = new TimerUtil();
    private final TimerUtil openTimer = new TimerUtil();

    public ChestStealer() {
        super("ChestStealer", Category.Player);
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (mc.player == null || event.isPost()) return;

        boolean instant = delay.getValue().doubleValue() == 0.0;
        setSuffix(String.format("%.1f", delay.getValue()));

        if (mc.currentScreen instanceof GenericContainerScreen container) {
            if (!openTimer.hasTimeElapsed(openDelay.getValue().longValue())) return;

            GenericContainerScreenHandler handler = container.getScreenHandler();
            boolean has = false;

            for (int i = 0; i < handler.getInventory().size(); i++) {
                Slot slot = handler.getSlot(i);

                if (slot.hasStack()) {
                    if (onlyBest.getValue() && ItemUtil.isUseless(-1, slot.getStack())) continue;
                    has = true;

                    if (instant) {
                        ClickSlotUtil.shiftClick(i);
                    } else if (clickTimer.hasTimeElapsed(delay.getValue().longValue())) {
                        ClickSlotUtil.shiftClick(i);
                        clickTimer.reset();
                        return;
                    }
                }
            }

            if ((!has || instant) && autoClose.getValue()) {
                mc.player.closeHandledScreen();
            }
        } else {
            openTimer.reset();
        }
    }
}