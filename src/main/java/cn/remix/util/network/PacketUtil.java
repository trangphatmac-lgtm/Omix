package cn.remix.util.network;

import cn.remix.util.IMinecraft;
import injection.accessor.ClientWorldAccessor;
import lombok.Getter;
import lombok.experimental.UtilityClass;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.client.network.SequencedPacketCreator;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;

import java.util.ArrayList;
import java.util.function.Supplier;

@UtilityClass
public class PacketUtil implements IMinecraft {
    @Getter
    private final ArrayList<Packet<?>> packets = new ArrayList<>();
    private final ThreadLocal<Integer> eventBypassDepth = ThreadLocal.withInitial(() -> 0);

    public boolean isBypassingEvents() {
        return eventBypassDepth.get() > 0;
    }

    public <T> T runWithoutEvents(Supplier<T> action) {
        eventBypassDepth.set(eventBypassDepth.get() + 1);
        try {
            return action.get();
        } finally {
            int depth = eventBypassDepth.get() - 1;
            if (depth == 0) {
                eventBypassDepth.remove();
            } else {
                eventBypassDepth.set(depth);
            }
        }
    }

    public void sendPacket(Packet<?> packet) {
        if (mc.getNetworkHandler() == null) return;

        mc.getNetworkHandler().sendPacket(packet);
    }

    public void sendPacketNoEvent(Packet<?> packet) {
        packets.add(packet);
        sendPacket(packet);
    }

    public void sendSequencedPacket(SequencedPacketCreator packetCreator) {
        if (mc.getNetworkHandler() == null || mc.world == null) return;

        try (PendingUpdateManager pendingUpdateManager = ((ClientWorldAccessor) mc.world).getPendingUpdateManagerField().incrementSequence()) {
            int sequence = pendingUpdateManager.getSequence();
            Packet<?> packet = packetCreator.predict(sequence);
            mc.getNetworkHandler().sendPacket(packet);
        }
    }

    public void sendSequencedPacketNoEvent(SequencedPacketCreator packetCreator) {
        if (mc.getNetworkHandler() == null || mc.world == null) return;

        try (PendingUpdateManager pendingUpdateManager = ((ClientWorldAccessor) mc.world).getPendingUpdateManagerField().incrementSequence()) {
            int sequence = pendingUpdateManager.getSequence();
            Packet<?> packet = packetCreator.predict(sequence);
            packets.add(packet);
            mc.getNetworkHandler().sendPacket(packet);
        }
    }

    @SuppressWarnings("unchecked")
    public void receivePacket(Packet<?> packet) {
        if (mc.getNetworkHandler() == null) return;
        ((Packet<ClientPlayPacketListener>) packet).apply(mc.getNetworkHandler());
    }

    public void receivePacketNoEvent(Packet<?> packet) {
        packets.add(packet);
        receivePacket(packet);
    }
}
