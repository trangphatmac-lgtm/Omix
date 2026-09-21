package cn.omix.util.network;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Non-consuming bounded history. Cursors are scoped to a capture/world, never to a chat drain. */
public final class PacketLogHistory {
    public static final int CAPACITY = 512;
    public static final int PAGE_CONTENT_CHARS = 12_000;
    private final ArrayDeque<Logged> entries = new ArrayDeque<>();
    private String sessionId = UUID.randomUUID().toString();
    private long sequence;

    public enum Direction { ALL, SENT, RECEIVED }
    public record Logged(long sequence, long timestampMillis, PacketLogBuffer.Entry entry) {}
    public record Stats(String sessionId, int retained, long captured, long overwritten, String latestCursor) {}
    public record Page(Stats stats, List<Logged> entries, String nextCursor, boolean hasMore, long missed) {}

    public synchronized void append(PacketLogBuffer.Entry entry) {
        if (entries.size() == CAPACITY) entries.removeFirst();
        entries.addLast(new Logged(++sequence, System.currentTimeMillis(), entry));
    }

    public synchronized Stats stats() {
        return new Stats(sessionId, entries.size(), sequence, sequence - entries.size(), cursor(sequence));
    }

    public synchronized Page read(String cursor, int limit, Direction direction, PacketLogRules rules, boolean details) {
        if (limit < 1 || limit > 50) throw new IllegalArgumentException("limit must be an integer from 1 through 50.");
        long after = 0;
        if (cursor != null) {
            int separator = cursor.lastIndexOf(':');
            if (separator < 0 || !sessionId.equals(cursor.substring(0, separator))) {
                throw new IllegalArgumentException("Packet log cursor expired; read without cursor to inspect the current capture.");
            }
            try { after = Long.parseLong(cursor.substring(separator + 1)); }
            catch (NumberFormatException exception) { throw new IllegalArgumentException("Invalid packet log cursor."); }
            if (after < 0 || after > sequence) throw new IllegalArgumentException("Invalid packet log cursor sequence.");
        }
        long oldest = entries.isEmpty() ? sequence + 1 : entries.getFirst().sequence();
        long missed = Math.max(0, oldest - after - 1);
        long scanned = Math.max(after, oldest - 1);
        int characters = 0;
        List<Logged> result = new ArrayList<>();
        for (Logged logged : entries) {
            if (logged.sequence() <= after) continue;
            var entry = logged.entry();
            boolean matches = (direction == Direction.ALL || entry.sent() == (direction == Direction.SENT))
                    && rules.allows(entry.name());
            int cost = 256 + entry.name().length() + (details ? entry.details().length() : 0);
            if (matches && (result.size() == limit || !result.isEmpty() && characters + cost > PAGE_CONTENT_CHARS)) break;
            scanned = logged.sequence();
            if (matches) {
                result.add(logged);
                characters += cost;
            }
        }
        return new Page(stats(), List.copyOf(result), cursor(scanned), scanned < sequence, missed);
    }

    public synchronized void clear() {
        entries.clear();
        sequence = 0;
        sessionId = UUID.randomUUID().toString();
    }

    private String cursor(long sequence) {
        return sessionId + ":" + sequence;
    }
}
