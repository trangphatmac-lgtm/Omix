package cn.omix.util.setsuna;

import org.junit.jupiter.api.Test;

import static cn.omix.util.setsuna.SetsunaLayout.*;
import static org.junit.jupiter.api.Assertions.*;

class SetsunaLayoutTest {
    @Test void panelsAndRingFitAcrossSmallWindowsAndGuiScales() {
        for (int[] framebuffer : new int[][]{{320, 240}, {640, 360}, {1279, 719}, {2558, 1438}, {3840, 2160}}) {
            for (int guiScale : new int[]{1, 2, 3, 4, 6}) for (float scale : new float[]{.65f, 1, 1.25f}) {
                int sw = (int) Math.ceil(framebuffer[0] / (float) guiScale), sh = (int) Math.ceil(framebuffer[1] / (float) guiScale);
                var v = viewport(sw, sh, framebuffer[0], framebuffer[1], guiScale, scale);
                assertTrue(v.width() >= 319.99f); assertTrue(v.height() >= 239.99f);
                for (float progress : new float[]{0, .2f, .5f, 1}) {
                    var p = panels(v.width(), v.height(), progress);
                    for (Rect r : new Rect[]{p.modules(), p.settings()}) {
                        assertTrue(r.x() >= 0); assertTrue(r.y() >= 0);
                        assertTrue(r.right() <= v.width()); assertTrue(r.bottom() <= v.height());
                        assertTrue(r.body().height() > 0);
                    }
                }
                var expanded = panels(v.width(), v.height(), 1);
                assertEquals(GAP, expanded.settings().x() - expanded.modules().right(), .0001f);
                for (int i = 0; i < 6; i++) {
                    Point p = bubble(i, 6, v.width(), v.height());
                    assertTrue(p.x() - BUBBLE_RADIUS - 4 >= 0); assertTrue(p.x() + BUBBLE_RADIUS + 4 <= v.width());
                    assertTrue(p.y() - BUBBLE_RADIUS - 4 >= 0); assertTrue(p.y() + BUBBLE_RADIUS + 4 <= v.height());
                }
                for (float t : new float[]{0, .125f, .5f, 1}) {
                    assertEquals(framebuffer[0] * t, v.mouse(sw * t) * v.renderX() * guiScale, .002f);
                    assertEquals(framebuffer[1] * t, v.mouse(sh * t) * v.renderY() * guiScale, .002f);
                }
                assertTrue(v.density() >= Math.max(v.renderX(), v.renderY()) * guiScale - .001f);
            }
        }
    }
    @Test void minimizedWindowStillHasFiniteCoordinates() {
        var v = viewport(0, 0, 0, 0, 0, 1);
        assertTrue(Float.isFinite(v.width())); assertTrue(Float.isFinite(v.height()));
        assertTrue(Float.isFinite(v.renderX())); assertTrue(Float.isFinite(v.renderY()));
        assertTrue(Float.isFinite(v.mouse(0)));
    }
    @Test void collapseAndExpansionHitExactEndpointsAndScrollClamps() {
        var point = new Point(100, 120); var target = new Rect(20, 30, 196, 322);
        assertEquals(new Rect(73, 93, 54, 54), expand(point, target, 0));
        assertEquals(target, expand(point, target, 1));
        assertEquals(190, scroll(800, 500, 310));
        assertEquals(0, scroll(190, 100, 310));
        assertFalse(new Rect(0, 0, 100, 25).contains(10, 25));
    }
}
