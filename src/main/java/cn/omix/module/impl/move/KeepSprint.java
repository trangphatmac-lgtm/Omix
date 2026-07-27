package cn.omix.module.impl.move;

import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.NumberValue;

public class KeepSprint extends Module {
    public final NumberValue motion = new NumberValue("Motion", 1, 0, 1, .1f);

    public KeepSprint() {
        super("KeepSprint", Category.Move);
    }
}
