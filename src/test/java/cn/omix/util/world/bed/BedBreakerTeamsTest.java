package cn.omix.util.world.bed;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BedBreakerTeamsTest {
    @Test void findsNearestHelmetDyeByRgbDistance() {
        assertEquals(14, BedBreakerTeams.nearestColor(0xB02E26));
        assertEquals(14, BedBreakerTeams.nearestColor(0xB22C28));
        assertEquals(11, BedBreakerTeams.nearestColor(0x3C44AA));
        assertEquals(0, BedBreakerTeams.nearestColor(0xFFFFFF));
    }

    @Test void hypixelAliasesGreenAquaAndGrayButKeepsOtherTeamsDistinct() {
        assertTrue(BedBreakerTeams.sameTeam(5, 13));
        assertTrue(BedBreakerTeams.sameTeam(3, 9));
        assertTrue(BedBreakerTeams.sameTeam(7, 8));
        assertFalse(BedBreakerTeams.sameTeam(14, 11));
        assertFalse(BedBreakerTeams.sameTeam(3, 11));
    }

    @Test void missingHelmetOrNonBedDoesNotProtectTargets() {
        assertFalse(BedBreakerTeams.sameTeam(14, -1));
        assertFalse(BedBreakerTeams.sameTeam(-1, 14));
        assertFalse(BedBreakerTeams.sameTeam(-1, -1));
    }
}
