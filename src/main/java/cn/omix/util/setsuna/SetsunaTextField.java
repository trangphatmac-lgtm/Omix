package cn.omix.util.setsuna;

import cn.omix.util.IMinecraft;
import cn.omix.util.skeet.SkeetTextBuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;

import static cn.omix.util.setsuna.SetsunaLayout.Rect;

/** Reuses Omix's Unicode editing buffer with Pop styling and sensitive-value protection. */
public final class SetsunaTextField implements IMinecraft {
    private final SkeetTextBuffer buffer = new SkeetTextBuffer(32768);
    private final boolean sensitive;
    public SetsunaTextField(String text, boolean sensitive) { buffer.set(text); this.sensitive = sensitive; }
    public String value() { return buffer.value(); }
    public boolean type(CharInput input) {
        if (!input.isValidChar()) return false;
        buffer.replace(input.asString()); return true;
    }
    public void key(KeyInput input) {
        boolean ctrl = (input.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
        boolean shift = (input.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
        if (ctrl) {
            switch (input.key()) {
                case GLFW.GLFW_KEY_A -> { buffer.selectAll(); return; }
                case GLFW.GLFW_KEY_C -> { if (!sensitive) mc.keyboard.setClipboard(buffer.selection()); return; }
                case GLFW.GLFW_KEY_X -> {
                    if (!sensitive) { mc.keyboard.setClipboard(buffer.selection()); buffer.replace(""); }
                    return;
                }
                case GLFW.GLFW_KEY_V -> { buffer.replace(mc.keyboard.getClipboard()); return; }
            }
        }
        switch (input.key()) {
            case GLFW.GLFW_KEY_BACKSPACE -> buffer.erase(true);
            case GLFW.GLFW_KEY_DELETE -> buffer.erase(false);
            case GLFW.GLFW_KEY_LEFT -> { if (ctrl) buffer.home(shift); else buffer.move(-1, shift); }
            case GLFW.GLFW_KEY_RIGHT -> { if (ctrl) buffer.end(shift); else buffer.move(1, shift); }
            case GLFW.GLFW_KEY_HOME -> buffer.home(shift);
            case GLFW.GLFW_KEY_END -> buffer.end(shift);
        }
    }
    public void draw(DrawContext c, SetsunaDraw draw, Rect r) {
        draw.rounded(c, r, 4, SetsunaDraw.alpha(draw.accent, .35f));
        var font = draw.font(8, false);
        String display = sensitive ? "*".repeat(value().length()) : value();
        float caret = font.getStringWidth(display.substring(0, buffer.cursor()));
        float offset = Math.max(0, caret - r.width() + 10), x = r.x() + 4 - offset;
        float y = r.y() + (r.height() - font.getHeight()) / 2;
        SetsunaDraw.scissor(c, r);
        if (buffer.cursor() != buffer.anchor()) {
            float start = font.getStringWidth(display.substring(0, Math.min(buffer.cursor(), buffer.anchor())));
            float end = font.getStringWidth(display.substring(0, Math.max(buffer.cursor(), buffer.anchor())));
            draw.rect(c, x + start, y, end - start, font.getHeight(), SetsunaDraw.alpha(draw.accent, .5f));
        }
        draw.text(c, display, x, y, draw.text(), 8, false);
        if (System.currentTimeMillis() % 1000 < 500) draw.rect(c, x + caret, y, .6f, font.getHeight(), draw.text());
        c.disableScissor();
    }
}
