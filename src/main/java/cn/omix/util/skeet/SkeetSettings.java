package cn.omix.util.skeet;

import cn.omix.Client;
import cn.omix.module.value.Value;
import cn.omix.module.value.impl.*;
import cn.omix.util.misc.KeyUtil;
import cn.omix.util.render.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static cn.omix.util.skeet.SkeetDraw.*;
import static cn.omix.util.skeet.SkeetLayout.*;

/** Direct Value adapters: no cached settings copy, so config loads and script changes remain live. */
public final class SkeetSettings {
    public record Row(Value value, Rect bounds) {}
    private List<Row> rows = List.of();
    private final Map<TextValue, SkeetTextField> texts = new IdentityHashMap<>();
    private Rect clip, window;
    private Value popup, dragging;
    private KeyValue binding;
    private TextValue focused;
    private float popupScroll;
    private boolean hueDrag;
    private String message = "";

    public String message() { return message; }

    public static float height(Value value) {
        if (value instanceof BoolValue || value instanceof ColorValue || value instanceof KeyValue) return 12;
        if (value instanceof NumberValue) return 24;
        return 28;
    }

    public void layout(List<Row> rows, Rect clip, Rect window) {
        this.rows = List.copyOf(rows); this.clip = clip; this.window = window;
        if (!available(popup)) popup = null;
        if (!available(dragging)) dragging = null;
        if (!available(binding)) binding = null;
        if (!available(focused)) blur();
        texts.keySet().removeIf(value -> find(value) == null);
    }

    private Row find(Value value) {
        if (value == null) return null;
        return rows.stream().filter(row -> row.value() == value).findFirst().orElse(null);
    }
    private boolean available(Value value) {
        Row row = find(value);
        return row != null && row.value().isVisible() && row.bounds().intersects(clip);
    }

    private static Rect field(Row row) {
        Rect r = row.bounds();
        if (row.value() instanceof NumberValue) return new Rect(r.x() + 10, r.y() + 12, r.width() - 20, 6);
        if (row.value() instanceof ColorValue) return new Rect(r.x() + r.width() - 23, r.y() + 2, 20, 8);
        if (row.value() instanceof KeyValue) return new Rect(r.x() + r.width() - 65, r.y(), 62, 12);
        return new Rect(r.x() + 10, r.y() + 12, r.width() - 20, 13);
    }

    public void drawRow(DrawContext c, SkeetDraw draw, Row row, float mx, float my) {
        Value value = row.value(); Rect r = row.bounds(), f = field(row);
        boolean hovered = r.contains(mx, my) && clip.contains(mx, my) && popup == null;
        if (value instanceof BoolValue bool) {
            draw.checkbox(c, r.x() + 3, r.y() + 2, bool.getValue(), hovered);
            draw.clipped(c, value.getName(), new Rect(r.x() + 15, r.y(), r.width() - 18, 12), TEXT);
            return;
        }
        float labelWidth = value instanceof KeyValue ? r.width() - 71 : value instanceof ColorValue ? r.width() - 30 : r.width() - 14;
        draw.clipped(c, value.getName(), new Rect(r.x() + 10, r.y(), labelWidth, 12), TEXT);
        if (value instanceof NumberValue n) {
            draw.field(c, f, hovered);
            float fraction = n.getMax() <= n.getMin() ? 0 : (n.getValue() - n.getMin()) / (n.getMax() - n.getMin());
            draw.gradient(c, f.x() + .5f, f.y() + .5f, (f.width() - 1) * fraction, f.height() - 1, ACCENT, 0x648628, false);
            String number = format(n.getValue());
            float tx = Math.clamp(f.x() + fraction * f.width() - draw.font().getStringWidth(number) / 2,
                    f.x(), Math.max(f.x(), f.x() + f.width() - draw.font().getStringWidth(number)));
            draw.text(c, number, tx, f.y() + 3, TEXT);
        } else if (value instanceof ColorValue color) {
            draw.field(c, f, hovered);
            draw.gradient(c, f.x() + 1, f.y() + 1, f.width() - 2, f.height() - 2,
                    color.getValue().getRGB(), color.getValue().darker().getRGB(), false);
        } else if (value instanceof KeyValue key) {
            String label = binding == key ? "[...]" : "[" + KeyUtil.getKeyName(key.getValue()) + "]";
            draw.clipped(c, label, f, binding == key ? ACCENT : MUTED);
        } else if (value instanceof TextValue text) {
            SkeetTextField editor = editor(text);
            if (focused != text && !editor.value().equals(text.getValue())) editor.set(text.getValue());
            editor.draw(c, draw, f, "...");
        } else if (value instanceof ModeValue mode) {
            combo(c, draw, f, mode.getValue(), hovered);
        } else if (value instanceof MultiBoolValue multi) {
            String selection = String.join(", ", multi.getValues().stream().filter(BoolValue::isVisible)
                    .filter(BoolValue::getValue).map(Value::getName).toList());
            combo(c, draw, f, selection.isEmpty() ? "None" : selection, hovered);
        }
    }

    private void combo(DrawContext c, SkeetDraw draw, Rect r, String label, boolean hovered) {
        draw.field(c, r, hovered);
        draw.clipped(c, label, new Rect(r.x() + 3, r.y(), r.width() - 13, r.height()), MUTED);
        Render2D.drawTriangle(c, r.x() + r.width() - 5, r.y() + 6, (float) Math.PI / 2, 3, draw.color(MUTED));
    }

    private List<String> options() {
        if (popup instanceof ModeValue mode) return List.of(mode.getModes());
        if (popup instanceof MultiBoolValue multi) return multi.getValues().stream().filter(BoolValue::isVisible).map(Value::getName).toList();
        return List.of();
    }
    private List<BoolValue> boolOptions() {
        return ((MultiBoolValue) popup).getValues().stream().filter(BoolValue::isVisible).toList();
    }
    private Rect popupBounds() {
        Row row = find(popup);
        if (row == null) return new Rect(0, 0, 0, 0);
        return SkeetLayout.popup(field(row), popup instanceof ColorValue ? 132 : field(row).width(),
                popup instanceof ColorValue ? 124 : Math.max(15, Math.min(180, options().size() * 15)), window);
    }

    public void drawOverlay(DrawContext c, SkeetDraw draw, float mx, float my) {
        if (!available(popup)) return;
        Rect r = popupBounds();
        draw.rect(c, r.x() - 1, r.y() - 1, r.width() + 2, r.height() + 2, 0x080808);
        draw.rect(c, r.x(), r.y(), r.width(), r.height(), 0x232323);
        if (popup instanceof ColorValue color) {
            Rect sv = saturationBox(r), hue = hueBox(r);
            draw.gradient(c, sv.x(), sv.y(), sv.width(), sv.height(), 0xffffff,
                    Color.HSBtoRGB(color.getHue(), 1, 1), true);
            // A translucent black overlay preserves the saturation gradient beneath it.
            Render2D.drawGradient(c, sv.x(), sv.y(), sv.width(), sv.height(), 0,
                    draw.color(0), false);
            for (int i = 0; i < 6; i++) draw.gradient(c, hue.x(), hue.y() + i * hue.height() / 6,
                    hue.width(), hue.height() / 6 + .1f, Color.HSBtoRGB(i / 6f, 1, 1), Color.HSBtoRGB((i + 1) / 6f, 1, 1), false);
            float sx = sv.x() + color.getSaturation() * sv.width(), sy = sv.y() + (1 - color.getBrightness()) * sv.height();
            Render2D.drawOutline(c, sx - 2, sy - 2, 4, 4, .5f, draw.color(TEXT));
            draw.rect(c, hue.x() - 1, hue.y() + color.getHue() * hue.height() - .5f, hue.width() + 2, 1, TEXT);
            draw.text(c, String.format("#%06X", color.getValue().getRGB() & 0xffffff), r.x() + 6, r.y() + 108, TEXT);
            return;
        }
        List<String> options = options();
        popupScroll = scroll(popupScroll, options.size() * 15, r.height());
        SkeetDraw.scissor(c, r);
        for (int i = 0; i < options.size(); i++) {
            Rect item = new Rect(r.x(), r.y() + i * 15 - popupScroll, r.width(), 15);
            if (!item.intersects(r)) continue;
            boolean selected = popup instanceof ModeValue mode ? mode.is(options.get(i)) : boolOptions().get(i).getValue();
            if (item.contains(mx, my)) draw.rect(c, item.x(), item.y(), item.width(), item.height(), 0x303030);
            draw.clipped(c, options.get(i), new Rect(item.x() + 4, item.y(), item.width() - 10, 15), selected ? ACCENT : TEXT);
        }
        if (options.isEmpty()) draw.text(c, "No options", r.x() + 3, r.y() + 2, MUTED);
        Render2D.endScissor(c);
        draw.scrollbar(c, r, options.size() * 15, popupScroll);
    }

    private static Rect saturationBox(Rect r) { return new Rect(r.x() + 6, r.y() + 6, 100, 96); }
    private static Rect hueBox(Rect r) { return new Rect(r.x() + 113, r.y() + 6, 12, 96); }

    /** Popup and key capture consume clicks before the screen or any underlying module. */
    public boolean overlayClick(float mx, float my, int button) {
        if (binding != null) {
            if (binding.isMouseAllowed() && button >= GLFW.GLFW_MOUSE_BUTTON_MIDDLE)
                binding.setValue(KeyUtil.mouseKeyCode(button));
            binding = null; return true;
        }
        if (popup == null) return false;
        Rect r = popupBounds();
        if (!r.contains(mx, my)) { popup = dragging = null; return true; }
        if (button != 0) return true;
        if (popup instanceof ColorValue) {
            if (saturationBox(r).contains(mx, my) || hueBox(r).contains(mx, my)) {
                hueDrag = hueBox(r).contains(mx, my); dragging = popup; drag(mx, my);
            }
        } else {
            int index = (int) ((my - r.y() + popupScroll) / 15);
            List<String> options = options();
            if (index >= 0 && index < options.size()) {
                if (popup instanceof ModeValue mode) {
                    try { mode.setValue(options.get(index)); message = ""; }
                    catch (RuntimeException error) {
                        message = "Could not select mode; see client log";
                        Client.logger.warn("Skeet mode selection failed", error);
                    }
                    popup = null;
                } else boolOptions().get(index).toggle();
            }
        }
        return true;
    }

    public boolean click(float mx, float my, int button) {
        blur();
        if (button != 0 || !clip.contains(mx, my)) return false;
        for (Row row : rows) {
            if (!row.bounds().contains(mx, my)) continue;
            Value value = row.value(); Rect f = field(row);
            if (value instanceof BoolValue bool) bool.toggle();
            else if (value instanceof NumberValue && new Rect(f.x(), f.y() - 2, f.width(), 13).contains(mx, my)) {
                dragging = value; drag(mx, my);
            } else if (f.contains(mx, my)) {
                if (value instanceof KeyValue key) binding = key;
                else if (value instanceof TextValue text) { focused = text; editor(text).focus(true); }
                else if (value instanceof ModeValue || value instanceof MultiBoolValue || value instanceof ColorValue) {
                    popup = value; popupScroll = 0;
                }
            }
            return true;
        }
        return false;
    }

    public boolean drag(float mx, float my) {
        if (!available(dragging)) { dragging = null; return false; }
        if (dragging instanceof NumberValue n) {
            Rect r = field(find(dragging));
            n.setValue(sliderValue((mx - r.x()) / r.width(), n.getMin(), n.getMax(), n.getInc()));
        } else if (dragging instanceof ColorValue color && popup == dragging) {
            Rect r = hueDrag ? hueBox(popupBounds()) : saturationBox(popupBounds());
            if (hueDrag) color.setHSB(Math.clamp((my - r.y()) / r.height(), 0, 1), color.getSaturation(), color.getBrightness());
            else color.setHSB(color.getHue(), Math.clamp((mx - r.x()) / r.width(), 0, 1), 1 - Math.clamp((my - r.y()) / r.height(), 0, 1));
        }
        return true;
    }
    public void release() { dragging = null; }
    public boolean scrollPopup(double amount) {
        if (popup == null) return false;
        if (!(popup instanceof ColorValue)) popupScroll = scroll(popupScroll - (float) amount * 15, options().size() * 15, popupBounds().height());
        return true;
    }
    public boolean key(KeyInput input) {
        if (binding != null) {
            int key = input.key();
            binding.setValue(key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE ? 0 : Math.max(0, key));
            binding = null; return true;
        }
        if (popup != null && input.key() == GLFW.GLFW_KEY_ESCAPE) { popup = dragging = null; return true; }
        if (focused != null) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE || input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) blur();
            else if (editor(focused).key(input)) focused.setValue(editor(focused).value());
            return true;
        }
        return false;
    }
    public boolean type(CharInput input) {
        if (focused == null) return false;
        if (editor(focused).type(input)) focused.setValue(editor(focused).value());
        return true;
    }
    private SkeetTextField editor(TextValue value) {
        return texts.computeIfAbsent(value, key -> { var field = new SkeetTextField(32768, key.isSensitive()); field.set(key.getValue()); return field; });
    }
    public void blur() { if (focused != null) editor(focused).focus(false); focused = null; }
    public void reset() { blur(); popup = dragging = binding = null; message = ""; }
}
