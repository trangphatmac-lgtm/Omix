package cn.omix.module.impl.combat;

/** Keeps only the latest acknowledgement, scoped to the original connection, world and player. */
final class ReachTeleportState<T> {
    private Object connection;
    private Object world;
    private Object player;
    private Pending<T> pending;
    private boolean awaitingCorrection;

    public synchronized boolean onCorrection(Object connection, Object world, Object player,
                                             int id, T position, boolean suppress) {
        peek(connection, world, player);
        // Recovery always accepts a freshly received correction, even after a rapid re-enable.
        if (awaitingCorrection || !suppress || connection == null || world == null || player == null) {
            clear();
            return false;
        }
        this.connection = connection;
        this.world = world;
        this.player = player;
        pending = new Pending<>(id, position);
        return true;
    }

    public synchronized Pending<T> peek(Object connection, Object world, Object player) {
        if (connection == null || world == null || player == null
                || this.connection != connection || this.world != world || this.player != player) {
            clear();
        }
        return pending;
    }

    public synchronized void beginRecovery(Object connection, Object world, Object player) {
        if (peek(connection, world, player) != null) awaitingCorrection = true;
    }

    public synchronized void clear() {
        pending = null;
        awaitingCorrection = false;
        connection = world = player = null;
    }

    record Pending<T>(int id, T position) {}
}
