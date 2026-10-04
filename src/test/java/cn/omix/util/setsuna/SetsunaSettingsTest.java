package cn.omix.util.setsuna;

import cn.omix.module.value.Value;
import cn.omix.module.value.impl.*;
import cn.omix.util.misc.KeyUtil;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static cn.omix.util.setsuna.SetsunaLayout.Rect;
import static org.junit.jupiter.api.Assertions.*;

class SetsunaSettingsTest {
    private final SetsunaSettings settings = new SetsunaSettings();
    private final Rect clip = new Rect(0, 0, 196, 260);
    private void show(Value... values) { settings.layout(List.of(values), clip, 0); }
    private KeyInput key(int key) { return new KeyInput(key, 0, 0); }

    @Test void modeCyclesLiveScriptOptionsThroughTheNativeListener() {
        var mode = new ModeValue("Mode", "Native", "Native");
        var changes = new AtomicInteger(); mode.onChange((old, next) -> changes.incrementAndGet());
        show(mode); var registration = mode.addMode("Script");
        settings.click(20, 30, 0); assertEquals("Script", mode.getValue());
        settings.click(20, 30, 1); assertEquals("Native", mode.getValue());
        assertEquals(2, changes.get());
        registration.close(); settings.click(20, 30, 0); assertEquals("Native", mode.getValue());
    }
    @Test void sliderHonorsFractionalMinimumAndStopsWhenHiddenOrRemoved() {
        var visible = new AtomicBoolean(true);
        var n = new NumberValue("Speed", .05f, .05f, .95f, .1f, visible::get);
        show(n); settings.click(98, 32, 0); assertEquals(.55f, n.getValue(), .00001f);
        settings.drag(999); assertEquals(.95f, n.getValue(), .00001f);
        visible.set(false); assertFalse(settings.drag(0)); assertEquals(.95f, n.getValue(), .00001f);
        visible.set(true); show(n); settings.click(98, 32, 0); show();
        assertFalse(settings.drag(999)); assertEquals(.55f, n.getValue(), .00001f);
    }
    @Test void aClippedRowCannotBeClickedAndScrollingCancelsCapture() {
        var key = new KeyValue("Bind", GLFW.GLFW_KEY_B);
        settings.layout(List.of(key), new Rect(0, 40, 196, 30), 10);
        assertFalse(settings.click(30, 35, 0)); assertFalse(settings.key(key(GLFW.GLFW_KEY_C)));
        settings.click(30, 45, 0);
        settings.layout(List.of(key), new Rect(0, 40, 196, 30), 31);
        assertFalse(settings.key(key(GLFW.GLFW_KEY_C))); assertEquals(GLFW.GLFW_KEY_B, key.getValue());
    }
    @Test void multiBoolFiltersHiddenChildrenAndRespectsParentScriptVisibility() {
        var first = new BoolValue("First", false); var hidden = new BoolValue("Hidden", false, () -> false);
        var last = new BoolValue("Last", false);
        var multi = new MultiBoolValue("Flags", first, hidden, last);
        show(multi); settings.click(20, 12, 0); show(multi);
        assertEquals(93, settings.contentHeight(List.of(multi)));
        settings.click(30, 42, 0); settings.click(30, 73, 0);
        assertTrue(first.getValue()); assertTrue(last.getValue()); assertFalse(hidden.getValue());
        multi.setScriptVisibility(() -> false); show(multi); settings.click(30, 42, 0);
        assertTrue(first.getValue()); assertEquals(0, settings.contentHeight(List.of(multi)));
    }
    @Test void bindingSupportsCancelClearKeyboardAndNativeMouseEncoding() {
        var bind = new KeyValue("Bind", GLFW.GLFW_KEY_B, true);
        show(bind); settings.click(120, 15, 0); settings.key(key(GLFW.GLFW_KEY_ESCAPE));
        assertEquals(GLFW.GLFW_KEY_B, bind.getValue());
        settings.click(120, 15, 0); settings.key(key(GLFW.GLFW_KEY_DELETE)); assertEquals(0, bind.getValue());
        settings.click(120, 15, 0); settings.key(key(GLFW.GLFW_KEY_F)); assertEquals(GLFW.GLFW_KEY_F, bind.getValue());
        settings.click(120, 15, 0); assertTrue(settings.captureMouse(2)); assertEquals(KeyUtil.mouseKeyCode(2), bind.getValue());
        settings.click(120, 15, 1); assertEquals(0, bind.getValue());
        var keyboardOnly = new KeyValue("Key", 0); show(keyboardOnly);
        settings.click(120, 15, 0); assertTrue(settings.captureMouse(2)); assertEquals(0, keyboardOnly.getValue());
        settings.key(key(GLFW.GLFW_KEY_G)); assertEquals(GLFW.GLFW_KEY_G, keyboardOnly.getValue());
    }
    @Test void textEditsWriteNativeValueAndRemoveWholeUnicodeCodePoints() {
        var text = new TextValue("Text", "A"); show(text); settings.click(40, 30, 0);
        assertTrue(settings.type(new CharInput(0x1f600, 0))); assertEquals("A😀", text.getValue());
        settings.key(key(GLFW.GLFW_KEY_BACKSPACE)); assertEquals("A", text.getValue());
        settings.key(new KeyInput(GLFW.GLFW_KEY_A, 0, GLFW.GLFW_MOD_CONTROL));
        settings.type(new CharInput('中', 0)); assertEquals("中", text.getValue());
        settings.key(key(GLFW.GLFW_KEY_ENTER)); assertFalse(settings.type(new CharInput('B', 0)));
        text.setValue("Loaded config"); show(text); settings.click(40, 30, 0);
        settings.type(new CharInput('!', 0)); assertEquals("Loaded config!", text.getValue());
    }
    @Test void sensitiveCopyAndCutDoNotExposeOrEraseTheValue() {
        var secret = new TextValue("Secret", "token", () -> true, true);
        show(secret); settings.click(40, 30, 0);
        settings.key(new KeyInput(GLFW.GLFW_KEY_A, 0, GLFW.GLFW_MOD_CONTROL));
        // Works without Minecraft/clipboard initialization because protected text never touches it.
        settings.key(new KeyInput(GLFW.GLFW_KEY_C, 0, GLFW.GLFW_MOD_CONTROL));
        settings.key(new KeyInput(GLFW.GLFW_KEY_X, 0, GLFW.GLFW_MOD_CONTROL));
        assertEquals("token", secret.getValue());
    }
    @Test void rgbAndHexEditingPreserveOtherChannelsAndIgnoreInvalidInput() {
        var color = new ColorValue("Color", new Color(10, 20, 30)); show(color);
        settings.click(140, 40, 0); settings.drag(999); settings.release();
        assertEquals(new Color(255, 20, 30), color.getValue());
        settings.click(120, 14, 0);
        settings.key(new KeyInput(GLFW.GLFW_KEY_A, 0, GLFW.GLFW_MOD_CONTROL));
        for (char ch : "#112233".toCharArray()) settings.type(new CharInput(ch, 0));
        assertEquals(new Color(0x112233), color.getValue());
        settings.type(new CharInput('x', 0)); assertEquals(new Color(0x112233), color.getValue());
        settings.key(key(GLFW.GLFW_KEY_ESCAPE)); assertEquals(new Color(255, 20, 30), color.getValue());
    }
    @Test void disappearingTextFieldCannotWriteIntoUnloadedScript() {
        var text = new TextValue("Text", "Original"); show(text); settings.click(40, 30, 0);
        show(); assertFalse(settings.type(new CharInput('x', 0))); assertEquals("Original", text.getValue());
    }
    @Test void configurationLoadCancelsAStaleTextOrColorEditor() {
        var text = new TextValue("Text", "Original"); show(text); settings.click(40, 30, 0);
        text.setValue("Loaded"); show(text);
        assertFalse(settings.type(new CharInput('x', 0))); assertEquals("Loaded", text.getValue());
        var color = new ColorValue("Color", Color.RED); show(color); settings.click(120, 14, 0);
        color.setValue(Color.BLUE); show(color);
        assertFalse(settings.key(key(GLFW.GLFW_KEY_ESCAPE))); assertEquals(Color.BLUE, color.getValue());
    }
    @Test void resizedSliderUsesItsNewDrawingCoordinatesWhileDragging() {
        var number = new NumberValue("Number", 0, 0, 100); show(number); settings.click(98, 32, 0);
        settings.layout(List.of(number), new Rect(100, 50, 196, 260), 0);
        settings.drag(198); assertEquals(50, number.getValue());
        settings.release(); assertFalse(settings.drag(999));
    }
}
