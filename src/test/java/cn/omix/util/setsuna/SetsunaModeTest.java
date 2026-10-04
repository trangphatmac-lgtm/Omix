package cn.omix.util.setsuna;

import cn.omix.module.impl.render.ClickGui;
import cn.omix.module.value.Value;
import cn.omix.module.value.impl.ModeValue;
import org.junit.jupiter.api.Test;

import java.awt.Font;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SetsunaModeTest {
    @Test void existingModesAndDefaultArePreservedAndOptionsUseNativeValueDeclarations() throws Exception {
        ClickGui gui = new ClickGui(); List<Value> values = new ArrayList<>();
        for (var field : ClickGui.class.getDeclaredFields()) if (Value.class.isAssignableFrom(field.getType())) {
            field.setAccessible(true); values.add((Value) field.get(gui));
        }
        var mode = (ModeValue) values.stream().filter(value -> value.getName().equals("Mode")).findFirst().orElseThrow();
        assertEquals("Web", mode.getValue());
        assertArrayEquals(new String[]{"Web", "Remix", "Sigma", "Skeet", "Setsuna"}, mode.getModes());
        List<Value> options = values.stream().filter(value -> value != mode).toList();
        assertEquals(4, options.size()); assertTrue(options.stream().noneMatch(Value::isVisible));
        mode.setValue("Setsuna"); assertTrue(options.stream().allMatch(Value::isVisible));
        assertEquals(5, gui.setsunaBlur()); assertEquals(1, gui.setsunaScale());
        assertFalse(gui.setsunaDaylight()); assertEquals(0xffa656ee, gui.setsunaAccent());
        mode.setValue("Skeet"); assertTrue(options.stream().noneMatch(Value::isVisible));
    }
    @Test void bundledIconFontContainsEveryCategoryGlyph() throws Exception {
        Path root = Path.of(System.getProperty("omix.test.root"));
        Font font = Font.createFont(Font.TRUETYPE_FONT, root.resolve("src/main/resources/assets/omix/setsuna/lucide.ttf").toFile());
        for (int glyph : new int[]{0xe2b4, 0xe29c, 0xe1dd, 0xe3b9, 0xe19f, 0xe154}) assertTrue(font.canDisplay(glyph));
    }
}
