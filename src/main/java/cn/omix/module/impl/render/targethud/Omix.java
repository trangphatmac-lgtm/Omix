package cn.omix.module.impl.render.targethud;

import cn.omix.ui.font.TrueTypeFont;
import cn.omix.util.IMinecraft;
import cn.omix.util.render.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;

import java.awt.*;

public class Omix implements IMinecraft {

    public static void render(DrawContext context, LivingEntity target, float x, float y) {
        float healthPresent = Math.max(0.0f, Math.min(1.0f, target.getHealth() / target.getMaxHealth()));
        float armorPresent = Math.max(0.0f, Math.min(1.0f, (float) target.getArmor() / 20.0f));

        float width = getWidth(target);
        float height = getHeight();

        Render2D.drawRect(context, x, y, width, height, new Color(30, 30, 30).getRGB());

        if (target instanceof AbstractClientPlayerEntity player) {
            Render2D.drawPlayerHead(context, player, x + 5, y + 5, 32, 32);
        } else {
            Render2D.drawRect(context, x + 5, y + 5, 32, 32, new Color(70, 70, 70).getRGB());
        }

        float barWidth = width - 10;
        Render2D.drawRect(context, x + 5, y + 40, barWidth, 5, new Color(80, 0, 0).getRGB());
        Render2D.drawRect(context, x + 5, y + 40, barWidth * healthPresent, 5, new Color(0, 165, 0).getRGB());

        float armorBarWidth = width - 44.5f;
        Render2D.drawRect(context, x + 39, y + 35.5f, armorBarWidth, 1, new Color(0, 0, 80).getRGB());
        Render2D.drawRect(context, x + 39, y + 35.5f, armorBarWidth * armorPresent, 1, new Color(0, 0, 165).getRGB());

        float slotX = x + 39;
        for (int i = 0; i < 4; i++) {
            Render2D.drawRect(context, slotX, y + 16, 18, 18, new Color(0, 0, 0).getRGB());
            Render2D.drawRect(context, slotX + 1, y + 17, 16, 16, new Color(70, 70, 70).getRGB());
            slotX += 20;
        }

        Render2D.drawItem(context, target.getEquippedStack(EquipmentSlot.HEAD), x + 40, y + 18);
        Render2D.drawItem(context, target.getEquippedStack(EquipmentSlot.CHEST), x + 60, y + 18);
        Render2D.drawItem(context, target.getEquippedStack(EquipmentSlot.LEGS), x + 80, y + 18);
        Render2D.drawItem(context, target.getEquippedStack(EquipmentSlot.FEET), x + 100, y + 18);

        TrueTypeFont nameFont = instance.getFontManager().getFont(16);
        nameFont.drawStringWithShadow(context, target.getName().getString(), x + 40, y + 4, new Color(200, 200, 200).getRGB());

        if (target instanceof AbstractClientPlayerEntity player && mc.getNetworkHandler() != null) {
            PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
            if (entry != null) {
                int ping = entry.getLatency();
                String pingText = ping + "ms";
                TrueTypeFont smallFont = instance.getFontManager().getFont(10);

                float maxRightX = x + width - 3;
                drawTabPingIcon(context, maxRightX - 12, y + 20, ping);

                float pingX = maxRightX - smallFont.getStringWidth(pingText);
                smallFont.drawStringWithShadow(context, pingText, pingX, y + 27, new Color(200, 200, 200).getRGB());
            }
        }
    }

    private static void drawTabPingIcon(DrawContext context, float x, float y, int ping) {
        int bars;
        int color;

        if (ping < 0) {
            bars = 0;
            color = new Color(165, 0, 0).getRGB();
        } else if (ping < 150) {
            bars = 5;
            color = new Color(0, 165, 0).getRGB();
        } else if (ping < 300) {
            bars = 4;
            color = new Color(0, 165, 0).getRGB();
        } else if (ping < 600) {
            bars = 3;
            color = new Color(165, 165, 0).getRGB();
        } else if (ping < 1000) {
            bars = 2;
            color = new Color(165, 80, 0).getRGB();
        } else {
            bars = 1;
            color = new Color(165, 0, 0).getRGB();
        }

        for (int i = 0; i < 5; i++) {
            int drawColor = (i < bars || bars == 0) ? color : new Color(60, 60, 60).getRGB();
            float height = 2 + i * 1.25f;
            Render2D.drawRect(context, x + i * 2, y + 7 - height, 1, height, drawColor);
        }
    }

    public static float getWidth(LivingEntity target) {
        float textWidth = instance.getFontManager().getFont(16).getStringWidth(target.getName().getString());
        return Math.max(140f, textWidth + 80f);
    }

    public static float getHeight() {
        return 50f;
    }
}
