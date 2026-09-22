package cn.omix.management.packet;

import cn.omix.event.impl.PacketEvent;
import cn.omix.util.IMinecraft;
import cn.omix.util.network.PacketUtil;
import net.minecraft.network.packet.Packet;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

public abstract class SubCore implements IMinecraft {
    public final ConcurrentLinkedDeque<Packet<?>> packets = new ConcurrentLinkedDeque<>();
    private final List<Object> holders = new ArrayList<>();
    private static final Object LEGACY_OWNER = new Object();
    public volatile boolean active;

    public void start() { start(LEGACY_OWNER); }

    public synchronized void start(Object holder) {
        if (!holders.contains(holder)) {
            holders.add(holder);
        }
        if (!active) {
            active = true;
        }
    }

    public synchronized void release(boolean clear) {
        if (!packets.isEmpty()) {
            packets.forEach(packet -> {
                if (mc.getNetworkHandler() != null && mc.player != null) {
                    onRelease(packet);
                }
            });

            if (clear) {
                packets.clear();
            }
        }
    }

    public synchronized void dispatch(boolean releasePackets) {
        if (releasePackets) {
            release(true);
        }
        holders.clear();
        active = false;
    }

    public synchronized void dispatch(Object holder, boolean releasePackets) {
        if (!holders.remove(holder)) return;

        if (holders.isEmpty()) {
            if (releasePackets) {
                release(true);
            }
            active = false;
        }
    }

    public void dispatch(Object holder) {
        dispatch(holder, true);
    }

    public void dispatch() {
        dispatch(true);
    }

    public synchronized void clear() {
        packets.clear();
        holders.clear();
        active = false;
    }

    public synchronized void handle(PacketEvent event) {
        Packet<?> packet = event.getPacket();

        if (PacketUtil.getPackets().contains(packet)) {
            PacketUtil.getPackets().remove(packet);
            return;
        }

        if (shouldIgnore(packet)) return;

        if (active) {
            event.setCancelled(true);
            packets.add(packet);
        }
    }

    protected abstract void onRelease(Packet<?> packet);
    protected abstract boolean shouldIgnore(Packet<?> packet);
}
