package im.webui.theme;

import im.webui.screen.WebScreenType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebThemeTest {
    @Test
    void routesMusicToItsIndependentApplication() {
        WebTheme theme = new WebTheme(
                "test",
                "http://127.0.0.1:1234/?omix_code=secret",
                false
        );

        assertEquals(
                "http://127.0.0.1:1234/music/index.html?omix_code=secret#/",
                theme.screenUrl(WebScreenType.MUSIC)
        );
        assertEquals(
                "http://127.0.0.1:1234/?omix_code=secret#/ai",
                theme.screenUrl(WebScreenType.AI)
        );
    }
}
