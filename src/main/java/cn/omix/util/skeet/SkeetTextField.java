package cn.omix.util.skeet;

import cn.omix.util.IMinecraft;
import cn.omix.util.render.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;

public final class SkeetTextField implements IMinecraft {
    private final SkeetTextBuffer buffer;
    private final boolean sensitive;
    private boolean focused;

    public SkeetTextField(int limit, boolean sensitive) { buffer = new SkeetTextBuffer(limit); this.sensitive = sensitive; }
    public String value() { return buffer.value(); }
    public boolean focused() { return focused; }
    public void set(String text) { buffer.set(text); }
    public void focus(boolean focus) { focused = focus; }

    public boolean key(KeyInput input) {
        if (!focused) return false;
        boolean ctrl = (input.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
        boolean shift = (input.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
        if (ctrl) {
            switch (input.key()) {
                case GLFW.GLFW_KEY_A -> { buffer.selectAll(); return true; }
                case GLFW.GLFW_KEY_C -> { if (!sensitive) mc.keyboard.setClipboard(buffer.selection()); return true; }
                case GLFW.GLFW_KEY_X -> {
                    if (!sensitive) { mc.keyboard.setClipboard(buffer.selection()); buffer.replace(""); }
                    return true;
                }
                case GLFW.GLFW_KEY_V -> { buffer.replace(mc.keyboard.getClipboard()); return true; }
            }
        }
        switch (input.key()) {
            case GLFW.GLFW_KEY_BACKSPACE -> buffer.erase(true);
            case GLFW.GLFW_KEY_DELETE -> buffer.erase(false);
            case GLFW.GLFW_KEY_LEFT -> { if (ctrl) buffer.home(shift); else buffer.move(-1, shift); }
            case GLFW.GLFW_KEY_RIGHT -> { if (ctrl) buffer.end(shift); else buffer.move(1, shift); }
            case GLFW.GLFW_KEY_HOME -> buffer.home(shift);
            case GLFW.GLFW_KEY_END -> buffer.end(shift);
            default -> { return false; }
        }
        return true;
    }
    public boolean type(CharInput input) {
        if (!focused || !input.isValidChar()) return false;
        buffer.replace(input.asString()); return true;
    }
    public void draw(DrawContext c, SkeetDraw draw, SkeetLayout.Rect bounds, String placeholder) {
        draw.field(c, bounds, focused);
        var inner = bounds.inset(3);
        var font = draw.font();
        String display = sensitive ? "*".repeat(value().length()) : value();
        float caret = font.getStringWidth(display.substring(0, buffer.cursor()));
        float offset = focused ? Math.max(0, caret - inner.width() + 1) : 0;
        float y = bounds.y() + (bounds.height() - font.getHeight()) / 2;
        SkeetDraw.scissor(c, bounds.inset(1));
        if (focused && buffer.cursor() != buffer.anchor()) {
            float a = font.getStringWidth(display.substring(0, Math.min(buffer.cursor(), buffer.anchor())));
            float b = font.getStringWidth(display.substring(0, Math.max(buffer.cursor(), buffer.anchor())));
            draw.rect(c, inner.x() + a - offset, y, b - a, font.getHeight(), 0x44522a);
        }
        draw.text(c, value().isEmpty() && !focused ? placeholder : display, inner.x() - offset, y,
                value().isEmpty() ? SkeetDraw.MUTED : SkeetDraw.TEXT);
        if (focused && System.currentTimeMillis() % 1000 < 500)
            draw.rect(c, inner.x() + caret - offset, y + 1, .5f, font.getHeight() - 2, SkeetDraw.TEXT);
        Render2D.endScissor(c);
    }
}
