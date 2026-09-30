package cn.omix.util.skeet;

import org.junit.jupiter.api.Test;

import static cn.omix.util.skeet.SkeetLayout.*;
import static org.junit.jupiter.api.Assertions.*;

class SkeetLayoutTest {
    @Test void mouseAndDrawingAgreeAcrossGuiScaleRetinaAndSmallWindows() {
        for (int[] window : new int[][]{{1920, 1080, 1}, {1279, 719, 2}, {640, 360, 2}, {320, 240, 1}}) {
            for (int guiScale : new int[]{1, 2, 3, 4, 6}) {
                int fw = window[0] * window[2], fh = window[1] * window[2];
                int sw = (int) Math.ceil(fw / (double) guiScale), sh = (int) Math.ceil(fh / (double) guiScale);
                var v = viewport(window[0], window[1], fw, fh, sw, sh, guiScale);
                assertTrue(v.width() >= WIDTH + 15.99f);
                assertTrue(v.height() >= HEIGHT + 15.99f);
                for (float fraction : new float[]{0, .123f, .5f, 1}) {
                    float x = v.mouseX(sw * fraction), y = v.mouseY(sh * fraction);
                    assertEquals(fw * fraction, x * v.scaleX() * guiScale, .002);
                    assertEquals(fh * fraction, y * v.scaleY() * guiScale, .002);
                }
                assertTrue(v.fontDensity() >= v.scaleX() * guiScale - .001f);
                assertTrue(v.fontDensity() >= v.scaleY() * guiScale - .001f);
            }
        }
        var minimized = viewport(0, 0, 0, 0, 0, 0, 0);
        assertTrue(Float.isFinite(minimized.scaleX()));
        assertTrue(Float.isFinite(minimized.mouseX(0)));
    }

    @Test void popupFitsAtEveryCornerAndLongListsStayInsideTheWindow() {
        Rect clip = new Rect(53, 8, 320, 330);
        for (Rect anchor : new Rect[]{new Rect(60, 10, 120, 13), new Rect(260, 319, 120, 13)}) {
            Rect popup = popup(anchor, 140, 1000, clip);
            assertTrue(popup.x() >= clip.x());
            assertTrue(popup.y() >= clip.y());
            assertTrue(popup.x() + popup.width() <= clip.x() + clip.width());
            assertTrue(popup.y() + popup.height() <= clip.y() + clip.height());
            assertEquals(330, popup.height());
        }
        Rect bottom = new Rect(100, 310, 120, 13);
        assertEquals(130, popup(bottom, 120, 180, clip).y());
    }

    @Test void slidersRespectFractionalMinimumStepsAndBothEndpoints() {
        assertEquals(.35f, sliderValue(.5f, .05f, .65f, .1f), .00001f);
        assertEquals(-.25f, sliderValue(.5f, -.75f, .25f, .1f), .00001f);
        assertEquals(.95f, sliderValue(1.5f, .05f, .95f, .2f), .00001f);
        assertEquals(.05f, sliderValue(-1, .05f, .95f, .2f));
        assertEquals(3, sliderValue(.7f, 3, 3, .1f));
        assertEquals(.123f, sliderValue(.123f, 0, 1, 0), .00001f);
    }

    @Test void shrinkingConditionalContentClampsScrollingAndEdgesDoNotDoubleHit() {
        assertEquals(190, scroll(800, 500, 310));
        assertEquals(0, scroll(190, 100, 310));
        Rect a = new Rect(0, 0, 100, 15), b = new Rect(0, 15, 100, 15);
        assertFalse(a.contains(1, 15));
        assertTrue(b.contains(1, 15));
        assertFalse(a.intersects(b));
    }
}
