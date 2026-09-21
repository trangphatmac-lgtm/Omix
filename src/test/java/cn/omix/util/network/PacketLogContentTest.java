package cn.omix.util.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PacketLogContentTest {
    @Test
    void detailIncludesUncataloguedReceivedMessageContentInInlineOutput() {
        var packet = new GameMessageS2CPacket(Text.literal("Hello from server"), true);
        assertFalse(PacketLogFormatter.details(packet, false).contains("Hello from server"));
        String inline = PacketLogFormatter.inlineDetails(PacketLogFormatter.details(packet, true));
        assertTrue(inline.contains("Hello from server"));
        assertTrue(inline.contains("overlay=true"));
        assertFalse(inline.contains("\n"));
        assertEquals("", PacketLogFormatter.inlineDetails("minecraft:client_tick_end"));
    }

    @Test
    void inheritedPacketFieldsAreIncludedWithoutStaticCodecFields() {
        var packet = new EntityS2CPacket.MoveRelative(7, (short) 12, (short) -8, (short) 4, true);
        String content = PacketLogContent.snapshot(packet);
        assertTrue(content.contains("id=7"));
        assertTrue(content.contains("deltaX=12"));
        assertTrue(content.contains("deltaY=-8"));
        assertFalse(content.contains("CODEC"));
    }

    @Test
    void bytePreviewDoesNotConsumeOrReleaseBuffers() {
        var bytes = Unpooled.buffer();
        try {
            bytes.writeBytes(new byte[]{0, 1, 2, (byte) 255});
            bytes.readByte();
            assertEquals("bytes[3]:0102ff", PacketLogContent.snapshot(bytes));
            assertEquals(1, bytes.readerIndex());
            assertEquals(4, bytes.writerIndex());
            assertEquals(1, bytes.refCnt());
        } finally {
            bytes.release();
        }
    }

    @Test
    void cyclesAndDepthAreBoundedAndLongCollectionsDoNotExpandFully() {
        List<Object> cycle = new ArrayList<>();
        cycle.add(cycle);
        assertTrue(PacketLogContent.snapshot(cycle).contains("<cycle>"));
        Object nested = List.of(List.of(List.of(List.of(List.of("hidden")))));
        assertTrue(PacketLogContent.snapshot(nested).contains("<max depth>"));
        assertTrue(PacketLogContent.snapshot(new int[100]).contains("100 items"));
        List<String> large = java.util.Collections.nCopies(1000, "x".repeat(1000));
        String content = PacketLogContent.snapshot(large);
        assertTrue(content.length() <= PacketLogContent.MAX_CHARS);
        assertTrue(content.endsWith("…"));
        assertFalse(PacketLogContent.snapshot("hello\n§aworld").contains("§"));
    }

    @Test
    void nestedPayloadValuesAreRenderedWithoutCallingArbitraryToString() {
        class Payload {
            final String channel = "example:test";
            final int[] values = {1, 2, 3};
            @Override public String toString() { throw new AssertionError("Must read fields"); }
        }
        String content = PacketLogContent.snapshot(new Payload());
        assertTrue(content.contains("channel=\"example:test\""));
        assertTrue(content.contains("values=[1, 2, 3]"));
    }
}
