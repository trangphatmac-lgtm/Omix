package cn.omix.module.impl.render.targethud;

import cn.omix.module.impl.render.HUD;
import cn.omix.ui.font.MinecraftFont;
import cn.omix.util.IMinecraft;
import cn.omix.util.render.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Formatting;

import java.awt.*;

public class Novoline implements IMinecraft {

    public static void render(DrawContext context, LivingEntity target, float x, float y) {
        HUD hud = instance.getModuleManager().getModule(HUD.class);
        float healthPercent = Math.max(0.0f, Math.min(1.0f, target.getHealth() / target.getMaxHealth()));
        float width = getWidth(target);

        Render2D.drawRect(context, x, y, width, getHeight(), new Color(29, 29, 29).getRGB());
        Render2D.drawRect(context, x + 1, y + 1, width - 2, getHeight() - 2, new Color(40, 40, 40).getRGB());

        float barWidth = width - 42;
        Render2D.drawRect(context, x + 37, y + 15, barWidth, 8, 0xFF271E1D);

        int c1 = hud.getColor(1);
        int c2 = hud.getColor(4);
        Render2D.drawGradient(context, x + 37, y + 15, barWidth * healthPercent, 8, c1, c2, true);

        MinecraftFont fr = instance.getFontManager().getMcFont();
        if (target instanceof AbstractClientPlayerEntity player) {
            Render2D.drawPlayerHead(context, player, x + 3, y + 3, 30, 30);
        } else {
            float qX = (x + 3) + (30 - fr.getStringWidth("?")) / 2f;
            float qY = (y + 3) + 15 - (fr.getHeight() / 2f);
            fr.drawStringWithShadow(context, "?", qX, qY, Color.WHITE.getRGB());
        }

        fr.drawStringWithShadow(context, target.getName().getString(), x + 38, y + 4, Color.WHITE.getRGB());

        float textX = x + 38;
        fr.drawStringWithShadow(context, Formatting.WHITE + String.format("%.1f", target.getHealth()) + Formatting.RESET + "❤", textX, y + 25, c1);
    }

    public static float getWidth(LivingEntity target) {
        return Math.max(120f, instance.getFontManager().getMcFont().getStringWidth(target.getName().getString()) + 50f);
    }

    public static float getHeight() {
        return 36;
    }
}
