package cn.omix.util.opai.bridge;

import cn.omix.module.value.impl.*;
import java.awt.Color;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SettingsAdapterTest {
    @Test void allControlsWriteTheOriginalNativeValues() {
        var bool = new BoolValue("Toggle", false);
        ((BooleanSetting) Setting.wrap(bool)).m217(true);
        assertTrue(bool.getValue());
        var number = new NumberValue("Number", 1, 1, 4, .25);
        var slider = (NumberSetting) Setting.wrap(number);
        slider.m223(2.62);
        assertEquals(2.5f, number.getValue());
        slider.m223(100);
        assertEquals(4f, number.getValue());
        var mode = new ModeValue("Mode", "A", "A", "B");
        var choice = (ModeSetting) Setting.wrap(mode);
        choice.select(1);
        assertTrue(mode.is("B"));
        mode.addMode("Script");
        assertArrayEquals(new String[]{"A", "B", "Script"}, choice.options());
        mode.setValue("A");
        assertEquals("A", choice.selectionLabel());
    }

    @Test void childVisibilityAndSensitiveTextRemainNative() {
        var visible = new java.util.concurrent.atomic.AtomicBoolean(false);
        var group = new MultiBoolValue("Group", new BoolValue("Always", true), new BoolValue("Conditional", false, visible::get));
        var multi = new MultiSelectSetting(group);
        assertArrayEquals(new String[]{"Always"}, multi.options());
        visible.set(true);
        multi.select(1);
        assertTrue(group.isEnabled("Conditional"));
        var secret = new TextValue("Secret", "must remain hidden", () -> true, true);
        assertFalse(((EditableSetting) Setting.wrapAll(secret).getFirst()).selectionLabel().contains(secret.getValue()));
        var key = new KeyValue("Bind", -1, true);
        assertSame(key, Setting.wrapAll(key).getFirst().value);
    }

    @Test void colorSlidersPreserveOtherChannelsAndOriginalIdentity() {
        var color = new ColorValue("Accent", Color.RED);
        var channels = Setting.wrapAll(color);
        assertEquals(3, channels.size());
        channels.forEach(channel -> assertSame(color, channel.value));
        ((NumberSetting) channels.get(0)).m223(50);
        ((NumberSetting) channels.get(1)).m223(25);
        ((NumberSetting) channels.get(2)).m223(75);
        assertEquals(.5f, color.getHue());
        assertEquals(.25f, color.getSaturation());
        assertEquals(.75f, color.getBrightness());
    }
}
