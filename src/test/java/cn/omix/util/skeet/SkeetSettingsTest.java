package cn.omix.util.skeet;

import cn.omix.module.value.impl.*;
import net.minecraft.client.input.KeyInput;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static cn.omix.util.skeet.SkeetLayout.Rect;
import static org.junit.jupiter.api.Assertions.*;

class SkeetSettingsTest {
    private final SkeetSettings settings = new SkeetSettings();
    private final Rect clip = new Rect(0, 0, 320, 310);

    private void show(cn.omix.module.value.Value... values) {
        var rows = new java.util.ArrayList<SkeetSettings.Row>();
        for (int i = 0; i < values.length; i++) rows.add(new SkeetSettings.Row(values[i], new Rect(0, i * 40, 140, SkeetSettings.height(values[i]))));
        settings.layout(rows, clip, clip);
    }

    @Test void modeDropdownUsesLiveScriptOptionsAndNativeChangeListeners() {
        var mode = new ModeValue("Mode", "Native", "Native");
        var changes = new AtomicInteger();
        mode.onChange((before, after) -> changes.incrementAndGet());
        show(mode);
        settings.click(20, 17, 0);
        mode.addMode("Script");
        assertTrue(settings.overlayClick(20, 46, 0));
        assertEquals("Script", mode.getValue());
        assertEquals(1, changes.get());
        assertFalse(settings.overlayClick(20, 46, 0));
    }

    @Test void multiSelectStaysOpenAndHiddenOptionsCannotBeToggled() {
        var first = new BoolValue("First", false);
        var hidden = new BoolValue("Hidden", false, () -> false);
        var last = new BoolValue("Last", false);
        show(new MultiBoolValue("Targets", first, hidden, last));
        settings.click(20, 17, 0);
        settings.overlayClick(20, 31, 0);
        settings.overlayClick(20, 46, 0);
        assertTrue(first.getValue()); assertTrue(last.getValue()); assertFalse(hidden.getValue());
        assertTrue(settings.overlayClick(200, 200, 0), "Outside click is consumed when dismissing a popup");
        assertFalse(settings.overlayClick(200, 200, 0));
    }

    @Test void sliderDragUsesRealValuesClampsOutsideAndStopsWhenHidden() {
        var visible = new AtomicBoolean(true);
        var number = new NumberValue("Speed", .05f, .05f, .95f, .1f, visible::get);
        show(number);
        settings.click(70, 15, 0);
        assertEquals(.55f, number.getValue(), .00001f);
        settings.drag(400, -10);
        assertEquals(.95f, number.getValue(), .00001f);
        visible.set(false);
        assertFalse(settings.drag(0, 0));
        assertEquals(.95f, number.getValue(), .00001f);
    }

    @Test void hiddenOrRemovedKeyFieldsReleaseCaptureAndEscapeClearsWithoutClosing() {
        var key = new KeyValue("Bind", GLFW.GLFW_KEY_B);
        show(key);
        settings.click(100, 5, 0);
        assertTrue(settings.key(new KeyInput(GLFW.GLFW_KEY_ESCAPE, 0, 0)));
        assertEquals(0, key.getValue());
        settings.click(100, 5, 0);
        settings.layout(List.of(), clip, clip);
        assertFalse(settings.key(new KeyInput(GLFW.GLFW_KEY_C, 0, 0)));
        assertEquals(0, key.getValue());
    }

    @Test void profileNamesArePortableAndCannotEscapeConfigDirectory() {
        for (String name : new String[]{"", " ", "../x", "..\\x", "/tmp/x", "a:b", "name\n", "a.", "CON", "aux.json", "COM1", "LPT9.txt"})
            assertFalse(SkeetProfiles.validName(name), name);
        for (String name : new String[]{"Default", "中文配置", "combat.v2", "Profile 1", "COM10"})
            assertTrue(SkeetProfiles.validName(name), name);
    }
}
