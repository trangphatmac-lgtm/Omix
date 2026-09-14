package cn.omix.module.impl.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReachTeleportStateTest {
    private final ReachTeleportState<String> state = new ReachTeleportState<>();
    private final Object connection = new Object();
    private final Object world = new Object();
    private final Object player = new Object();

    @Test
    void recoveryUsesLatestCorrectionOnceEvenWhenTeleportIdsWrap() {
        state.remember(connection, world, player, Integer.MAX_VALUE, "old position");
        state.remember(connection, world, player, 0, "latest position");
        assertEquals(new ReachTeleportState.Pending<>(0, "latest position"), state.peek(connection, world, player));
        assertEquals(new ReachTeleportState.Pending<>(0, "latest position"), state.take(connection, world, player));
        assertNull(state.take(connection, world, player));
        assertNull(state.peek(connection, world, player));
    }

    @Test
    void reconnectWorldChangeAndRespawnDiscardOldAcknowledgements() {
        Object[][] contexts = {{new Object(), world, player}, {connection, new Object(), player},
                {connection, world, new Object()}, {null, world, player}, {connection, null, player},
                {connection, world, null}};
        for (Object[] context : contexts) {
            state.remember(connection, world, player, 42, "old session");
            assertNull(state.take(context[0], context[1], context[2]));
            assertNull(state.take(connection, world, player), "Discard permanently, even if the old context returns");
        }
    }

    @Test
    void renderingDoesNotConsumeRecoveryAndWorldResetClearsRendering() {
        state.remember(connection, world, player, 7, "server position");
        var expected = new ReachTeleportState.Pending<>(7, "server position");
        assertEquals(expected, state.peek(connection, world, player));
        assertEquals(expected, state.peek(connection, world, player));
        assertNotNull(state.take(connection, world, player));
        state.remember(connection, world, player, 8, "next position");
        state.clear();
        assertNull(state.peek(connection, world, player));
        assertNull(state.take(connection, world, player));
    }
}
