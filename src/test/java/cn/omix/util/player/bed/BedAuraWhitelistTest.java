package cn.omix.util.player.bed;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BedAuraWhitelistTest {
    private static final String START = "Protect your bed and destroy the enemy beds.";
    private static final Vec3d HOME = new Vec3d(100, 64, -100);
    private static final Vec3d ENEMY = new Vec3d(-100, 64, 100);

    @Test void requiresBothTheStartMessageAndItsFollowingTeleport() {
        var whitelist = new BedAuraWhitelist();
        whitelist.onPositionApplied(HOME);
        assertFalse(whitelist.isProtected(HOME));
        whitelist.onGameMessage(null);
        whitelist.onGameMessage("You will respawn in 5 seconds!");
        whitelist.onPositionApplied(HOME);
        assertFalse(whitelist.isProtected(HOME));
        whitelist.onGameMessage("BED WARS\n" + START + "\nGood luck!");
        assertFalse(whitelist.isProtected(HOME));
        whitelist.onPositionApplied(HOME);
        assertTrue(whitelist.isProtected(HOME));
        assertFalse(whitelist.isProtected(ENEMY));
    }

    @Test void preservesTheStrictThreeDimensionalPlayerDistanceThreshold() {
        var whitelist = new BedAuraWhitelist();
        whitelist.onGameMessage(START);
        whitelist.onPositionApplied(HOME);
        assertTrue(whitelist.isProtected(HOME.add(9.99, 10, 20)));
        assertFalse(whitelist.isProtected(HOME.add(10, 10, 20)), "Squared distance exactly 600 is outside");
        assertFalse(whitelist.isProtected(HOME.add(10.01, 10, 20)));
        assertFalse(whitelist.isProtected(HOME.add(0, 25, 0)), "Height is part of the source's distance check");
        assertFalse(whitelist.isProtected(null));
    }

    @Test void laterCorrectionsAndRespawnTeleportsDoNotMoveHome() {
        var whitelist = new BedAuraWhitelist();
        whitelist.onGameMessage(START);
        whitelist.onPositionApplied(HOME);
        whitelist.onPositionApplied(ENEMY);
        whitelist.onPositionApplied(ENEMY.add(5, 0, 5));
        assertTrue(whitelist.isProtected(HOME));
        assertFalse(whitelist.isProtected(ENEMY));
    }

    @Test void nextMatchCanReplaceTheSpawnWithoutAWorldReload() {
        var whitelist = new BedAuraWhitelist();
        whitelist.onGameMessage(START);
        whitelist.onPositionApplied(HOME);
        whitelist.onGameMessage(START);
        assertTrue(whitelist.isProtected(HOME), "Keep the old protection until the new spawn arrives, as in Samsara");
        whitelist.onPositionApplied(ENEMY);
        assertFalse(whitelist.isProtected(HOME));
        assertTrue(whitelist.isProtected(ENEMY));
    }

    @Test void worldChangeOrDisconnectClearsBothRecordedAndPendingSpawns() {
        var whitelist = new BedAuraWhitelist();
        whitelist.onGameMessage(START);
        whitelist.onPositionApplied(HOME);
        whitelist.onGameMessage(START);
        whitelist.clear();
        assertFalse(whitelist.isProtected(HOME));
        whitelist.onPositionApplied(ENEMY);
        assertFalse(whitelist.isProtected(ENEMY), "An old pending message cannot capture a different world's first teleport");
        whitelist.onGameMessage(START);
        whitelist.onPositionApplied(ENEMY);
        assertTrue(whitelist.isProtected(ENEMY));
    }

    @Test void onlyAnAppliedPositionConsumesThePendingStart() {
        var whitelist = new BedAuraWhitelist();
        whitelist.onGameMessage(START);
        whitelist.onPositionApplied(null);
        whitelist.onPositionApplied(HOME);
        assertTrue(whitelist.isProtected(HOME));
    }
}
