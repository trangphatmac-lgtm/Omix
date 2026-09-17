package cn.omix.util.webui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebPanelLayoutTest {
    @Test
    void largeWindowUsesCenteredWindowedViewport() {
        WebPanelLayout layout = WebPanelLayout.calculate(1920, 1080, 1.0D);
        var browser = layout.browserViewport();

        assertEquals(1536, layout.width());
        assertEquals(886, layout.height());
        assertEquals((1920 - layout.width()) / 2, layout.x());
        assertEquals((1080 - layout.height()) / 2, layout.y());
        assertFalse(browser.fullscreen());
        assertEquals(layout.x(), browser.x());
        assertEquals(layout.y(), browser.y());
        assertEquals(layout.width(), browser.width());
        assertEquals(layout.height(), browser.height());
        assertEquals(18, layout.cornerRadius());
        assertEquals(layout.cornerRadius(), browser.cornerRadius());
    }

    @Test
    void compactWindowKeepsUsableMargins() {
        WebPanelLayout layout = WebPanelLayout.calculate(854, 480, 1.0D);
        var browser = layout.browserViewport();

        assertTrue(layout.x() >= 8);
        assertTrue(layout.y() >= 8);
        assertTrue(browser.width() >= 700);
        assertTrue(browser.height() >= 380);
        assertFalse(browser.fullscreen());
    }

    @Test
    void browserViewportFillsTheWholePanel() {
        WebPanelLayout layout = WebPanelLayout.calculate(2560, 1440, 2.0D);
        var browser = layout.browserViewport();

        assertEquals(layout.x(), browser.x());
        assertEquals(layout.y(), browser.y());
        assertEquals(layout.width(), browser.width());
        assertEquals(layout.height(), browser.height());
        assertTrue(browser.contains(
                browser.x() + browser.cornerRadius(),
                browser.y() + browser.cornerRadius()
        ));
        assertFalse(browser.contains(layout.x() - 1, layout.y()));
    }

    @Test
    void retinaWindowDoesNotUseCompactLayoutBecauseOfMinecraftGuiScale() {
        WebPanelLayout layout = WebPanelLayout.calculate(3840, 2054, 4.0D);

        assertEquals(3072, layout.width());
        assertEquals(1684, layout.height());
        assertEquals(384, layout.x());
        assertEquals(185, layout.y());
        assertEquals(72, layout.cornerRadius());
    }

    @Test
    void roundedViewportDoesNotAcceptInputInClippedCorners() {
        WebPanelLayout layout = WebPanelLayout.calculate(1920, 1080, 1.0D);
        var browser = layout.browserViewport();

        assertFalse(browser.contains(browser.x(), browser.y()));
        assertTrue(browser.contains(
                browser.x() + browser.cornerRadius(),
                browser.y() + browser.cornerRadius()
        ));
    }
}
