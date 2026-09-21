package cn.omix.util.network;

import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PacketLogHistoryTest {
    private final PacketLogHistory history = new PacketLogHistory();

    private static PacketLogBuffer.Entry entry(String name, boolean sent, String details) {
        return new PacketLogBuffer.Entry(name, details, 42, sent, false, false, false, false, true);
    }

    private PacketLogHistory.Page read(String cursor, int limit) {
        return history.read(cursor, limit, PacketLogHistory.Direction.ALL, PacketLogRules.ALL, true);
    }

    @Test
    void readsAreNonConsumingAndPaginationDoesNotRepeatOrSkipRecords() {
        for (int i = 0; i < 5; i++) history.append(entry("minecraft:ping", false, "parameter=" + i));
        var first = read(null, 2);
        assertEquals(2, first.entries().size());
        assertTrue(first.hasMore());
        assertEquals(first, read(null, 2));
        var second = read(first.nextCursor(), 2);
        assertEquals(3, second.entries().getFirst().sequence());
        var third = read(second.nextCursor(), 2);
        assertEquals(5, third.entries().getFirst().sequence());
        assertFalse(third.hasMore());
        assertTrue(read(third.nextCursor(), 2).entries().isEmpty());
        history.append(entry("minecraft:ping", false, "new"));
        assertEquals(6, read(third.nextCursor(), 2).entries().getFirst().sequence());
    }

    @Test
    void overrunReportsMissingSequenceRangeAndKeepsNewestBoundedHistory() {
        history.append(entry("minecraft:ping", false, "first"));
        String cursor = read(null, 1).nextCursor();
        for (int i = 0; i < PacketLogHistory.CAPACITY + 3; i++) history.append(entry("minecraft:ping", false, "next"));
        var page = read(cursor, 50);
        assertEquals(PacketLogHistory.CAPACITY, page.stats().retained());
        assertEquals(4, page.stats().overwritten());
        assertEquals(3, page.missed());
        assertEquals(5, page.entries().getFirst().sequence());
    }

    @Test
    void clearingInvalidatesOldCursorsEvenWhenSequencesAreReused() {
        history.append(entry("minecraft:ping", false, "old world"));
        var before = read(null, 1);
        history.clear();
        history.append(entry("minecraft:ping", false, "new world"));
        assertThrows(IllegalArgumentException.class, () -> read(before.nextCursor(), 1));
        var after = read(null, 1);
        assertNotEquals(before.stats().sessionId(), after.stats().sessionId());
        assertEquals(1, after.stats().captured());
        assertEquals("new world", after.entries().getFirst().entry().details());
        assertThrows(IllegalArgumentException.class, () -> read(after.stats().sessionId() + ":999", 1));
    }

    @Test
    void readFiltersAdvanceAcrossExcludedTrafficWithoutChangingCapture() {
        history.append(entry("minecraft:keep_alive", true, "sent"));
        history.append(entry("minecraft:ping", false, "filtered"));
        history.append(entry("minecraft:keep_alive", false, "received"));
        history.append(entry("minecraft:ping", false, "filtered tail"));
        var page = history.read(null, 1, PacketLogHistory.Direction.RECEIVED, PacketLogRules.parse("keep_alive", ""), true);
        assertEquals(1, page.entries().size());
        assertEquals(3, page.entries().getFirst().sequence());
        assertFalse(page.hasMore());
        assertTrue(page.nextCursor().endsWith(":4"));
        assertEquals(4, read(null, 50).entries().size());
    }

    @Test
    void contentBudgetStopsBeforeNextEntryAndMetadataOnlyReadsCanFitMore() {
        for (int i = 0; i < 10; i++) history.append(entry("minecraft:ping", false, "x".repeat(3000)));
        var page = read(null, 50);
        assertEquals(3, page.entries().size());
        assertTrue(page.hasMore());
        assertEquals(4, read(page.nextCursor(), 50).entries().getFirst().sequence());
        assertEquals(10, history.read(null, 50, PacketLogHistory.Direction.ALL, PacketLogRules.ALL, false).entries().size());
    }

    @Test
    void chatDrainsAndStoppingDoNotConsumeHistoryAndOverflowDoesNotStopCapture() {
        var buffer = new PacketLogBuffer();
        var filter = new PacketLogFilter(true, true, true, true, Set.of(), PacketLogRules.ALL);
        var options = new PacketLogBuffer.Options(filter, filter, false, false, true);
        for (int i = 0; i < PacketLogBuffer.CAPACITY + 5; i++)
            buffer.offer(new CommonPingS2CPacket(i), false, false, false, false, i, options);
        assertEquals(5, buffer.totalChatDropped());
        assertEquals(PacketLogBuffer.CAPACITY + 5, buffer.historyStats().captured());
        assertEquals(PacketLogHistory.CAPACITY, buffer.historyStats().retained());
        buffer.drain(100);
        buffer.clearPending(); // same operation as stopping the module
        assertEquals(PacketLogHistory.CAPACITY, buffer.historyStats().retained());
        assertTrue(buffer.drain(1).isEmpty());
        buffer.clear();
        assertEquals(0, buffer.historyStats().retained());
        assertEquals(0, buffer.totalChatDropped());
    }

    @Test
    void quietCaptureStillRecordsAndPreservesMovementCompactionAcrossTicks() {
        var buffer = new PacketLogBuffer();
        var filter = new PacketLogFilter(true, true, true, true, Set.of(), PacketLogRules.ALL);
        var options = new PacketLogBuffer.Options(filter, filter, true, false, false);
        var movement = new PlayerMoveC2SPacket.OnGroundOnly(true, false);
        buffer.offer(movement, true, false, false, false, 1, options);
        buffer.clearPending(); // quiet tick
        buffer.offer(movement, true, false, false, false, 2, options);
        assertEquals(1, buffer.historyStats().captured());
        assertTrue(buffer.drain(10).isEmpty());
        assertEquals(0, buffer.totalChatDropped());
    }
}
