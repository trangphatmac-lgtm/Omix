package cn.omix.event.impl;

import cn.omix.event.base.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.network.packet.Packet;

@Setter
@Getter
@AllArgsConstructor
public class PacketEvent extends Event {
    private Packet<?> packet;
    private final Type type;

    public enum Type {
        Send,
        Received
    }
}
