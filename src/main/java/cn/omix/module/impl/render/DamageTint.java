package cn.omix.module.impl.render;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.Render2DEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.util.render.ColorUtil;
import cn.omix.util.render.Render2D;
import net.minecraft.util.math.MathHelper;

public class DamageTint extends Module {

    public DamageTint() {
        super("DamageTint", Category.Render);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null) return;

        float health = mc.player.getHealth();
        float factor = 1 - MathHelper.clamp(health, 0, 12) / 12;

        if (factor > 0) {
            int alpha = (int) (factor * 170);
            alpha = MathHelper.clamp(alpha, 0, 255);

            int topColor = ColorUtil.applyAlpha(getModule(HUD.class).getColor(), 0);
            int bottomColor = ColorUtil.applyAlpha(getModule(HUD.class).getColor(4), alpha);

            float width = mc.getWindow().getScaledWidth();
            float height = mc.getWindow().getScaledHeight();

            Render2D.drawGradient(event.getContext(), 0, 0, width, height, topColor, bottomColor, false);
        }
    }
}
