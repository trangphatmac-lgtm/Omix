package cn.omix.util.world;

import cn.omix.management.rotation.RotationRequest.YawDirection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TellyRotationStateTest {
    private final TellyRotationState state = new TellyRotationState();

    private void nextRound(String mode) {
        state.update(mode, true, false, false, 0);
        state.update(mode, true, false, true, 1);
    }

    private float completeLeg(float yaw, float target, YawDirection expected, boolean returning) {
        for (int tick = 0; tick < 20; tick++) {
            YawDirection direction = returning ? state.returnDirection(yaw, target) : state.direction(yaw, target);
            if (direction == YawDirection.DEFAULT) {
                assertEquals(0.0F, YawDirection.DEFAULT.delta(yaw, target), 0.0001F);
                return yaw;
            }
            assertEquals(expected, direction);
            float delta = direction.delta(yaw, target);
            float step = Math.copySign(Math.min(Math.abs(delta), 70.0F), delta);
            assertTrue(expected == YawDirection.RIGHT ? step >= 0 : step <= 0);
            yaw += step;
        }
        fail("The directed leg must reach its target without extra revolutions");
        return yaw;
    }

    @Test
    void sameKeepsBothPlacementAndReturnInOneDirectionAndAccumulatesFullTurns() {
        for (int sign : new int[]{-1, 1}) {
            state.reset();
            float yaw = 0;
            YawDirection direction = sign < 0 ? YawDirection.LEFT : YawDirection.RIGHT;
            for (int round = 1; round <= 3; round++) {
                state.update("Always Same", true, false, true, 1);
                yaw = completeLeg(yaw, sign * 170, direction, false);
                state.update("Always Same", true, false, false, 0);
                assertTrue(state.shouldReturn(), "Releasing placement must still request the return leg");
                yaw = completeLeg(yaw, 0, direction, true);
                assertEquals(sign * round * 360.0F, yaw);
            }
        }
    }

    @Test
    void changeAlternatesWholeRoundOnlyAfterItsReturnLeg() {
        float yaw = 0;
        for (int round = 0; round < 4; round++) {
            YawDirection direction = round % 2 == 0 ? YawDirection.RIGHT : YawDirection.LEFT;
            state.update("Always Change", true, false, true, 1);
            yaw = completeLeg(yaw, 170, direction, false);
            state.update("Always Change", true, false, false, 0);
            yaw = completeLeg(yaw, 0, direction, true);
            assertEquals(round % 2 == 0 ? 360.0F : 0.0F, yaw);
        }
    }

    @Test
    void onTickTransactionsReturnInTheSameDirectionWithoutConsumingRounds() {
        nextRound("Always Change");
        float yaw = 0;
        for (int placement = 0; placement < 3; placement++) {
            yaw = completeLeg(yaw, 170, YawDirection.RIGHT, false);
            // On tick returns inside each placement transaction, before canRotate changes.
            yaw = completeLeg(yaw, 0, YawDirection.RIGHT, true);
            assertEquals((placement + 1) * 360.0F, yaw);
        }
        nextRound("Always Change");
        yaw = completeLeg(yaw, 170, YawDirection.LEFT, false);
        yaw = completeLeg(yaw, 0, YawDirection.LEFT, true);
        assertEquals(720.0F, yaw);
    }

    @Test
    void defaultClutchAndFreshSessionsDoNotRequestDirectedReturns() {
        state.update("Always Same", true, false, false, 0);
        assertFalse(state.shouldReturn());
        assertEquals(YawDirection.DEFAULT, state.returnDirection(170, 0));
        nextRound("Always Same");
        assertEquals(YawDirection.RIGHT, state.direction(0, 170));
        state.update("Always Same", true, true, false, 2);
        assertFalse(state.isActive());
        assertFalse(state.shouldReturn());
        assertEquals(YawDirection.DEFAULT, state.returnDirection(170, 0));
        state.update("Default", true, false, false, 0);
        assertFalse(state.shouldReturn());
        assertEquals(YawDirection.DEFAULT, state.returnDirection(170, 0));
    }

    @Test
    void returnArrivalAllowsSmallCorrectionsWithoutStartingAnExtraCircle() {
        nextRound("Always Same");
        assertEquals(YawDirection.RIGHT, state.direction(0, 170));
        state.update("Always Same", true, false, false, 0);
        assertEquals(YawDirection.RIGHT, state.returnDirection(170, 0));
        assertEquals(YawDirection.DEFAULT, state.returnDirection(360.1F, 0));
        assertEquals(YawDirection.DEFAULT, state.returnDirection(360, -1));
    }

    @Test
    void sameKeepsTheFirstLeftOrRightDirectionAcrossRounds() {
        for (float firstTarget : new float[]{-170, 170}) {
            state.reset();
            nextRound("Always Same");
            YawDirection first = state.direction(0, firstTarget);
            assertEquals(firstTarget < 0 ? YawDirection.LEFT : YawDirection.RIGHT, first);
            for (int round = 0; round < 3; round++) {
                nextRound("Always Same");
                assertEquals(first, state.direction(0, -firstTarget));
                assertTrue(Math.abs(first.delta(0, -firstTarget)) > 180);
            }
        }
    }

    @Test
    void changeAlternatesOncePerRoundRatherThanOncePerTick() {
        nextRound("Always Change");
        assertEquals(YawDirection.RIGHT, state.direction(0, 170));
        state.update("Always Change", true, false, true, 2);
        assertEquals(YawDirection.RIGHT, state.direction(50, 170));
        nextRound("Always Change");
        assertEquals(YawDirection.LEFT, state.direction(0, 170));
        state.update("Always Change", true, false, true, 2);
        assertEquals(YawDirection.LEFT, state.direction(-80, 170));
        nextRound("Always Change");
        assertEquals(YawDirection.RIGHT, state.direction(0, -170));
    }

    @Test
    void zeroTellyTickStillStartsANewRoundOnLanding() {
        state.update("Always Change", true, false, true, 0);
        assertEquals(YawDirection.RIGHT, state.direction(0, 170));
        state.update("Always Change", true, false, true, 1);
        assertEquals(YawDirection.RIGHT, state.direction(50, 170));
        state.update("Always Change", true, false, true, 0);
        assertEquals(YawDirection.LEFT, state.direction(0, 170));
        state.update("Always Change", true, false, true, 0);
        assertEquals(YawDirection.LEFT, state.direction(-50, 170));
    }

    @Test
    void pausingRotationWithinTheSameJumpDoesNotStartAnotherRound() {
        state.update("Always Change", true, false, true, 1);
        assertEquals(YawDirection.RIGHT, state.direction(0, 170));
        state.update("Always Change", true, false, false, 2);
        assertEquals(YawDirection.DEFAULT, state.direction(50, 170));
        state.update("Always Change", true, false, true, 3);
        assertEquals(YawDirection.RIGHT, state.direction(50, 170));
        nextRound("Always Change");
        assertEquals(YawDirection.LEFT, state.direction(0, 170));
    }

    @Test
    void clutchAlwaysDefaultsAndDoesNotConsumeAnAlternation() {
        nextRound("Always Change");
        assertEquals(YawDirection.RIGHT, state.direction(0, 170));
        state.update("Always Change", true, true, true, 2);
        assertEquals(YawDirection.DEFAULT, state.direction(40, -170));
        state.update("Always Change", true, true, true, 3);
        assertEquals(YawDirection.DEFAULT, state.direction(-170, 30));
        nextRound("Always Change");
        assertEquals(YawDirection.LEFT, state.direction(0, 170));
    }

    @Test
    void defaultNormalModeAndResetDiscardPreviousDirection() {
        nextRound("Always Same");
        assertEquals(YawDirection.RIGHT, state.direction(0, 170));
        state.update("Always Same", false, false, true, 2);
        assertEquals(YawDirection.DEFAULT, state.direction(0, -170));
        nextRound("Always Same");
        assertEquals(YawDirection.LEFT, state.direction(0, -170));
        nextRound("Default");
        assertEquals(YawDirection.DEFAULT, state.direction(0, 170));
        nextRound("Always Same");
        assertEquals(YawDirection.RIGHT, state.direction(0, 170));
        state.reset();
        nextRound("Always Same");
        assertEquals(YawDirection.LEFT, state.direction(0, -170));
    }

    @Test
    void directionChangeRestartsHistoryAndEmptyRoundsDoNotChooseASide() {
        nextRound("Always Same");
        assertEquals(YawDirection.RIGHT, state.direction(0, 170));
        nextRound("Always Change");
        assertEquals(YawDirection.DEFAULT, state.direction(720, 0));
        nextRound("Always Change");
        assertEquals(YawDirection.LEFT, state.direction(0, -170));
    }

    @Test
    void movingTargetAcrossWrapAndSensitivityOvershootDoNotCauseExtraFullTurns() {
        nextRound("Always Same");
        assertEquals(YawDirection.RIGHT, state.direction(0, 170));
        nextRound("Always Same");
        assertEquals(YawDirection.RIGHT, state.direction(0, -170));
        assertEquals(YawDirection.RIGHT, state.direction(100, 179));
        assertEquals(YawDirection.RIGHT, state.direction(175, -178));
        assertEquals(YawDirection.DEFAULT, state.direction(182.1F, -178));
        assertEquals(YawDirection.DEFAULT, state.direction(182, -179));

        state.reset();
        nextRound("Always Same");
        assertEquals(YawDirection.LEFT, state.direction(0, -170));
        nextRound("Always Same");
        assertEquals(YawDirection.LEFT, state.direction(0, 170));
        assertEquals(YawDirection.LEFT, state.direction(-175, 170));
        assertEquals(YawDirection.DEFAULT, state.direction(-190.1F, 170));
    }
}
