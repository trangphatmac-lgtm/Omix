package cn.omix.module.impl.player.chest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChestInteractionStateTest {
    @Test
    void stopSprintMustCrossOutgoingTickBoundaryBeforeOpening() {
        ChestInteractionState state = new ChestInteractionState();
        state.sprintChanged(); // STOP is also a state-change packet, even with local sprint=false.
        assertFalse(state.beginUse());
        state.tickEnded();
        assertTrue(state.beginUse());
    }

    @Test
    void anotherModulesBlockPacketDefersChestUse() {
        ChestInteractionState state = new ChestInteractionState();
        state.blockUsed();
        assertFalse(state.beginUse());
        assertFalse(state.suppressOtherUse());
        state.tickEnded();
        assertTrue(state.beginUse());
    }

    @Test
    void chestUseBlocksVanillaFallbackAndOtherModulesUntilScreenCloses() {
        ChestInteractionState state = new ChestInteractionState();
        assertTrue(state.beginUse());
        assertTrue(state.suppressOtherUse());
        state.tickEnded();
        assertTrue(state.awaitingScreen());
        assertTrue(state.suppressOtherUse());
        assertFalse(state.beginUse());
        state.finishUse();
        assertFalse(state.suppressOtherUse());
        assertTrue(state.beginUse());
    }

    @Test
    void failureOrImmediateCloseDoesNotAllowSecondHitInSameTick() {
        ChestInteractionState state = new ChestInteractionState();
        assertTrue(state.beginUse());
        state.finishUse();
        assertFalse(state.awaitingScreen());
        assertTrue(state.suppressOtherUse());
        assertFalse(state.beginUse());
        state.tickEnded();
        assertFalse(state.suppressOtherUse());
        assertTrue(state.beginUse());
    }

    @Test
    void timeoutReleasesWaitingButDoesNotEraseNewSprintChange() {
        ChestInteractionState state = new ChestInteractionState();
        state.beginUse();
        state.tickEnded();
        state.sprintChanged();
        state.finishUse();
        assertFalse(state.canUse());
        state.tickEnded();
        assertTrue(state.canUse());
    }

    @Test
    void disconnectResetsAllPendingState() {
        ChestInteractionState state = new ChestInteractionState();
        state.beginUse();
        state.sprintChanged();
        state.reset();
        assertFalse(state.suppressOtherUse());
        assertFalse(state.awaitingScreen());
        assertTrue(state.beginUse());
    }

    @Test
    void yawPreservesAccumulatedTurnsInBothDirections() {
        assertEquals(730F, ChestInteractionState.nearestYaw(720F, 10F));
        assertEquals(-730F, ChestInteractionState.nearestYaw(-720F, -10F));
        assertEquals(181F, ChestInteractionState.nearestYaw(179F, -179F));
        assertEquals(-181F, ChestInteractionState.nearestYaw(-179F, 179F));
    }

    @Test
    void returningToCameraDoesNotUndoTurnContinuityOnNextPacket() {
        float aimedYaw = ChestInteractionState.nearestYaw(359F, 1F);
        float cameraYaw = ChestInteractionState.nearestYaw(aimedYaw, -1F);
        assertEquals(361F, aimedYaw);
        assertEquals(359F, cameraYaw);
        assertEquals(cameraYaw, ChestInteractionState.nearestYaw(cameraYaw, cameraYaw));
    }
}
