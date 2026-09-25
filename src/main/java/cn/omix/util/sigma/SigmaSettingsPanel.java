package cn.omix.util.sigma;

import cn.omix.module.Module;
import cn.omix.module.value.Value;
import cn.omix.module.value.impl.*;
import cn.omix.util.misc.KeyUtil;
import cn.omix.util.render.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Jello SettingPanel geometry, backed by Omix values (including nested visibility predicates). */
public final class SigmaSettingsPanel {
    public static final int BLUE = -14047489;
    private final Module module;
    private final List<Row> rows = new ArrayList<>();
    private final Map<TextValue, SigmaTextInput> textInputs = new IdentityHashMap<>();
    private final Map<BoolValue, SigmaAnimation> checks = new IdentityHashMap<>();
    private float x, y, width, height, scroll, contentHeight;
    private Row dragging, dropdown;
    private int dragPart;
    private KeyValue binding;
    private boolean moduleBinding;
    private TextValue focused;
    private final SigmaAnimation animation = new SigmaAnimation();
    private boolean closing;
    private float scale = 1, dropdownScroll;

    public SigmaSettingsPanel(Module module) { this.module = module; }
    public Module module() { return module; }
    public void close() { closing = true; unfocus(); }
    public boolean closed() { return closing && animation.value() == 0; }
    public float progress() { return animation.value(); }

    public void draw(DrawContext context, float mouseX, float mouseY, float alpha) {
        float p = animation.update(!closing, System.nanoTime(), closing ? 120 : 200);
        alpha *= p;
        float back = 1 + 2.70158f * (float) Math.pow(p - 1, 3) + 1.70158f * (float) Math.pow(p - 1, 2);
        scale = .8f + .2f * (closing ? SigmaAnimation.easeOut(p) : back);
        width = Math.min(500, SigmaDraw.width() - 40);
        height = Math.min(600, SigmaDraw.height() * .7f);
        x = (SigmaDraw.width() - width) / 2; y = (SigmaDraw.height() - height) / 2 + 20;
        Render2D.drawRect(context, 0, 0, SigmaDraw.width(), SigmaDraw.height(), SigmaColors.alpha(SigmaColors.BLACK, alpha * .45f));
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(SigmaDraw.width() / 2f, SigmaDraw.height() / 2f);
        context.getMatrices().scale(scale, scale);
        context.getMatrices().translate(-SigmaDraw.width() / 2f, -SigmaDraw.height() / 2f);
        mouseX = local(mouseX, true); mouseY = local(mouseY, false);
        SigmaResources.medium(40).drawString(context, module.getName(), x, y - 60, SigmaColors.alpha(SigmaColors.WHITE, alpha));
        SigmaDraw.shadow(context, x, y, width, height, 30, alpha * .5f);
        SigmaShape.rounded(context, x, y, width, height, 10, SigmaColors.alpha(SigmaColors.WHITE, alpha));
        Render2D.beginScissor(context, x + 30, y + 25, width - 155, 30);
        SigmaResources.light(20).drawString(context, description(), x + 30, y + 30, SigmaColors.alpha(SigmaColors.BLACK, alpha * .7f));
        Render2D.endScissor(context);
        String key = moduleBinding ? "Press key…" : KeyUtil.getKeyName(module.getKey());
        SigmaResources.light(14).drawString(context, key, x + width - 35 - SigmaResources.light(14).getStringWidth(key), y + 31, SigmaColors.alpha(SigmaColors.BLACK, alpha * .5f));
        rebuild();
        if (focused != null && !focused.isVisible()) unfocus();
        if (binding != null && !binding.isVisible()) binding = null;
        if (dropdown != null && !dropdown.value.isVisible()) dropdown = null;
        Render2D.beginScissor(context, x + 10, y + 59, width - 20, height - 69);
        try {
            for (Row row : rows) {
                if (row.y + row.height < y + 59 || row.y > y + height - 10) continue;
                drawRow(context, row, mouseX, mouseY, alpha);
            }
        } finally { Render2D.endScissor(context); }
        if (contentHeight > height - 89) {
            float track = height - 89, thumb = Math.max(20, track * track / contentHeight);
            SigmaShape.rounded(context, x + width - 8, y + 69 + (track - thumb) * scroll / Math.max(1, contentHeight - track), 3, thumb, 1.5f, SigmaColors.alpha(SigmaColors.BLACK, .2f * alpha));
        }
        if (dropdown != null) drawDropdown(context, mouseX, mouseY, alpha);
        context.getMatrices().popMatrix();
    }

    private float local(float coordinate, boolean horizontal) {
        float center = (horizontal ? SigmaDraw.width() : SigmaDraw.height()) / 2f;
        return (coordinate - center) / Math.max(.01f, scale) + center;
    }

    private String description() {
        return switch (module.getName()) {
            case "NameTags" -> "Render better name tags";
            case "ChestESP" -> "Allows you to see chests through blocks";
            case "ESP" -> "See entities anywhere anytime";
            case "Tracers" -> "Shows players";
            case "Waypoint" -> "Renders waypoints you added in Jello maps";
            case "HUD" -> "Jello in-game interface";
            case "Maps" -> "Explore your world with Jello Maps";
            default -> module.getCategory().getName();
        };
    }

    private void rebuild() {
        rows.clear(); float offset = 20;
        for (Value value : module.getValues()) {
            if (!value.isVisible()) continue;
            if (value instanceof MultiBoolValue multi) {
                rows.add(new Row(value, offset, 34, 0)); offset += 34;
                for (BoolValue child : multi.getValues()) if (child.isVisible()) {
                    rows.add(new Row(child, offset, 34, 12)); offset += 34;
                }
            } else {
                float rowHeight = value instanceof ColorValue ? 124 : value instanceof ModeValue || value instanceof TextValue ? 37 : 34;
                rows.add(new Row(value, offset, rowHeight, 0)); offset += rowHeight;
            }
        }
        contentHeight = offset + 10;
        scroll = Math.clamp(scroll, 0, Math.max(0, contentHeight - (height - 89)));
        for (Row row : rows) row.y += y + 59 - scroll;
        if (dropdown != null) dropdown = rows.stream().filter(r -> r.value == dropdown.value).findFirst().orElse(null);
    }

    private void drawRow(DrawContext context, Row row, float mx, float my, float alpha) {
        Value value = row.value; float right = x + width - 30, cy = row.y;
        var font = SigmaResources.light(20);
        Render2D.beginScissor(context, x + 30, cy, Math.max(20, width - 205), row.height);
        String label = value.getName().replaceFirst("^Sigma ", "");
        float indent = row.indent;
        if (module instanceof cn.omix.module.impl.render.HUD && label.matches("^(ActiveMods|InfoHUD|RearView) .+")) {
            label = label.substring(label.indexOf(' ') + 1); indent += 12;
        }
        font.drawString(context, label, x + 30 + indent, cy + 4, SigmaColors.alpha(SigmaColors.BLACK, alpha * .8f));
        Render2D.endScissor(context);
        if (value instanceof BoolValue bool) {
            float p = checks.computeIfAbsent(bool, v -> new SigmaAnimation(v.getValue() ? 1 : 0)).update(bool.getValue(), System.nanoTime(), bool.getValue() ? 70 : 90);
            SigmaShape.rounded(context, right - 24, cy + 6, 24, 24, 10, SigmaColors.alpha(0xffc0c0c0, .43f * (1 - p) * alpha));
            SigmaShape.rounded(context, right - 24, cy + 6, 24, 24, 10, SigmaColors.alpha(BLUE, p * alpha));
            SigmaDraw.image(context, "component/check.png", right - 24, cy + 6, 24, 24, SigmaColors.alpha(SigmaColors.WHITE, p * alpha));
        } else if (value instanceof NumberValue number) {
            float p = (number.getValue() - number.getMin()) / Math.max(.0001f, number.getMax() - number.getMin());
            SigmaShape.rounded(context, right - 126, cy + 23, 126, 3, 1.5f, SigmaColors.alpha(SigmaColors.BLACK, .12f * alpha));
            SigmaShape.rounded(context, right - 126, cy + 23, 126 * p, 3, 1.5f, SigmaColors.alpha(BLUE, alpha));
            SigmaShape.rounded(context, right - 132 + p * 126, cy + 18, 12, 12, 6, SigmaColors.alpha(BLUE, alpha));
            SigmaResources.light(14).drawString(context, format(number.getValue()), right - 126, cy + 1, SigmaColors.alpha(SigmaColors.BLACK, .7f * alpha));
        } else if (value instanceof ModeValue mode) {
            box(context, right - 123, cy + 5, 123, 27, alpha);
            clippedText(context, mode.getValue(), right - 114, cy + 8, 92, alpha);
            SigmaResources.light(14).drawString(context, "⌄", right - 17, cy + 8, SigmaColors.alpha(SigmaColors.BLACK, .6f * alpha));
        } else if (value instanceof TextValue text) {
            SigmaTextInput input = textInputs.computeIfAbsent(text, v -> { var t = new SigmaTextInput(8192); t.set(v.getValue()); return t; });
            if (!input.focused() && !input.value().equals(text.getValue())) input.set(text.getValue());
            box(context, right - 114, cy + 5, 114, 27, alpha);
            input.draw(context, SigmaResources.light(18), right - 107, cy + 8, 100, SigmaColors.alpha(SigmaColors.BLACK, .8f * alpha), "", text.isSensitive());
        } else if (value instanceof KeyValue key) {
            box(context, right - 123, cy + 5, 123, 27, alpha);
            clippedText(context, binding == key ? "Press key…" : KeyUtil.getKeyName(key.getValue()), right - 114, cy + 8, 110, alpha);
        } else if (value instanceof ColorValue color) {
            float bx = right - 140, by = cy + 10;
            Render2D.drawGradient(context, bx, by, 140, 64, SigmaColors.alpha(0xffffffff, alpha), SigmaColors.alpha(Color.HSBtoRGB(color.getHue(), 1, 1), alpha), true);
            Render2D.drawGradient(context, bx, by, 140, 64, 0, SigmaColors.alpha(0xff000000, alpha), false);
            float sx = bx + color.getSaturation() * 140, sy = by + (1 - color.getBrightness()) * 64;
            SigmaShape.rounded(context, sx - 4, sy - 4, 8, 8, 4, SigmaColors.alpha(0xffffffff, alpha));
            SigmaShape.rounded(context, sx - 2, sy - 2, 4, 4, 2, SigmaColors.alpha(color.getValue().getRGB(), alpha));
            for (int i = 0; i < 6; i++) Render2D.drawGradient(context, right - 136 + i * 95f / 6, cy + 89, 95f / 6, 8,
                    SigmaColors.alpha(Color.HSBtoRGB(i / 6f, 1, 1), alpha), SigmaColors.alpha(Color.HSBtoRGB((i + 1) / 6f, 1, 1), alpha), true);
            SigmaShape.rounded(context, right - 139 + color.getHue() * 95, cy + 86, 6, 14, 3, SigmaColors.alpha(0xffffffff, alpha));
            SigmaShape.rounded(context, right - 32, cy + 82, 25, 25, 12.5f, SigmaColors.alpha(color.getValue().getRGB(), alpha));
        }
    }

    private void drawDropdown(DrawContext context, float mx, float my, float alpha) {
        ModeValue mode = (ModeValue) dropdown.value;
        float dx = x + width - 153, dy = dropdownY(mode), dh = Math.min(mode.getModes().length * 27, SigmaDraw.height() - 20);
        dropdownScroll = Math.clamp(dropdownScroll, 0, Math.max(0, mode.getModes().length * 27 - dh));
        SigmaDraw.shadow(context, dx, dy, 123, dh, 12, alpha * .4f);
        SigmaShape.rounded(context, dx, dy, 123, dh, 6, SigmaColors.alpha(SigmaColors.WHITE, alpha));
        Render2D.beginScissor(context, dx, dy, 123, dh);
        for (int i = 0; i < mode.getModes().length; i++) {
            float ry = dy + i * 27 - dropdownScroll;
            boolean selected = mode.is(mode.getModes()[i]);
            if (selected || inside(mx, my, dx, ry, 123, 27)) Render2D.drawRect(context, dx, ry, 123, 27, SigmaColors.alpha(selected ? BLUE : 0xffe8e8e8, alpha));
            SigmaResources.light(18).drawString(context, mode.getModes()[i], dx + 9, ry + 3, SigmaColors.alpha(selected ? SigmaColors.WHITE : SigmaColors.BLACK, alpha));
        }
        Render2D.endScissor(context);
    }

    private float dropdownY(ModeValue mode) { return Math.max(10, Math.min(dropdown.y + 33, SigmaDraw.height() - mode.getModes().length * 27 - 10)); }
    private static void box(DrawContext c, float x, float y, float w, float h, float alpha) { SigmaShape.rounded(c, x, y, w, h, 5, SigmaColors.alpha(SigmaColors.BLACK, .07f * alpha)); }
    private static void clippedText(DrawContext c, String text, float x, float y, float w, float a) {
        Render2D.beginScissor(c, x, y, w, 24); SigmaResources.light(18).drawString(c, text, x, y, SigmaColors.alpha(SigmaColors.BLACK, a * .8f)); Render2D.endScissor(c);
    }
    public static String format(float value) { return java.math.BigDecimal.valueOf((double) Math.round(value * 10000) / 10000).stripTrailingZeros().toPlainString(); }
    public static boolean inside(double mx, double my, float x, float y, float w, float h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    /** False only for a click outside the modal, allowing its owner to close it. */
    public boolean click(float mx, float my, int button) {
        if (closing) return true;
        mx = local(mx, true); my = local(my, false);
        if (binding != null && binding.isMouseAllowed() && button >= 2) { binding.setValue(KeyUtil.mouseKeyCode(button)); binding = null; return true; }
        if (dropdown != null) {
            ModeValue mode = (ModeValue) dropdown.value; float dy = dropdownY(mode);
            int index = (int) ((my - dy + dropdownScroll) / 27);
            if (inside(mx, my, x + width - 153, dy, 123, Math.min(mode.getModes().length * 27, SigmaDraw.height() - 20)) && index >= 0 && index < mode.getModes().length) mode.setValue(mode.getModes()[index]);
            dropdown = null; return true;
        }
        unfocus(); binding = null; moduleBinding = false;
        if (!inside(mx, my, x, y, width, height)) return false;
        if (button != 0) return true;
        if (inside(mx, my, x + width - 170, y + 20, 140, 30)) { moduleBinding = true; return true; }
        if (!inside(mx, my, x + 10, y + 59, width - 20, height - 69)) return true;
        for (Row row : rows) {
            if (!inside(mx, my, x + 20, row.y, width - 40, row.height)) continue;
            float right = x + width - 30;
            if (row.value instanceof BoolValue bool) { bool.setValue(!bool.getValue()); SigmaSounds.play("click"); }
            else if (row.value instanceof ModeValue && mx >= right - 123) { dropdown = row; dropdownScroll = 0; }
            else if (row.value instanceof NumberValue && mx >= right - 132) { dragging = row; dragLocal(mx, my); }
            else if (row.value instanceof KeyValue key && mx >= right - 123) binding = key;
            else if (row.value instanceof TextValue text && mx >= right - 114) { focused = text; var input = textInputs.get(text); input.focus(true); input.selectAll(); }
            else if (row.value instanceof ColorValue) {
                if (inside(mx, my, right - 140, row.y + 10, 140, 64)) { dragging = row; dragPart = 0; dragLocal(mx, my); }
                else if (inside(mx, my, right - 136, row.y + 82, 95, 22)) { dragging = row; dragPart = 1; dragLocal(mx, my); }
            }
            return true;
        }
        return true;
    }

    public boolean drag(float mx, float my) {
        return dragLocal(local(mx, true), local(my, false));
    }
    private boolean dragLocal(float mx, float my) {
        if (dragging == null || !dragging.value.isVisible()) return false;
        float right = x + width - 30;
        if (dragging.value instanceof NumberValue n) {
            float raw = n.getMin() + Math.clamp((mx - right + 126) / 126, 0, 1) * (n.getMax() - n.getMin());
            n.setValue(n.getInc() > 0 ? n.getMin() + Math.round((raw - n.getMin()) / n.getInc()) * n.getInc() : raw);
        } else if (dragging.value instanceof ColorValue color) {
            if (dragPart == 0) color.setHSB(color.getHue(), Math.clamp((mx - right + 140) / 140, 0, 1), 1 - Math.clamp((my - dragging.y - 10) / 64, 0, 1));
            else color.setHSB(Math.clamp((mx - right + 136) / 95, 0, 1), color.getSaturation(), color.getBrightness());
        }
        return true;
    }
    public void release() { dragging = null; }
    public void scroll(double amount) {
        if (dropdown != null) { dropdownScroll = Math.clamp(dropdownScroll - (float) amount * 27, 0, Math.max(0, ((ModeValue) dropdown.value).getModes().length * 27 - SigmaDraw.height() + 20)); return; }
        scroll = Math.clamp(scroll - (float) amount * 34, 0, Math.max(0, contentHeight - height + 89));
    }
    public boolean key(KeyInput input) {
        if (binding != null || moduleBinding) {
            int key = input.key(); if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) key = -1;
            if (binding != null) binding.setValue(Math.max(0, key)); else module.setKey(key);
            binding = null; moduleBinding = false; return true;
        }
        if (dropdown != null && input.key() == GLFW.GLFW_KEY_ESCAPE) { dropdown = null; return true; }
        if (focused != null) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE || input.key() == GLFW.GLFW_KEY_ENTER) { unfocus(); return true; }
            var editor = textInputs.get(focused); if (editor.key(input)) { focused.setValue(editor.value()); return true; }
        }
        return false;
    }
    public boolean type(CharInput input) {
        if (focused == null) return false;
        var editor = textInputs.get(focused); if (editor.type(input)) { focused.setValue(editor.value()); return true; } return false;
    }
    private void unfocus() { if (focused != null) textInputs.get(focused).focus(false); focused = null; }
    private static final class Row {
        final Value value; float y; final float height, indent;
        Row(Value value, float y, float height, float indent) { this.value = value; this.y = y; this.height = height; this.indent = indent; }
    }
}
