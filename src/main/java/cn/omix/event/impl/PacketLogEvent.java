package cn.omix.event.impl;

import cn.omix.event.base.Event;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;

/** Observation only: cancelling this event never changes network processing. */
@Getter
@RequiredArgsConstructor
public final class PacketLogEvent extends Event {
    private final ClientConnection connection;
    private final Packet<?> packet;
    private final PacketEvent.Type direction;
    private final boolean packetCancelled;
    private final boolean bypassedEvents;
}
