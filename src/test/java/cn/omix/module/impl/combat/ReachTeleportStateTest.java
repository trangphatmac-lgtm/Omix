package cn.omix.module.impl.combat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReachTeleportStateTest {
    private final ReachTeleportState<String> state = new ReachTeleportState<>();
    private final Object connection = new Object();
    private final Object world = new Object();
    private final Object player = new Object();

    @Test
    void recoveryWaitsForFreshCorrectionInsteadOfReplayingOldMovement() {
        List<String> wire = new ArrayList<>();
        receiveCorrection(wire, 41, "old position", true);
        assertEquals(List.of("move:old position"), wire);
        wire.add("tick-end");

        state.beginRecovery(connection, world, player);
        state.beginRecovery(connection, world, player);
        assertEquals(List.of("move:old position", "tick-end"), wire,
                "Disabling must not fabricate an acknowledgement, movement, or tick-end");
        assertEquals(new ReachTeleportState.Pending<>(41, "old position"), state.peek(connection, world, player));

        receiveCorrection(wire, 42, "new position", false);
        assertEquals(List.of("move:old position", "tick-end", "confirm:42", "move:new position"), wire);
        assertNull(state.peek(connection, world, player));
    }

    @Test
    void rapidReenableStillAcceptsOneFreshCorrectionBeforeSuppressingAgain() {
        assertTrue(state.onCorrection(connection, world, player, 1, "old", true));
        state.beginRecovery(connection, world, player);
        assertFalse(state.onCorrection(connection, world, player, 2, "fresh", true));
        assertNull(state.peek(connection, world, player));
        assertTrue(state.onCorrection(connection, world, player, 3, "next", true));
    }

    @Test
    void normalModeAcceptsFreshCorrectionEvenBeforeNextUpdate() {
        assertTrue(state.onCorrection(connection, world, player, 1, "old", true));
        assertFalse(state.onCorrection(connection, world, player, 2, "fresh", false));
        assertNull(state.peek(connection, world, player));
        assertFalse(state.onCorrection(connection, world, player, 3, "normal", false));
    }

    @Test
    void tracksLatestPositionEvenWhenTeleportIdsWrapOrRepeat() {
        state.onCorrection(connection, world, player, Integer.MAX_VALUE, "old", true);
        state.onCorrection(connection, world, player, 0, "latest", true);
        assertEquals(new ReachTeleportState.Pending<>(0, "latest"), state.peek(connection, world, player));
        state.beginRecovery(connection, world, player);
        assertFalse(state.onCorrection(connection, world, player, 0, "reissued", true),
                "Freshness comes from receipt, not ordering or uniqueness of the teleport ID");
    }

    @Test
    void reconnectWorldChangeAndRespawnDiscardOldRecovery() {
        Object[][] contexts = {{new Object(), world, player}, {connection, new Object(), player},
                {connection, world, new Object()}, {null, world, player}, {connection, null, player},
                {connection, world, null}};
        for (Object[] context : contexts) {
            state.onCorrection(connection, world, player, 42, "old session", true);
            state.beginRecovery(connection, world, player);
            assertNull(state.peek(context[0], context[1], context[2]));
            assertNull(state.peek(connection, world, player));
            assertTrue(state.onCorrection(connection, world, player, 43, "new session", true),
                    "Old recovery must not force acceptance in a different session");
        }
    }

    @Test
    void renderingCannotConsumeRecoveryAndWorldResetClearsIt() {
        state.onCorrection(connection, world, player, 7, "server position", true);
        state.beginRecovery(connection, world, player);
        var expected = new ReachTeleportState.Pending<>(7, "server position");
        assertEquals(expected, state.peek(connection, world, player));
        assertEquals(expected, state.peek(connection, world, player));
        assertFalse(state.onCorrection(connection, world, player, 8, "fresh", true));
        state.onCorrection(connection, world, player, 9, "next", true);
        state.beginRecovery(connection, world, player);
        state.clear();
        assertNull(state.peek(connection, world, player));
        assertTrue(state.onCorrection(connection, world, player, 10, "new world", true));
    }

    private void receiveCorrection(List<String> wire, int id, String position, boolean grimEnabled) {
        if (!state.onCorrection(connection, world, player, id, position, grimEnabled)) {
            wire.add("confirm:" + id);
        }
        // Vanilla always sends the response belonging to this newly received correction.
        wire.add("move:" + position);
    }
}
