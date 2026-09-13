package cn.omix.util.misc;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class KeyUtilTest {
    @Test
    void middleAndSideButtonsTrackPhysicalHoldAndReleaseWithoutKeyboardEvents() {
        for (int button : new int[]{2, 3, 4, 7}) {
            AtomicBoolean held = new AtomicBoolean(true);
            int encoded = KeyUtil.mouseKeyCode(button);
            assertTrue(KeyUtil.isPressed(encoded, key -> fail("Must not poll keyboard"),
                    polled -> polled == button && held.get()));
            held.set(false);
            assertFalse(KeyUtil.isPressed(encoded, key -> fail("Must not poll keyboard"),
                    polled -> polled == button && held.get()));
        }
    }

    @Test
    void oldKeyboardAndUnboundValuesKeepTheirMeaning() {
        assertTrue(KeyUtil.isPressed(65, key -> key == 65, button -> fail("Must not poll mouse")));
        for (int key : new int[]{0, -1, -92, -101, 349, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertFalse(KeyUtil.isPressed(key, code -> fail("Invalid keyboard code"),
                    button -> fail("Invalid mouse code")));
        }
    }

    @Test
    void mouseLabelsWorkWithoutGlfwKeyboardNameLookup() {
        assertEquals("MOUSE MIDDLE", KeyUtil.getKeyName(-98));
        assertEquals("MOUSE 4", KeyUtil.getKeyName(-97));
        assertEquals("MOUSE 5", KeyUtil.getKeyName(-96));
        assertEquals("None", KeyUtil.getKeyName(-1));
        assertEquals("None", KeyUtil.getKeyName(0));
    }
}
