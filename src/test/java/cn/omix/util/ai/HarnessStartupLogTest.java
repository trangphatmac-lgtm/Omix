package cn.omix.util.ai;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HarnessStartupLogTest {
    @Test void preservesTheCauseBeforeALongStackTrace() {
        var log = new HarnessStartupLog();
        log.append("Error: dsh: profile \"omix\" already exists; omit --from-default-profile to use it");
        for (int i = 0; i < 1000; i++) log.append("    at prepareProfile (/very/long/game/directory/profile-boot.js:207:37)");
        assertEquals("dsh: profile \"omix\" already exists; omit --from-default-profile to use it", log.summary());
        assertTrue(log.content().startsWith("Error: dsh: profile"));
        assertTrue(log.content().length() <= 65_536);
    }

    @Test void removesCredentialsAndAnsiBeforePersistingOrDisplaying() {
        var log = new HarnessStartupLog();
        log.append("dsh web: http://127.0.0.1:123/?token=browser-secret");
        log.append("{\"apiKey\":\"model-secret\",\"authorization\":\"Bearer bridge-secret\"}");
        log.append("\u001b[31mError: token=error-secret connection failed\u001b[0m");
        assertFalse(log.content().contains("-secret"));
        assertFalse(log.content().contains("\u001b"));
        assertEquals("token=[redacted] connection failed", log.summary());
    }

    @Test void boundsSummaryEvenWhenOutputIsFull() {
        var log = new HarnessStartupLog();
        assertEquals("Harness exited before its Web server was ready.", log.summary());
        log.append("x".repeat(70_000));
        log.append("TypeError: " + "y".repeat(2000));
        assertEquals(501, log.summary().length());
        assertEquals(65_536, log.content().length());
    }
}
