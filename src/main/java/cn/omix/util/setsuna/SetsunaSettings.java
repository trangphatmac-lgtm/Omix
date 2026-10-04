package cn.omix.util.setsuna;

import cn.omix.module.value.Value;
import cn.omix.module.value.impl.*;
import cn.omix.util.misc.KeyUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static cn.omix.util.setsuna.SetsunaLayout.*;

/** Pop controls backed directly by Omix Values, including live script options/visibility. */
public final class SetsunaSettings {
    public record Row(Value value, Rect bounds, boolean child) {}
    private List<Row> rows = List.of();
    private Rect clip = new Rect(0, 0, 0, 0);
    private final Set<MultiBoolValue> expanded = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<BoolValue, Float> toggles = new IdentityHashMap<>();
    private Value dragging, editing;
    private KeyValue binding;
    private SetsunaTextField editor;
    private Color originalColor, lastEditedColor;
    private int colorChannel;
    private float trackX, trackWidth;

    public static float height(Value value) {
        if (value instanceof ColorValue) return 85;
        if (value instanceof NumberValue || value instanceof ModeValue || value instanceof TextValue) return 44;
        return 31;
    }
    public static boolean supported(Value value) {
        return value instanceof BoolValue || value instanceof NumberValue || value instanceof ModeValue
                || value instanceof TextValue || value instanceof ColorValue || value instanceof KeyValue || value instanceof MultiBoolValue;
    }
    private List<Value> visible(List<Value> values) { return values.stream().filter(Value::isVisible).filter(SetsunaSettings::supported).toList(); }
    public float contentHeight(List<Value> values) {
        float height = 0;
        for (Value value : visible(values)) {
            height += height(value);
            if (value instanceof MultiBoolValue multi && expanded.contains(multi))
                height += multi.getValues().stream().filter(Value::isVisible).count() * 31;
        }
        return height;
    }
    public void layout(List<Value> values, Rect clip, float scroll) {
        this.clip = clip;
        List<Row> next = new ArrayList<>();
        float y = clip.y() - scroll;
        for (Value value : visible(values)) {
            next.add(new Row(value, new Rect(clip.x(), y, clip.width(), height(value)), false));
            y += height(value);
            if (value instanceof MultiBoolValue multi && expanded.contains(multi)) {
                for (BoolValue child : multi.getValues()) if (child.isVisible()) {
                    next.add(new Row(child, new Rect(clip.x() + 8, y, clip.width() - 8, 31), true)); y += 31;
                }
            }
        }
        rows = List.copyOf(next);
        expanded.removeIf(value -> !values.contains(value));
        toggles.keySet().removeIf(value -> find(value) == null);
        if (!available(dragging)) dragging = null;
        else {
            Row row = find(dragging);
            Rect track = dragging instanceof NumberValue ? numberTrack(row.bounds()) : colorTrack(row.bounds(), colorChannel);
            trackX = track.x(); trackWidth = track.width();
        }
        if (!available(binding)) binding = null;
        if (!available(editing)) blur();
        // A configuration/script may replace a value while this field has focus.
        // Release the stale editor before its next keystroke can overwrite the loaded value.
        if (editing instanceof TextValue text && !text.getValue().equals(editor.value())) blur();
        if (editing instanceof ColorValue color && !color.getValue().equals(lastEditedColor)) blur();
    }
    private Row find(Value value) { return value == null ? null : rows.stream().filter(row -> row.value() == value).findFirst().orElse(null); }
    private boolean available(Value value) {
        Row row = find(value);
        return row != null && value.isVisible() && row.bounds().intersects(clip);
    }
    public String hint() {
        if (binding != null) return "Press a key / Esc cancels / Delete clears";
        if (editing instanceof ColorValue) return "#RRGGBB / Enter applies / Esc restores";
        if (editing != null) return "Enter finishes editing / Ctrl+A selects all";
        return "Left: toggle / Right: settings / Middle: bind";
    }
    public void draw(DrawContext c, SetsunaDraw draw, float mx, float my, float delta) {
        SetsunaDraw.scissor(c, clip);
        for (Row row : rows) {
            if (!row.bounds().intersects(clip)) continue;
            Value value = row.value(); Rect r = row.bounds();
            boolean hovered = r.contains(mx, my) && clip.contains(mx, my);
            draw.rounded(c, new Rect(r.x() + 7, r.y() + 2, r.width() - 14, r.height() - 4), 5, hovered ? draw.hover() : draw.inner());
            float reserve = value instanceof BoolValue ? 60 : value instanceof ColorValue ? 118
                    : value instanceof KeyValue || value instanceof NumberValue ? 82 : value instanceof MultiBoolValue ? 64 : 26;
            draw.label(c, value.getName(), new Rect(r.x() + 13, r.y(), r.width() - reserve, 27), draw.dim(), 8, false);
            if (value instanceof BoolValue bool) {
                float progress = animate(toggles.getOrDefault(bool, bool.getValue() ? 1f : 0f), bool.getValue() ? 1 : 0, 14, delta);
                toggles.put(bool, progress);
                draw.rounded(c, new Rect(r.right() - 40, r.y() + 10, 27, 12), 6, SetsunaDraw.mix(draw.track(), draw.accent, progress));
                draw.circle(c, r.right() - 34 + progress * 15, r.y() + 16, 4, draw.text());
            } else if (value instanceof NumberValue number) {
                draw.right(c, format(number.getValue()), new Rect(r.right() - 65, r.y(), 52, 23), draw.accent, 8);
                draw.slider(c, numberTrack(r), fraction(number), draw.accent);
            } else if (value instanceof ModeValue mode) {
                Rect f = field(r);
                draw.rounded(c, f, 4, draw.track());
                draw.label(c, mode.getValue(), new Rect(f.x() + 4, f.y(), f.width() - 8, f.height()), draw.accent, 8, false);
            } else if (value instanceof KeyValue key) {
                draw.right(c, binding == key ? "..." : KeyUtil.getKeyName(key.getValue()),
                        new Rect(r.right() - 70, r.y(), 57, 31), draw.accent, 8);
            } else if (value instanceof TextValue text) {
                if (editing == text) editor.draw(c, draw, field(r));
                else {
                    draw.rounded(c, field(r), 4, draw.track());
                    draw.label(c, text.isSensitive() ? "*".repeat(Math.min(32, text.getValue().length())) : text.getValue(),
                            inset(field(r), 4), draw.text(), 8, false);
                }
            } else if (value instanceof MultiBoolValue multi) {
                long count = multi.getValues().stream().filter(Value::isVisible).filter(BoolValue::getValue).count();
                draw.right(c, count + (expanded.contains(multi) ? "  -" : "  +"),
                        new Rect(r.right() - 50, r.y(), 37, 31), draw.accent, 8);
            } else if (value instanceof ColorValue color) {
                Rect field = colorField(r);
                if (editing == color) editor.draw(c, draw, field);
                else { draw.rounded(c, field, 4, draw.track()); draw.label(c, hex(color), inset(field, 3), draw.dim(), 7, false); }
                draw.rounded(c, new Rect(r.right() - 29, r.y() + 7, 16, 16), 4, color.getValue().getRGB());
                int rgb = color.getValue().getRGB();
                int[] colors = {0xffe85b5b, 0xff55d780, 0xff5a9feb};
                for (int i = 0; i < 3; i++) {
                    int channel = rgb >> (16 - i * 8) & 255;
                    float y = r.y() + 31 + i * 18;
                    draw.label(c, new String[]{"R", "G", "B"}[i], new Rect(r.x() + 13, y, 12, 18), colors[i], 7, true);
                    draw.slider(c, colorTrack(r, i), channel / 255f, colors[i]);
                    draw.right(c, Integer.toString(channel), new Rect(r.right() - 39, y, 26, 18), draw.text(), 7);
                }
            }
        }
        c.disableScissor();
    }
    private static Rect inset(Rect r, float n) { return new Rect(r.x() + n, r.y(), r.width() - n * 2, r.height()); }
    private static Rect field(Rect r) { return new Rect(r.x() + 13, r.y() + 24, r.width() - 26, 16); }
    private static Rect colorField(Rect r) { return new Rect(r.right() - 91, r.y() + 7, 57, 16); }
    private static Rect numberTrack(Rect r) { return new Rect(r.x() + 13, r.y() + 26, r.width() - 26, 12); }
    private static Rect colorTrack(Rect r, int channel) { return new Rect(r.x() + 28, r.y() + 34 + channel * 18, r.width() - 76, 12); }
    private static float fraction(NumberValue n) { return n.getMax() <= n.getMin() ? 0 : (n.getValue() - n.getMin()) / (n.getMax() - n.getMin()); }
    public static String format(float value) { return java.math.BigDecimal.valueOf(Double.parseDouble(Float.toString(value))).stripTrailingZeros().toPlainString(); }
    private static String hex(ColorValue value) { return String.format(Locale.ROOT, "#%06X", value.getValue().getRGB() & 0xffffff); }

    public boolean click(float mx, float my, int button) {
        if (captureMouse(button)) return true;
        // Clicking away commits valid text; invalid hex never changes the native color.
        blur(); dragging = null;
        if (!clip.contains(mx, my)) return false;
        for (Row row : rows) {
            Rect r = row.bounds(); Value value = row.value();
            if (!r.contains(mx, my) || !value.isVisible()) continue;
            if (value instanceof BoolValue bool && button == 0) bool.toggle();
            else if (value instanceof MultiBoolValue multi && button == 0) {
                if (!expanded.remove(multi)) expanded.add(multi);
            } else if (value instanceof ModeValue mode && (button == 0 || button == 1)) {
                List<String> modes = List.of(mode.getModes());
                if (!modes.isEmpty()) mode.setValue(modes.get(Math.floorMod(modes.indexOf(mode.getValue()) + (button == 0 ? 1 : -1), modes.size())));
            } else if (value instanceof KeyValue key) {
                if (button == 0) binding = key;
                else if (button == 1) key.setValue(0);
            } else if (value instanceof TextValue text && button == 0) {
                editing = text; editor = new SetsunaTextField(text.getValue(), text.isSensitive());
            } else if (value instanceof NumberValue && button == 0) beginDrag(value, numberTrack(r), mx, 0);
            else if (value instanceof ColorValue color && button == 0) {
                if (colorField(r).contains(mx, my)) {
                    editing = color; originalColor = lastEditedColor = color.getValue(); editor = new SetsunaTextField(hex(color), false);
                } else for (int i = 0; i < 3; i++) if (colorTrack(r, i).contains(mx, my)) beginDrag(color, colorTrack(r, i), mx, i);
            }
            return true;
        }
        return true;
    }
    public boolean captureMouse(int button) {
        if (binding == null || !available(binding)) return false;
        if (binding.isMouseAllowed()) { binding.setValue(KeyUtil.mouseKeyCode(button)); binding = null; }
        return true;
    }
    private void beginDrag(Value value, Rect track, float mx, int channel) {
        dragging = value; trackX = track.x(); trackWidth = track.width(); colorChannel = channel; drag(mx);
    }
    public boolean drag(float mx) {
        if (!available(dragging)) { dragging = null; return false; }
        float fraction = Math.clamp((mx - trackX) / Math.max(1, trackWidth), 0, 1);
        if (dragging instanceof NumberValue n) {
            float value = lerp(n.getMin(), n.getMax(), fraction);
            if (n.getInc() > 0) value = n.getMin() + Math.round((value - n.getMin()) / n.getInc()) * n.getInc();
            n.setValue(fraction == 1 ? n.getMax() : value);
        } else if (dragging instanceof ColorValue color) {
            int shift = 16 - colorChannel * 8;
            int rgb = color.getValue().getRGB() & ~(255 << shift) | Math.round(fraction * 255) << shift;
            color.setValue(new Color(rgb));
        }
        return true;
    }
    public void release() { dragging = null; }
    public void reset() { blur(); binding = null; dragging = null; }
    public void blur() { editing = null; editor = null; originalColor = lastEditedColor = null; }
    public boolean key(KeyInput input) {
        if (binding != null && available(binding)) {
            int key = input.key();
            if (key != GLFW.GLFW_KEY_ESCAPE && key != GLFW.GLFW_KEY_UNKNOWN)
                binding.setValue(key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE ? 0 : key);
            binding = null; return true;
        }
        if (editing == null || !available(editing)) return false;
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (editing instanceof ColorValue color && originalColor != null) color.setValue(originalColor);
            blur();
        } else if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) blur();
        else { editor.key(input); applyText(); }
        return true;
    }
    public boolean type(CharInput input) {
        if (editing == null || !available(editing)) return false;
        if (editor.type(input)) applyText();
        return true;
    }
    private void applyText() {
        if (editing instanceof TextValue text) text.setValue(editor.value());
        else if (editing instanceof ColorValue color && editor.value().matches("#?[0-9a-fA-F]{6}")) {
            color.setValue(new Color(Integer.parseInt(editor.value().replace("#", ""), 16)));
            lastEditedColor = color.getValue();
        }
    }
}
