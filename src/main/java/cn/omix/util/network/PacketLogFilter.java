package cn.omix.util.network;

import net.minecraft.network.packet.Packet;

import java.util.Set;

/** One direction's immutable settings snapshot, published by the client tick. */
public record PacketLogFilter(boolean enabled, boolean includeCancelled, boolean includeNoEvent,
                              boolean includeBundle, Set<Kind> ignored, PacketLogRules rules) {
    public PacketLogFilter {
        ignored = Set.copyOf(ignored);
    }

    public enum Kind { KEEP_ALIVE, PING_PONG, MOVEMENT, TICK_END, CHUNKS, LIGHT, PARTICLES, SOUNDS, CUSTOM_PAYLOAD, OTHER }

    public boolean allows(Packet<?> packet, boolean cancelled, boolean bypass, boolean bundled) {
        return enabled && (!cancelled || includeCancelled) && (!bypass || includeNoEvent)
                && (!bundled || includeBundle) && !ignored.contains(kind(packet))
                && rules.allows(PacketLogFormatter.name(packet));
    }

    public static Kind kind(Packet<?> packet) {
        var id = packet.getPacketType().id();
        if (!id.getNamespace().equals("minecraft")) return Kind.OTHER;
        return switch (id.getPath()) {
            case "keep_alive" -> Kind.KEEP_ALIVE;
            case "ping", "pong" -> Kind.PING_PONG;
            case "move_player_pos", "move_player_pos_rot", "move_player_rot", "move_player_status_only",
                 "move_entity_pos", "move_entity_pos_rot", "move_entity_rot", "rotate_head",
                 "entity_position_sync", "teleport_entity", "move_minecart_along_track" -> Kind.MOVEMENT;
            case "client_tick_end" -> Kind.TICK_END;
            case "level_chunk_with_light", "chunks_biomes", "forget_level_chunk",
                 "chunk_batch_start", "chunk_batch_finished", "section_blocks_update" -> Kind.CHUNKS;
            case "light_update" -> Kind.LIGHT;
            case "level_particles" -> Kind.PARTICLES;
            case "sound", "sound_entity", "stop_sound" -> Kind.SOUNDS;
            case "custom_payload" -> Kind.CUSTOM_PAYLOAD;
            default -> Kind.OTHER;
        };
    }
}
