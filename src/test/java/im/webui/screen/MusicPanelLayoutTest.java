package im.webui.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicPanelLayoutTest {
    @Test
    void largeWindowUsesCenteredWindowedViewport() {
        MusicPanelLayout layout = MusicPanelLayout.calculate(1920, 1080, 1.0D);
        var browser = layout.browserViewport();

        assertEquals(1536, layout.width());
        assertEquals(886, layout.height());
        assertEquals((1920 - layout.width()) / 2, layout.x());
        assertEquals((1080 - layout.height()) / 2, layout.y());
        assertFalse(browser.fullscreen());
        assertTrue(browser.x() > layout.x());
        assertTrue(browser.y() > layout.y());
        assertTrue(browser.width() < layout.width());
        assertTrue(browser.height() < layout.height());
    }

    @Test
    void compactWindowKeepsUsableMargins() {
        MusicPanelLayout layout = MusicPanelLayout.calculate(854, 480, 1.0D);
        var browser = layout.browserViewport();

        assertTrue(layout.x() >= 8);
        assertTrue(layout.y() >= 8);
        assertTrue(browser.width() >= 700);
        assertTrue(browser.height() >= 380);
        assertFalse(browser.fullscreen());
    }

    @Test
    void browserViewportExcludesPanelChrome() {
        MusicPanelLayout layout = MusicPanelLayout.calculate(2560, 1440, 2.0D);
        var browser = layout.browserViewport();

        assertEquals(layout.y() + layout.chromeHeight(), browser.y());
        assertEquals(layout.height() - layout.chromeHeight() - layout.border(), browser.height());
        assertTrue(browser.contains(browser.x(), browser.y()));
        assertFalse(browser.contains(layout.x(), layout.y()));
    }
}
