package cn.omix.util.network;

import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Bounded text snapshots; never retains packets, worlds, or payload buffers. */
public final class PacketLogBuffer {
    public static final int CAPACITY = 512;
    private final ArrayDeque<Entry> entries = new ArrayDeque<>();
    private final PacketLogHistory history = new PacketLogHistory();
    private long dropped;
    private long totalChatDropped;
    private boolean previousMovement;
    private boolean previousCancelled;
    private boolean previousBypass;

    public record Options(PacketLogFilter sent, PacketLogFilter received, boolean compactMovement, boolean detail,
                          boolean chatOutput) {}

    public record Entry(String name, String details, long tick, boolean sent, boolean cancelled,
                        boolean bypass, boolean movement, boolean bundled, boolean detail) {}

    public synchronized void offer(Packet<?> packet, boolean sent, boolean cancelled, boolean bypass,
                                   boolean bundled, long tick, Options options) {
        PacketLogFilter filter = sent ? options.sent() : options.received();
        if (!filter.allows(packet, cancelled, bypass, bundled)) return;
        boolean movement = packet instanceof PlayerMoveC2SPacket;
        if (sent && movement && options.compactMovement() && previousMovement
                && previousCancelled == cancelled && previousBypass == bypass) return;
        Entry entry = new Entry(PacketLogFormatter.name(packet), PacketLogFormatter.details(packet, options.detail()),
                tick, sent, cancelled, bypass, movement, bundled, options.detail());
        history.append(entry);
        if (options.chatOutput()) {
            if (entries.size() >= CAPACITY) {
                dropped++;
                totalChatDropped++;
            } else entries.addLast(entry);
        }
        if (sent) {
            previousMovement = movement && options.compactMovement();
            previousCancelled = cancelled;
            previousBypass = bypass;
        }
    }

    public synchronized List<Entry> drain(int limit) {
        List<Entry> result = new ArrayList<>();
        while (result.size() < limit && !entries.isEmpty()) result.add(entries.removeFirst());
        return result;
    }

    public synchronized long takeDropped() {
        long result = dropped;
        dropped = 0;
        return result;
    }

    public synchronized void clear() {
        clearPending();
        history.clear();
        totalChatDropped = 0;
        previousMovement = false;
        previousCancelled = false;
        previousBypass = false;
    }

    public synchronized void clearPending() {
        entries.clear();
        dropped = 0;
    }

    public synchronized PacketLogHistory.Stats historyStats() { return history.stats(); }

    public synchronized long totalChatDropped() { return totalChatDropped; }

    public synchronized PacketLogHistory.Page readHistory(String cursor, int limit, PacketLogHistory.Direction direction,
                                                          PacketLogRules rules, boolean details) {
        return history.read(cursor, limit, direction, rules, details);
    }
}
