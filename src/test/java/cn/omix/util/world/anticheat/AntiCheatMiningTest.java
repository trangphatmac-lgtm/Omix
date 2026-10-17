package cn.omix.util.world.anticheat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AntiCheatMiningTest {
    private final AntiCheatMining<String> mining = new AntiCheatMining<>();

    @Test void completedAnimationIsConsumedByPositionExactlyOnce() {
        mining.animation(1, "stone", 6, 0);
        mining.animation(1, "stone", 255, 100);
        var result = mining.consume("stone", 150);
        assertEquals(1, result.entityId());
        assertEquals(6, result.progress());
        assertNull(mining.consume("stone", 151));
    }

    @Test void modernNegativeResetAlsoEndsAnimation() {
        mining.animation(1, "stone", 7, 0);
        mining.animation(1, "stone", -1, 50);
        assertEquals(7, mining.consume("stone", 100).progress());
    }

    @Test void switchingBlocksDoesNotAttributeOldProgressToNewBlock() {
        mining.animation(1, "stone", 6, 0);
        mining.animation(1, "dirt", 255, 100);
        assertNull(mining.consume("stone", 150));
        assertNull(mining.consume("dirt", 150));
    }

    @Test void staleOrRestartedBreakIsNotEvidence() {
        mining.animation(1, "stone", 6, 0);
        mining.animation(1, "stone", 255, 2001);
        assertNull(mining.consume("stone", 2002));
        mining.animation(1, "stone", 6, 3000);
        mining.animation(1, "stone", 255, 3100);
        assertNull(mining.consume("stone", 4101));
        mining.animation(1, "stone", 6, 5000);
        mining.animation(1, "stone", 255, 5100);
        mining.animation(2, "stone", 1, 5150);
        assertNull(mining.consume("stone", 5200));
    }

    @Test void worldResetDropsAllEvidence() {
        mining.animation(1, "stone", 6, 0);
        mining.animation(1, "stone", 255, 100);
        mining.clear();
        assertNull(mining.consume("stone", 101));
    }
}
