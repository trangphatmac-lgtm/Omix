package cn.omix.util.combat;

/** Keeps only the latest acknowledgement, scoped to the original connection, world and player. */
public final class ReachTeleportState<T> {
    private Object connection;
    private Object world;
    private Object player;
    private Pending<T> pending;

    public synchronized void remember(Object connection, Object world, Object player, int id, T position) {
        this.connection = connection;
        this.world = world;
        this.player = player;
        pending = new Pending<>(id, position);
    }

    public synchronized Pending<T> peek(Object connection, Object world, Object player) {
        if (connection == null || world == null || player == null
                || this.connection != connection || this.world != world || this.player != player) {
            clear();
        }
        return pending;
    }

    public synchronized Pending<T> take(Object connection, Object world, Object player) {
        Pending<T> result = peek(connection, world, player);
        clear();
        return result;
    }

    public synchronized void clear() {
        pending = null;
        connection = world = player = null;
    }

    public record Pending<T>(int id, T position) {}
}
