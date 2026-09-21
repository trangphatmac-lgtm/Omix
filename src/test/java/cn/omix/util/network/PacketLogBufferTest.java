package cn.omix.util.network;

import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientTickEndC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class PacketLogBufferTest {
    private final PacketLogBuffer buffer = new PacketLogBuffer();
    private static final PacketLogBuffer.Options ALL = options(
            true, true, true, true, false, false, false, false, false);

    private static PacketLogBuffer.Options options(boolean sent, boolean received, boolean cancelled, boolean noEvent,
                                                   boolean keepAlive, boolean movement, boolean compact,
                                                   boolean pingPong, boolean tickEnd) {
        var ignored = java.util.EnumSet.noneOf(PacketLogFilter.Kind.class);
        if (keepAlive) ignored.add(PacketLogFilter.Kind.KEEP_ALIVE);
        if (movement) ignored.add(PacketLogFilter.Kind.MOVEMENT);
        if (pingPong) ignored.add(PacketLogFilter.Kind.PING_PONG);
        if (tickEnd) ignored.add(PacketLogFilter.Kind.TICK_END);
        return new PacketLogBuffer.Options(
                new PacketLogFilter(sent, cancelled, noEvent, true, ignored, PacketLogRules.ALL),
                new PacketLogFilter(received, cancelled, noEvent, true, ignored, PacketLogRules.ALL), compact, false, true);
    }

    private void offer(Packet<?> packet, boolean sent, boolean cancelled, boolean bypass, PacketLogBuffer.Options options) {
        buffer.offer(packet, sent, cancelled, bypass, false, 42, options);
    }

    private static List<PlayerMoveC2SPacket> movements() {
        return List.of(new PlayerMoveC2SPacket.Full(1, 2, 3, 4, 5, true, false),
                new PlayerMoveC2SPacket.PositionAndOnGround(1, 2, 3, false, true),
                new PlayerMoveC2SPacket.LookAndOnGround(4, 5, false, false),
                new PlayerMoveC2SPacket.OnGroundOnly(true, false));
    }

    @Test
    void cancellationAndBypassFiltersApplyInBothDirections() {
        var options = options(true, true, false, false, false, false, false, false, false);
        offer(new CommonPongC2SPacket(1), true, true, false, options);
        offer(new CommonPingS2CPacket(1), false, true, false, options);
        offer(new CommonPongC2SPacket(2), true, false, true, options);
        offer(new CommonPingS2CPacket(2), false, false, false, options);
        assertEquals(1, buffer.drain(10).size());
        offer(new CommonPongC2SPacket(3), true, true, true, ALL);
        var entry = buffer.drain(1).getFirst();
        assertTrue(entry.cancelled());
        assertTrue(entry.bypass());
        assertEquals(42, entry.tick());
    }

    @Test
    void directionTogglesAreIndependent() {
        var receivedOnly = options(false, true, true, true, false, false, false, false, false);
        offer(new CommonPongC2SPacket(1), true, false, false, receivedOnly);
        offer(new CommonPingS2CPacket(1), false, false, false, receivedOnly);
        assertFalse(buffer.drain(10).getFirst().sent());
        var sentOnly = options(true, false, true, true, false, false, false, false, false);
        offer(new CommonPingS2CPacket(1), false, false, false, sentOnly);
        offer(new CommonPongC2SPacket(1), true, false, false, sentOnly);
        assertTrue(buffer.drain(10).getFirst().sent());
    }

    @Test
    void modernKeepAliveAndPingPongAreSeparateFilters() {
        var ignoreKeepAlive = options(true, true, true, true, true, false, false, false, false);
        offer(new KeepAliveC2SPacket(Long.MAX_VALUE), true, false, false, ignoreKeepAlive);
        offer(new KeepAliveS2CPacket(Long.MAX_VALUE), false, false, false, ignoreKeepAlive);
        offer(new CommonPongC2SPacket(-7), true, false, false, ignoreKeepAlive);
        offer(new CommonPingS2CPacket(-7), false, false, false, ignoreKeepAlive);
        assertEquals(2, buffer.drain(10).size());
        var ignorePing = options(true, true, true, true, false, false, false, true, false);
        offer(new CommonPongC2SPacket(-7), true, false, false, ignorePing);
        offer(new CommonPingS2CPacket(-7), false, false, false, ignorePing);
        offer(new KeepAliveS2CPacket(10), false, false, false, ignorePing);
        assertEquals(1, buffer.drain(10).size());
    }

    @Test
    void allFourMovementVariantsAreFilteredAndIgnoreWinsOverCompact() {
        var options = options(true, true, true, true, false, true, true, false, false);
        movements().forEach(p -> offer(p, true, false, false, options));
        offer(new UpdateSelectedSlotC2SPacket(2), true, false, false, options);
        assertEquals(1, buffer.drain(10).size());
    }

    @Test
    void compactKeepsFirstAndRestartsAfterOtherSendsOrStatusChanges() {
        var compact = options(true, true, true, true, false, false, true, false, true);
        var move = movements().getFirst();
        offer(move, true, false, false, compact);
        offer(ClientTickEndC2SPacket.INSTANCE, true, false, false, compact);
        offer(new CommonPingS2CPacket(1), false, false, false, compact);
        offer(move, true, false, false, compact);
        assertEquals(2, buffer.drain(10).size(), "Receive and filtered tick-end do not split a movement run");
        offer(move, true, true, false, compact);
        offer(move, true, false, true, compact);
        offer(new UpdateSelectedSlotC2SPacket(2), true, false, false, compact);
        offer(move, true, false, true, compact);
        assertEquals(4, buffer.drain(10).size());
        buffer.clear();
        offer(move, true, false, false, compact);
        assertEquals(1, buffer.drain(10).size(), "A new session must show its first movement");
    }

    @Test
    void queueIsBoundedAndDrainsInOrderWithOverflowAndReset() {
        for (int i = 0; i < PacketLogBuffer.CAPACITY + 3; i++) {
            offer(new CommonPingS2CPacket(i), false, false, false, ALL);
        }
        assertEquals(3, buffer.takeDropped());
        assertEquals(0, buffer.takeDropped());
        var first = buffer.drain(30);
        assertEquals(30, first.size());
        assertTrue(first.getFirst().details().contains("Ping parameter: 0"));
        assertTrue(first.getLast().details().contains("Ping parameter: 29"));
        assertEquals(PacketLogBuffer.CAPACITY - 30, buffer.drain(1000).size());
        offer(new CommonPingS2CPacket(99), false, false, false, ALL);
        buffer.clear();
        assertTrue(buffer.drain(1000).isEmpty());
        assertEquals(0, buffer.takeDropped());
    }

    @Test
    void concurrentProducersCannotExceedCapacity() throws Exception {
        try (var executor = Executors.newFixedThreadPool(4)) {
            var tasks = new java.util.ArrayList<java.util.concurrent.Callable<Void>>();
            for (int worker = 0; worker < 4; worker++) tasks.add(() -> {
                for (int i = 0; i < 200; i++) offer(new CommonPingS2CPacket(i), false, false, false, ALL);
                return null;
            });
            for (var result : executor.invokeAll(tasks)) result.get();
        }
        assertEquals(PacketLogBuffer.CAPACITY, buffer.drain(1000).size());
        assertEquals(800 - PacketLogBuffer.CAPACITY, buffer.takeDropped());
    }
}
