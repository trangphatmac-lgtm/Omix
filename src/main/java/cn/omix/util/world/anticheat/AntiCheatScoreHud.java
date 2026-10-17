package cn.omix.util.world.anticheat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Language;

import java.util.ArrayList;
import java.util.List;

/** Vanilla text rendering keeps the score overlay compatible with all Omix HUD themes. */
public final class AntiCheatScoreHud {
    private AntiCheatScoreHud() {}

    public record Row(int score, Text name) {}

    public static void render(MinecraftClient mc, DrawContext context, List<Row> rows, int x, int y) {
        var font = mc.textRenderer;
        int lineHeight = font.fontHeight + 2;
        int screenWidth = context.getScaledWindowWidth(), screenHeight = context.getScaledWindowHeight();
        if (screenWidth < 40 || screenHeight < lineHeight * 2 + 12) return;
        List<Text> lines = new ArrayList<>();
        lines.add(Text.literal("Cheat Score UI").withColor(0xFF5555));
        int rowLimit = Math.min(15, Math.max(1, (screenHeight - 12) / lineHeight - 1));
        for (Row row : rows.stream().limit(rowLimit).toList()) {
            lines.add(Text.literal(row.score() + "%  ").withColor(AntiCheatSuspicion.color(row.score()) & 0xFFFFFF)
                    .append(row.name().copy()));
        }
        if (rows.isEmpty()) lines.add(Text.literal("No suspicious players detected").withColor(0xAAAAAA));
        int width = Math.min(screenWidth - 12, lines.stream().mapToInt(font::getWidth).max().orElse(0));
        int height = lines.size() * lineHeight;
        x = Math.max(6, Math.min(x, screenWidth - width - 6));
        y = Math.max(6, Math.min(y, screenHeight - height - 6));
        context.fill(x - 4, y - 4, x + width + 4, y + height + 2, 0xA0101018);
        for (int i = 0; i < lines.size(); i++) {
            context.drawText(font, Language.getInstance().reorder(font.trimToWidth(lines.get(i), width)),
                    x, y + i * lineHeight, 0xFFFFFFFF, true);
        }
    }
}
