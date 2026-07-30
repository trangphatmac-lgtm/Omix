package cn.omix.module.impl.render;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.UpdateEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.module.value.impl.ModeValue;
import cn.omix.module.value.impl.NumberValue;

public class Animation extends Module {
    public final NumberValue swingSpeed = new NumberValue("Swing Speed", 0, -4, 20, 1);
    public final ModeValue swingMode = new ModeValue("Swing Mode", "Vanilla", "Vanilla", "Smooth");
    public final ModeValue blockMode = new ModeValue("Block Mode", "Flux", "Flux", "1.7", "Stella", "SideDown", "Leaked", "Styles", "Spin", "Screw", "Swang");
    public final BoolValue equipProgress = new BoolValue("Equip Progress", true);

    public Animation() {
        super("Animation", Category.Render);
        setEnabled(true);
        setHidden(true);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        setSuffix(blockMode.getValue());
    }

    @Override
    public void onDisable() {
        setEnabled(true);
    }
}