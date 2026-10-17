package cn.omix.util.world.anticheat;

import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.UUID;
import static cn.omix.util.world.anticheat.AntiCheatCheck.*;
import static org.junit.jupiter.api.Assertions.*;

class AntiCheatAlertsTest {
    @Test void cooldownIsPerPlayerAndPerCheck() {
        var alerts = new AntiCheatAlerts();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        assertTrue(alerts.allow(first, NO_SLOW, 0, 1000));
        assertFalse(alerts.allow(first, NO_SLOW, 1000, 1000));
        assertTrue(alerts.allow(first, NO_SLOW, 1001, 1000));
        assertTrue(alerts.allow(first, SCAFFOLD, 1001, 1000));
        assertTrue(alerts.allow(second, NO_SLOW, 1001, 1000));
        assertTrue(alerts.allow(first, NO_SLOW, 1002, 0));
    }

    @Test void leavingAndResetReleaseCooldowns() {
        var alerts = new AntiCheatAlerts();
        UUID player = UUID.randomUUID();
        alerts.allow(player, NO_SLOW, 0, 1000);
        alerts.retain(Set.of());
        assertTrue(alerts.allow(player, NO_SLOW, 1, 1000));
        alerts.clear();
        assertTrue(alerts.allow(player, NO_SLOW, 2, 1000));
    }
}
