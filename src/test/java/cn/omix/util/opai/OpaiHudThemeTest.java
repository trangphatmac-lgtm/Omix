package cn.omix.util.opai;

import cn.omix.util.opai.clickgui.OpaiStyle;
import java.awt.Color;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OpaiHudThemeTest {
    @Test void defaultRetainsTheOriginalPalette() {
        assertEquals(OpaiStyle.LAVENDER, OpaiHudTheme.palette(OpaiHudTheme.DEFAULT_COLOR));
    }

    @Test void customAccentAlsoRecolorsProgressAndToggleKnobs() {
        var green = OpaiHudTheme.palette(0x33CC66);
        assertEquals(0xFF33CC66, green.accent());
        assertTrue((green.hudProgress() >> 8 & 255) > (green.hudProgress() >> 16 & 255));
        assertTrue((green.hudKnob() >> 8 & 255) > (green.hudKnob() >> 16 & 255));
        assertNotEquals(OpaiStyle.LAVENDER.hudProgress(), green.hudProgress());
        assertNotEquals(OpaiStyle.LAVENDER.hudKnob(), green.hudKnob());
        assertEquals(OpaiStyle.LAVENDER.body(), green.body());
        assertEquals(OpaiStyle.LAVENDER.text(), green.text());
        assertEquals(0xFFBBC3FF, OpaiStyle.LAVENDER.accent());
    }

    @Test void neutralAndDarkChoicesRemainFiniteNeutralAndOpaque() {
        for (int color : new int[]{Color.WHITE.getRGB(), Color.GRAY.getRGB(), Color.BLACK.getRGB()}) {
            var palette = OpaiHudTheme.palette(color);
            for (int shade : new int[]{palette.accent(), palette.hudProgress(), palette.hudKnob()}) {
                assertEquals(255, shade >>> 24);
                assertEquals(shade & 255, shade >> 8 & 255);
                assertEquals(shade & 255, shade >> 16 & 255);
            }
        }
    }
}
