package cn.omix.module.impl.player.phase;

import org.junit.jupiter.api.Test;

import static cn.omix.module.impl.player.phase.HeypixelPhaseState.Update.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HeypixelPhaseStateTest {
    @Test
    void waitsForSubtitleAndForAgeStrictlyGreaterThanTen() {
        HeypixelPhaseState state = new HeypixelPhaseState();
        assertEquals(WAIT, state.onUpdate(100));
        state.onSubtitle("请稍等片刻，即将传送");
        assertEquals(WAIT, state.onUpdate(0));
        assertEquals(WAIT, state.onUpdate(10));
        assertEquals(PHASE, state.onUpdate(11));
    }

    @Test
    void movementTickContinuesAndOnlySubsequentTicksFreeze() {
        HeypixelPhaseState state = new HeypixelPhaseState();
        state.onSubtitle("稍等片刻");
        assertEquals(PHASE, state.onUpdate(11));
        assertEquals(FREEZE, state.onUpdate(12));
        assertEquals(FREEZE, state.onUpdate(12));
        state.onSubtitle("稍等片刻");
        assertEquals(FREEZE, state.onUpdate(12));
    }

    @Test
    void startTitleClearsBothFrozenAndPendingState() {
        HeypixelPhaseState state = new HeypixelPhaseState();
        state.onSubtitle("稍等片刻");
        assertEquals(PHASE, state.onUpdate(11));
        state.onSubtitle("稍等片刻");
        state.onTitle("游戏开始！");
        assertEquals(WAIT, state.onUpdate(12));
        state.onSubtitle("稍等片刻");
        assertEquals(PHASE, state.onUpdate(13));
    }

    @Test
    void startBeforeEligibleTickPreventsMovement() {
        HeypixelPhaseState state = new HeypixelPhaseState();
        state.onSubtitle("稍等片刻");
        assertEquals(WAIT, state.onUpdate(10));
        state.onTitle("开始");
        assertEquals(WAIT, state.onUpdate(11));
    }

    @Test
    void matchesTextOnlyInTheCorrespondingPacketType() {
        HeypixelPhaseState state = new HeypixelPhaseState();
        state.onTitle("稍等片刻");
        assertEquals(WAIT, state.onUpdate(11));
        state.onSubtitle("稍等片刻");
        assertEquals(PHASE, state.onUpdate(11));
        state.onSubtitle("开始");
        state.onTitle("等待玩家");
        assertEquals(FREEZE, state.onUpdate(12));
    }
}
