package cn.omix.module.impl.move;

import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.NumberValue;
import cn.omix.util.misc.TimerSpeedUtil;

import java.util.Locale;

public class Timer extends Module {
    private final NumberValue speed = new NumberValue("Speed", 1.0F, 0.01F, 5.0F, 0.01F);

    public Timer() {
        super("Timer", Category.Move);
    }

    @Override
    public void onEnable() {
        TimerSpeedUtil.setTimerOverride(() -> mc.player == null || mc.world == null ? 1.0F : speed.getValue());
    }

    @Override
    public void onDisable() {
        TimerSpeedUtil.clearTimerOverride();
    }

    @Override
    public String getSuffix() {
        return String.format(Locale.ROOT, "%.2fx", speed.getValue());
    }
}
