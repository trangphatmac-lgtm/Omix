package cn.omix.util.skeet;

import cn.omix.Client;
import cn.omix.ui.font.TrueTypeFont;
import cn.omix.util.render.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

import java.awt.Font;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Adapted from Exhibition-Reborn's SkeetUI and group/control renderers; see assets/omix/skeet/LICENSE. */
public final class SkeetDraw {
    public static final int TEXT = 0xffdcdcdc, MUTED = 0xff969696, ACCENT = 0xff96c83c;
    public static final Identifier BACKGROUND = Identifier.of("omix", "skeet/skeetchainmail.png");
    private static final Map<String, Font> RAW_FONTS = new HashMap<>();
    private static final Map<String, TrueTypeFont> FONTS = new HashMap<>();
    private final int density;
    private final float opacity;

    public SkeetDraw(int density, float opacity) { this.density = density; this.opacity = opacity; }
    public int color(int rgb) { return Math.round(Math.clamp(opacity, 0, 1) * 255) << 24 | rgb & 0xffffff; }
    public TrueTypeFont font() { return font("tahoma.ttf", 9); }
    public TrueTypeFont header() { return font("tahoma-bold.ttf", 10); }
    public TrueTypeFont icons() { return font("icon.ttf", 24); }

    private TrueTypeFont font(String name, int size) {
        return FONTS.computeIfAbsent(name + ":" + size + ":" + density, key -> {
            Font raw = RAW_FONTS.computeIfAbsent(name, SkeetDraw::loadFont);
            Font fallback = new Font("Dialog", Font.PLAIN, size * density);
            return new TrueTypeFont(raw.deriveFont((float) size * density), List.of(fallback), density, 1024, true);
        });
    }

    private static Font loadFont(String name) {
        try (var input = SkeetDraw.class.getResourceAsStream("/assets/omix/skeet/font/" + name)) {
            if (input != null) return Font.createFont(Font.TRUETYPE_FONT, input);
        } catch (Exception error) {
            Client.logger.warn("Cannot load Skeet font {}", name, error);
        }
        return new Font("Dialog", name.contains("bold") ? Font.BOLD : Font.PLAIN, 9);
    }

    public void rect(DrawContext c, float x, float y, float w, float h, int rgb) {
        Render2D.drawRect(c, x, y, w, h, color(rgb));
    }
    public void gradient(DrawContext c, float x, float y, float w, float h, int a, int b, boolean horizontal) {
        Render2D.drawGradient(c, x, y, w, h, color(a), color(b), horizontal);
    }
    public void text(DrawContext c, String text, float x, float y, int rgb) {
        font().drawStringWithShadow(c, text, x, y, color(rgb));
    }
    public void clipped(DrawContext c, String text, SkeetLayout.Rect bounds, int rgb) {
        scissor(c, bounds);
        text(c, text, bounds.x(), bounds.y() + (bounds.height() - font().getHeight()) / 2, rgb);
        Render2D.endScissor(c);
    }
    public static void scissor(DrawContext c, SkeetLayout.Rect r) {
        c.enableScissor((int) Math.floor(r.x()), (int) Math.floor(r.y()),
                (int) Math.ceil(r.x() + r.width()), (int) Math.ceil(r.y() + r.height()));
    }

    public void group(DrawContext c, String title, SkeetLayout.Rect r) {
        rect(c, r.x(), r.y(), r.width(), r.height(), 0x0c0c0c);
        rect(c, r.x() + .5f, r.y() + .5f, r.width() - 1, r.height() - 1, 0x282828);
        rect(c, r.x() + 1, r.y() + 1, r.width() - 2, r.height() - 2, 0x171717);
        float labelWidth = Math.min(r.width() - 12, header().getStringWidth(title));
        rect(c, r.x() + 4, r.y(), labelWidth + 3, 1, 0x171717);
        scissor(c, new SkeetLayout.Rect(r.x() + 5, r.y() - 5, r.width() - 10, 14));
        header().drawStringWithShadow(c, title, r.x() + 5, r.y() - 5, color(TEXT));
        Render2D.endScissor(c);
    }

    public void field(DrawContext c, SkeetLayout.Rect r, boolean hovered) {
        rect(c, r.x(), r.y(), r.width(), r.height(), 0x0d0d0d);
        gradient(c, r.x() + .5f, r.y() + .5f, r.width() - 1, r.height() - 1,
                hovered ? 0x303030 : 0x232323, hovered ? 0x262626 : 0x1b1b1b, false);
    }

    public void checkbox(DrawContext c, float x, float y, boolean checked, boolean hovered) {
        rect(c, x, y, 7, 7, 0x0b0b0b);
        gradient(c, x + .5f, y + .5f, 6, 6, checked ? ACCENT : hovered ? 0x595959 : 0x494949,
                checked ? 0x648628 : 0x303030, false);
    }
    public void button(DrawContext c, String label, SkeetLayout.Rect r, boolean hovered, boolean enabled) {
        field(c, r, hovered && enabled);
        clipped(c, label, new SkeetLayout.Rect(r.x() + 4, r.y(), r.width() - 8, r.height()), enabled ? TEXT : 0x606060);
    }

    public void scrollbar(DrawContext c, SkeetLayout.Rect r, float content, float scroll) {
        if (content <= r.height()) return;
        float h = Math.max(14, r.height() * r.height() / content);
        float y = r.y() + scroll / (content - r.height()) * (r.height() - h);
        rect(c, r.x() + r.width() - 3, r.y(), 2, r.height(), 0x101010);
        rect(c, r.x() + r.width() - 3, y, 2, h, 0x474747);
    }
}
