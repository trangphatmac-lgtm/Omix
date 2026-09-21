package cn.omix.util.network;

import cn.omix.Client;
import cn.omix.event.impl.PacketEvent;
import cn.omix.event.impl.PacketLogEvent;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.Packet;

/** Bridges final event decisions to logging without replaying cancellable packet events. */
public final class PacketLogHooks {
    private static final ThreadLocal<ReceiveScope> RECEIVING = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SUPPRESS_SEND = new ThreadLocal<>();

    private PacketLogHooks() {}

    public static void sent(ClientConnection connection, Packet<?> packet, boolean cancelled, boolean bypass) {
        if (!Boolean.TRUE.equals(SUPPRESS_SEND.get()) && connection.getSide() == NetworkSide.CLIENTBOUND) {
            publish(connection, packet, PacketEvent.Type.Send, cancelled, bypass);
        }
    }

    public static void replacement(Runnable send) {
        Boolean previous = SUPPRESS_SEND.get();
        SUPPRESS_SEND.set(true);
        try {
            send.run();
        } finally {
            if (previous == null) SUPPRESS_SEND.remove();
            else SUPPRESS_SEND.set(previous);
        }
    }

    public static void receive(ClientConnection connection, Packet<?> packet, Runnable apply) {
        if (connection.getSide() != NetworkSide.CLIENTBOUND || !listening()) {
            apply.run();
            return;
        }
        ReceiveScope previous = RECEIVING.get();
        ReceiveScope scope = new ReceiveScope(packet);
        RECEIVING.set(scope);
        try {
            apply.run();
        } finally {
            if (previous == null) RECEIVING.remove();
            else RECEIVING.set(previous);
            publish(connection, packet, PacketEvent.Type.Received, scope.cancelled, false);
        }
    }

    public static void receivedDecision(Packet<?> packet, boolean cancelled) {
        ReceiveScope scope = RECEIVING.get();
        if (scope != null && scope.packet == packet) scope.cancelled = cancelled;
    }

    private static boolean listening() {
        return Client.instance != null && Client.instance.getEventManager() != null
                && Client.instance.getEventManager().hasListeners(PacketLogEvent.class);
    }

    private static void publish(ClientConnection connection, Packet<?> packet, PacketEvent.Type direction,
                                boolean cancelled, boolean bypass) {
        if (packet != null && listening()) {
            Client.instance.getEventManager().call(new PacketLogEvent(connection, packet, direction, cancelled, bypass));
        }
    }

    private static final class ReceiveScope {
        private final Packet<?> packet;
        private boolean cancelled;

        private ReceiveScope(Packet<?> packet) {
            this.packet = packet;
        }
    }
}
