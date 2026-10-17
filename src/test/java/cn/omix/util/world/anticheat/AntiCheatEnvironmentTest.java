package cn.omix.util.world.anticheat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AntiCheatEnvironmentTest {
    @Test void reportsOnlyGoToExactHypixelDomainOrSubdomains() {
        assertTrue(AntiCheatEnvironment.isHypixel("MC.HYPIXEL.NET:25565"));
        assertTrue(AntiCheatEnvironment.isHypixel("hypixel.net"));
        assertFalse(AntiCheatEnvironment.isHypixel("hypixel.net.example.org"));
        assertFalse(AntiCheatEnvironment.isHypixel("nothypixel.net"));
        assertFalse(AntiCheatEnvironment.isHypixel(null));
    }

    @Test void minigameGateRequiresPositiveEvidenceAndExcludesWaiting() {
        assertTrue(AntiCheatEnvironment.inMinigame("bed wars\ndiamond ii in 5:00"));
        assertFalse(AntiCheatEnvironment.inMinigame("bed wars\nwaiting..."));
        assertFalse(AntiCheatEnvironment.inMinigame("lobby\nbeds broken: 30"));
        assertFalse(AntiCheatEnvironment.inMinigame("replay\nnext event: refill"));
        assertFalse(AntiCheatEnvironment.inMinigame(""));
    }
}
