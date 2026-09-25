package cn.omix.util.sigma;

import cn.omix.ui.font.TrueTypeFont;
import cn.omix.util.IMinecraft;
import cn.omix.util.render.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;

/** Keyboard editing shared by the Jello search box, settings and waypoint popup. */
public final class SigmaTextInput implements IMinecraft {
    private String value = "";
    private int cursor, anchor;
    private final int limit;
    private boolean focused;

    public SigmaTextInput(int limit) { this.limit = limit; }
    public String value() { return value; }
    public boolean focused() { return focused; }
    public void focus(boolean focused) { this.focused = focused; }
    public void set(String value) { this.value = value.substring(0, Math.min(limit, value.length())); cursor = anchor = this.value.length(); }
    public void selectAll() { anchor = 0; cursor = value.length(); }

    public boolean type(CharInput input) {
        if (!focused || !input.isValidChar()) return false;
        replace(input.asString()); return true;
    }

    public boolean key(KeyInput input) {
        if (!focused) return false;
        boolean control = (input.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
        boolean shift = (input.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
        if (control) {
            switch (input.key()) {
                case GLFW.GLFW_KEY_A -> { selectAll(); return true; }
                case GLFW.GLFW_KEY_C -> { mc.keyboard.setClipboard(selection()); return true; }
                case GLFW.GLFW_KEY_X -> { mc.keyboard.setClipboard(selection()); replace(""); return true; }
                case GLFW.GLFW_KEY_V -> { replace(mc.keyboard.getClipboard().replaceAll("[\\r\\n\\p{Cntrl}]", "")); return true; }
            }
        }
        switch (input.key()) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (cursor == anchor && cursor > 0) anchor = value.offsetByCodePoints(cursor, -1);
                replace(""); return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (cursor == anchor && cursor < value.length()) anchor = value.offsetByCodePoints(cursor, 1);
                replace(""); return true;
            }
            case GLFW.GLFW_KEY_LEFT -> cursor = control ? 0 : value.offsetByCodePoints(cursor, cursor > 0 ? -1 : 0);
            case GLFW.GLFW_KEY_RIGHT -> cursor = control ? value.length() : value.offsetByCodePoints(cursor, cursor < value.length() ? 1 : 0);
            case GLFW.GLFW_KEY_HOME -> cursor = 0;
            case GLFW.GLFW_KEY_END -> cursor = value.length();
            default -> { return false; }
        }
        if (!shift) anchor = cursor;
        return true;
    }

    private String selection() { return value.substring(Math.min(cursor, anchor), Math.max(cursor, anchor)); }
    private void replace(String replacement) {
        int start = Math.min(cursor, anchor), end = Math.max(cursor, anchor);
        int length = Math.min(replacement.length(), Math.max(0, limit - value.length() + end - start));
        if (length > 0 && Character.isHighSurrogate(replacement.charAt(length - 1))) length--;
        value = value.substring(0, start) + replacement.substring(0, length) + value.substring(end);
        cursor = anchor = start + length;
    }

    public void draw(DrawContext context, TrueTypeFont font, float x, float y, float width, int color, String placeholder) {
        draw(context, font, x, y, width, color, placeholder, false);
    }

    public void draw(DrawContext context, TrueTypeFont font, float x, float y, float width, int color, String placeholder, boolean masked) {
        String display = masked ? "•".repeat(value.length()) : value;
        Render2D.beginScissor(context, x, y, width, font.getHeight() + 3);
        try {
            float caret = font.getStringWidth(display.substring(0, cursor));
            float offset = focused ? Math.max(0, caret - width + 2) : 0;
            if (focused && cursor != anchor) {
                float a = font.getStringWidth(display.substring(0, Math.min(cursor, anchor)));
                float b = font.getStringWidth(display.substring(0, Math.max(cursor, anchor)));
                Render2D.drawRect(context, x + a - offset, y, b - a, font.getHeight(), 0x4066aaff);
            }
            font.drawString(context, value.isEmpty() && !focused ? placeholder : display, x - offset, y,
                    value.isEmpty() ? SigmaColors.alpha(color, .4f) : color);
            if (focused && System.currentTimeMillis() % 1000 < 500) Render2D.drawRect(context, x + caret - offset, y + 2, 1, font.getHeight() - 3, color);
        } finally { Render2D.endScissor(context); }
    }
}
