package cn.omix.util.script;

import cn.omix.module.value.impl.*;
import org.junit.jupiter.api.Test;
import java.awt.Color;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ScriptModuleSettingsTest {
    @Test void modeWritesUseNativeListenersAndInvalidValuesDoNotMutate() {
        var mode = new ModeValue("Mode", "Built-in", "Built-in", "Custom");
        var changes = new AtomicInteger(); mode.onChange((before, after) -> changes.incrementAndGet());
        ScriptModuleSettings.write(mode, "custom"); assertEquals("Custom", mode.getValue()); assertEquals(1, changes.get());
        assertThrows(IllegalArgumentException.class, () -> ScriptModuleSettings.write(mode, "absent"));
        assertEquals("Custom", mode.getValue()); assertEquals(1, changes.get());
        mode.onChange((before, after) -> {throw new IllegalStateException("rejected");});
        assertThrows(IllegalStateException.class, () -> ScriptModuleSettings.write(mode, "Built-in"));
        assertEquals("Custom", mode.getValue());
    }
    @Test void numbersAreFiniteAndInRangeWithNoImplicitTypeConversion() {
        var value = new NumberValue("Speed", 1, 0, 5, .1);
        ScriptModuleSettings.write(value, 2.25); assertEquals(2.25f, value.getValue());
        for (Object bad : List.of("3", false, Double.NaN, Double.POSITIVE_INFINITY, 6, -1))
            assertThrows(IllegalArgumentException.class, () -> ScriptModuleSettings.write(value, bad));
        assertEquals(2.25f, value.getValue());
        var decimal = new NumberValue("Decimal", .1, .1, .3, .1);
        ScriptModuleSettings.write(decimal, .1);
        ScriptModuleSettings.write(decimal, .3);
        ScriptModuleSettings.write(decimal, (double) decimal.getValue());
        assertEquals(.3f, decimal.getValue());
        var descriptor = ScriptModuleSettings.describe(value);
        assertEquals(5, descriptor.get("max").getAsInt());
        assertTrue(descriptor.get("visible").getAsBoolean());
    }
    @Test void multiOptionPatchValidatesAllChildrenAndReadsAreDetached() {
        var first = new BoolValue("One", false); var second = new BoolValue("Two", false);
        var value = new MultiBoolValue("Targets", first, second);
        var invalid = new LinkedHashMap<String, Boolean>(); invalid.put("One", true); invalid.put("Missing", true);
        assertThrows(IllegalArgumentException.class, () -> ScriptModuleSettings.write(value, invalid)); assertFalse(first.getValue());
        ScriptModuleSettings.write(value, Map.of("one", true)); assertTrue(first.getValue()); assertFalse(second.getValue());
        var snapshot = ScriptModuleSettings.read(value).getAsJsonObject(); snapshot.addProperty("One", false); assertTrue(first.getValue());
        assertThrows(IllegalArgumentException.class, () -> ScriptModuleSettings.write(value, Map.of("One", true, "one", false)));
    }
    @Test void booleanTextColorKeyAndLookupHaveExplicitTypes() {
        var bool = new BoolValue("Enabled", false); ScriptModuleSettings.write(bool, true); assertTrue(bool.getValue());
        assertThrows(IllegalArgumentException.class, () -> ScriptModuleSettings.write(bool, "true"));
        var text = new TextValue("Text", "old"); ScriptModuleSettings.write(text, "中文"); assertEquals("中文", text.getValue());
        var color = new ColorValue("Color", Color.RED); ScriptModuleSettings.write(color, Color.BLUE); assertEquals(Color.BLUE, color.getValue());
        ScriptModuleSettings.write(color, 0xff00ff00); assertEquals(Color.GREEN, color.getValue());
        var key = new KeyValue("Key", 0); ScriptModuleSettings.write(key, 82); assertEquals(82, key.getValue());
        assertThrows(IllegalArgumentException.class, () -> ScriptModuleSettings.write(key, 1.5));
        assertThrows(IllegalArgumentException.class, () -> ScriptModuleSettings.write(key, Long.MAX_VALUE));
        assertSame(text, ScriptModuleSettings.find(List.of(text), "text"));
        assertThrows(IllegalArgumentException.class, () -> ScriptModuleSettings.find(List.of(text), "missing"));
    }
}
