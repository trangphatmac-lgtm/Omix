package cn.omix.util.player.velocity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GrimFullStateTest {
    private final GrimFullState state = new GrimFullState();
    private final Object player = new Object();

    @Test
    void waitsFull180TicksEvenWhenEnabledWithAnOldPlayer() {
        assertTrue(state.update(player, 1000, 0, false));
        assertTrue(state.update(player, 1179, 8_950_000_000L, false));
        assertFalse(state.update(player, 1180, 9_000_000_000L, false));
    }

    @Test
    void setbackImmediatelyReopensPingAndRestartsTheFullWait() {
        state.update(player, 0, 0, false);
        assertFalse(state.update(player, 200, 10_000_000_000L, false));
        assertTrue(state.update(player, 201, 10_050_000_000L, true));
        assertTrue(state.update(player, 201, 10_050_000_001L, false));
        assertTrue(state.update(player, 380, 19_000_000_000L, false));
        assertFalse(state.update(player, 381, 19_050_000_000L, false));
    }

    @Test
    void repeatedSetbacksRestartAnExistingWait() {
        state.update(player, 0, 0, false);
        state.update(player, 100, 5_000_000_000L, true);
        assertTrue(state.update(player, 200, 10_000_000_000L, true));
        assertTrue(state.update(player, 379, 19_000_000_000L, false));
        assertFalse(state.update(player, 380, 19_050_000_000L, false));
    }

    @Test
    void refreshesAt30SecondsAndAgainAt60Seconds() {
        state.update(player, 0, 0, false);
        assertFalse(state.update(player, 599, 29_999_999_999L, false));
        assertTrue(state.update(player, 600, 30_000_000_000L, false));
        assertTrue(state.update(player, 779, 38_950_000_000L, false));
        assertFalse(state.update(player, 780, 39_000_000_000L, false));
        assertFalse(state.update(player, 1199, 59_999_999_999L, false));
        assertTrue(state.update(player, 1200, 60_000_000_000L, false));
    }

    @Test
    void setbackDoesNotPostponePeriodicRefresh() {
        state.update(player, 0, 0, false);
        state.update(player, 580, 29_000_000_000L, true);
        assertTrue(state.update(player, 600, 30_000_000_000L, false));
        assertTrue(state.update(player, 760, 38_000_000_000L, false));
        assertFalse(state.update(player, 780, 39_000_000_000L, false));
    }

    @Test
    void realTimeRefreshAndTickWaitUseIndependentClocks() {
        state.update(player, 0, 0, false);
        assertFalse(state.update(player, 1000, 1_000_000_000L, false));
        assertTrue(state.update(player, 1001, 30_000_000_000L, false));
        assertTrue(state.update(player, 1001, 50_000_000_000L, false));
        assertFalse(state.update(player, 1181, 51_000_000_000L, false));
    }

    @Test
    void lifecycleResetAndPlayerReplacementDiscardOldTimers() {
        state.update(player, 1000, 0, false);
        assertFalse(state.update(player, 1180, 9_000_000_000L, false));
        state.reset();
        assertTrue(state.update(player, 1180, 10_000_000_000L, false));
        assertFalse(state.update(player, 1360, 19_000_000_000L, false));
        Object replacement = new Object();
        assertTrue(state.update(replacement, 1360, 20_000_000_000L, false));
        assertFalse(state.update(replacement, 1540, 29_000_000_000L, false));
        assertTrue(state.update(replacement, 0, 30_000_000_000L, false));
        assertFalse(state.update(replacement, 180, 39_000_000_000L, false));
    }

    @Test
    void ordinaryWaitingStillHonorsVelocityOption() {
        assertTrue(state.decide(player, 0, 0, false, false, false).cancelVelocity());
        assertFalse(state.decide(player, 1, 50_000_000L, false, false, true).cancelVelocity());
        assertTrue(state.decide(player, 180, 9_000_000_000L, false, false, true).cancelVelocity());
        assertTrue(state.decide(player, 200, 10_000_000_000L, true, false, false).cancelVelocity());
        assertFalse(state.decide(player, 201, 10_050_000_000L, false, false, false).cancelPing());
    }

    @Test
    void exemptionDoesNotRestartInitialWaitOrPersistForLaterVelocity() {
        state.decide(player, 0, 0, false, false, false);
        var exempt = state.decide(player, 179, 8_950_000_000L, false, true, false);
        assertFalse(exempt.cancelPing());
        assertFalse(exempt.cancelVelocity());
        var next = state.decide(player, 180, 9_000_000_000L, false, false, false);
        assertTrue(next.cancelPing());
        assertTrue(next.cancelVelocity());
    }

    @Test
    void repeatedExemptionsDoNotShift30SecondRefreshOrItsWait() {
        state.decide(player, 0, 0, false, false, false);
        assertTrue(state.decide(player, 200, 10_000_000_000L, false, true, false).cancelPing());
        assertTrue(state.decide(player, 599, 29_950_000_000L, false, true, false).cancelPing());
        var refresh = state.decide(player, 600, 30_000_000_000L, false, false, false);
        assertFalse(refresh.cancelPing());
        assertTrue(refresh.cancelVelocity());
        state.decide(player, 779, 38_950_000_000L, false, true, false);
        assertTrue(state.decide(player, 780, 39_000_000_000L, false, false, false).cancelPing());
    }

    @Test
    void exemptionDuringSetbackWaitDoesNotExtendIt() {
        state.decide(player, 0, 0, false, false, false);
        state.decide(player, 200, 10_000_000_000L, true, false, false);
        assertFalse(state.decide(player, 379, 18_950_000_000L, false, true, false).cancelPing());
        assertTrue(state.decide(player, 380, 19_000_000_000L, false, false, false).cancelPing());
    }
}
