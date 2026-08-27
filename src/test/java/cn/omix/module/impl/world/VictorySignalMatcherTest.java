package cn.omix.module.impl.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VictorySignalMatcherTest {
    @Test
    void titleSignalsAreCaseInsensitiveAndSupportChinese() {
        assertTrue(VictorySignalMatcher.matchesTitle("VICTORY!"));
        assertTrue(VictorySignalMatcher.matchesTitle("恭喜你获得胜利"));
        assertTrue(VictorySignalMatcher.matchesTitle("Congratulations"));
        assertFalse(VictorySignalMatcher.matchesTitle("Defeat"));
    }

    @Test
    void chatSignalsMatchSupportedWinPhrases() {
        assertTrue(VictorySignalMatcher.matchesChat("Congratulations, player!"));
        assertTrue(VictorySignalMatcher.matchesChat("You   Won the game"));
        assertTrue(VictorySignalMatcher.matchesChat("YOU WIN!"));
        assertTrue(VictorySignalMatcher.matchesChat("You got 1st place"));
        assertTrue(VictorySignalMatcher.matchesChat("恭喜，你赢了"));
        assertFalse(VictorySignalMatcher.matchesChat("You got 2nd place"));
    }
}
