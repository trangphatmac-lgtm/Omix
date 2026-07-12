package cn.remix.module.impl.render;

import cn.remix.Client;
import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.Render2DEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.ui.font.TrueTypeFont;
import cn.remix.util.Util;
import cn.remix.util.render.ColorUtil;
import cn.remix.util.render.Render2D;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class Notify extends Module {
    private static final long DISPLAY_TIME = 3000L;
    private static final long ENTER_TIME = 250L;
    private static final long EXIT_TIME = 300L;
    private static final int MAX_NOTIFICATIONS = 5;

    private final ModeValue mode = new ModeValue("Mode", "HUD", "Chat", "HUD", "Both");
    private final List<Notification> notifications = new CopyOnWriteArrayList<>();

    public Notify() {
        super("Notify", Category.Render);
    }

    public void post(String message) {
        if (mode.is("Chat") || mode.is("Both")) {
            Util.logToChat(message);
        }

        if (mode.is("Chat")) {
            return;
        }

        notifications.add(new Notification(message, System.currentTimeMillis()));
        while (notifications.size() > MAX_NOTIFICATIONS) {
            notifications.removeFirst();
        }
    }

    @Override
    public void onDisable() {
        notifications.clear();
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!mode.is("HUD") && !mode.is("Both")) return;

        long now = System.currentTimeMillis();
        notifications.removeIf(notification -> now - notification.createdAt() >= DISPLAY_TIME);

        DrawContext context = event.getContext();
        TrueTypeFont titleFont = instance.getFontManager().getBoldFont(16);
        TrueTypeFont messageFont = instance.getFontManager().getBoldFont(18);
        int screenWidth = mc.getWindow().getScaledWidth();
        int screenHeight = mc.getWindow().getScaledHeight();
        int index = 0;

        for (int i = notifications.size() - 1; i >= 0; i--) {
            Notification notification = notifications.get(i);
            long age = now - notification.createdAt();
            float visibility = getVisibility(age);
            String message = Util.formatCodes(notification.message());

            float width = Math.max(110, Math.max(
                    titleFont.getStringWidth(Client.name),
                    messageFont.getStringWidth(message)
            ) + 18);
            float height = 31;
            float targetX = screenWidth - width - 6;
            float x = targetX + (width + 8) * (1 - visibility);
            float y = screenHeight - 6 - height - index * (height + 5);
            int alpha = Math.round(220 * visibility);
            int textAlpha = Math.round(255 * visibility);
            int accent = getAccentColor(notification.message(), textAlpha);

            Render2D.drawRect(context, x, y, width, height,
                    ColorUtil.applyAlpha(new Color(18, 18, 22).getRGB(), alpha));
            Render2D.drawRect(context, x, y, 2, height, accent);
            titleFont.drawString(context, Client.name, x + 8, y + 3,
                    ColorUtil.applyAlpha(new Color(185, 185, 190).getRGB(), textAlpha));
            messageFont.drawString(context, message, x + 8, y + 15,
                    ColorUtil.applyAlpha(Color.WHITE.getRGB(), textAlpha));
            index++;
        }
    }

    private float getVisibility(long age) {
        if (age < ENTER_TIME) {
            float progress = (float) age / ENTER_TIME;
            return 1 - (float) Math.pow(1 - progress, 3);
        }

        long exitStart = DISPLAY_TIME - EXIT_TIME;
        if (age > exitStart) {
            float progress = (float) (age - exitStart) / EXIT_TIME;
            return 1 - progress * progress * progress;
        }

        return 1;
    }

    private int getAccentColor(String message, int alpha) {
        int color = message.contains("&a") || message.contains("§a")
                ? new Color(85, 255, 85).getRGB()
                : message.contains("&c") || message.contains("§c")
                ? new Color(255, 85, 85).getRGB()
                : new Color(85, 170, 255).getRGB();
        return ColorUtil.applyAlpha(color, alpha);
    }

    private record Notification(String message, long createdAt) {}
}
