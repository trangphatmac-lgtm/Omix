package cn.omix.util.skeet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkeetTextBufferTest {
    @Test void editingNeverSplitsSupplementaryCharacters() {
        var buffer = new SkeetTextBuffer(32);
        buffer.set("a😀中");
        buffer.move(-1, false);
        buffer.erase(true);
        assertEquals("a中", buffer.value());
        buffer.replace("🚀");
        buffer.move(-1, false);
        buffer.erase(false);
        assertEquals("a中", buffer.value());
        assertEquals(1, buffer.cursor());
    }

    @Test void pasteLimitsAndSelectionReplacementPreserveUnicode() {
        var buffer = new SkeetTextBuffer(4);
        buffer.replace("abc😀");
        assertEquals("abc", buffer.value());
        buffer.selectAll();
        buffer.replace("😀中x");
        assertEquals("😀中x", buffer.value());
        buffer.home(false);
        buffer.move(1, true);
        assertEquals("😀", buffer.selection());
        buffer.replace("a");
        assertEquals("a中x", buffer.value());
    }

    @Test void externallyLoadedLongTextIsNotTruncatedWithoutAnEdit() {
        var buffer = new SkeetTextBuffer(3);
        buffer.set("existing longer value");
        assertEquals("existing longer value", buffer.value());
        buffer.selectAll();
        buffer.replace("ab\n\t§c");
        assertEquals("abc", buffer.value());
        buffer.selectAll();
        buffer.move(-1, false);
        assertEquals(0, buffer.cursor());
        assertEquals("", buffer.selection());
    }
}
