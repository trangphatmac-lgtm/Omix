package cn.omix.util.player.noslow;

import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket;
import net.minecraft.network.packet.s2c.play.*;

import java.util.ArrayDeque;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Shared queue exclusions, with hand-aware use confirmation and FIFO inventory sync. */
public final class GrimNoSlowPackets {
    private boolean receivedUseMetadata;
    private final ArrayDeque<Packet<?>> incoming = new ArrayDeque<>();

    public boolean allowsNoSlow() {
        return receivedUseMetadata;
    }

    public void clear() {
        receivedUseMetadata = false;
        incoming.clear();
    }

    public int size() {
        return incoming.size();
    }

    public void enqueue(Packet<?> packet) {
        incoming.addLast(packet);
    }

    /** Replay every packet once, in arrival order, only into its original session. */
    public void flush(BooleanSupplier sameSession, Consumer<Packet<?>> apply) {
        while (!incoming.isEmpty()) {
            if (!sameSession.getAsBoolean()) {
                clear();
                return;
            }
            apply.accept(incoming.removeFirst());
        }
    }

    /**
     * Grim brackets self use metadata with transactions: the leading pong clears
     * certain use, the trailing pong confirms it. Preserve this receive boundary.
     * Until it arrives, movement retains vanilla slowdown, including for bows.
     */
    public boolean bypassesBuffer(Packet<?> packet, int playerId, int livingFlagsId, GrimNoSlowState.Hand useHand) {
        if (packet instanceof EntityTrackerUpdateS2CPacket tracker && tracker.id() == playerId) {
            for (var entry : tracker.trackedValues()) {
                if (entry.id() == livingFlagsId && entry.value() instanceof Byte flags && (flags & 1) != 0
                        && useHand != null && ((flags & 2) != 0) == (useHand == GrimNoSlowState.Hand.OFF_HAND)) {
                    receivedUseMetadata = true;
                }
            }
        }
        if (packet instanceof CommonPingS2CPacket) return !receivedUseMetadata;
        return bypassesBuffer(packet);
    }

    private static final List<Class<?>> IMMEDIATE_PACKET_TYPES = List.of(
            KeepAliveS2CPacket.class,
            PlayerPositionLookS2CPacket.class,
            PlayerActionResponseS2CPacket.class,
            HealthUpdateS2CPacket.class,
            EntityEquipmentUpdateS2CPacket.class,
            EntitySpawnS2CPacket.class,
            EntitiesDestroyS2CPacket.class,
            EntitySetHeadYawS2CPacket.class,
            EntityDamageS2CPacket.class,
            EntityPassengersSetS2CPacket.class,
            EntityAttachS2CPacket.class,
            ScoreboardDisplayS2CPacket.class,
            ScoreboardObjectiveUpdateS2CPacket.class,
            ScoreboardScoreResetS2CPacket.class,
            ScoreboardScoreUpdateS2CPacket.class,
            SubtitleS2CPacket.class,
            TitleS2CPacket.class,
            PlaySoundS2CPacket.class,
            PlaySoundFromEntityS2CPacket.class,
            WorldEventS2CPacket.class,
            ParticleS2CPacket.class,
            LightUpdateS2CPacket.class,
            WorldTimeUpdateS2CPacket.class,
            GameMessageS2CPacket.class,
            ChatMessageS2CPacket.class,
            ProfilelessChatMessageS2CPacket.class,
            BundleS2CPacket.class,
            ChunkDataS2CPacket.class,
            CustomPayloadS2CPacket.class);

    public static boolean bypassesBuffer(Packet<?> packet) {
        return bypassesBuffer(packet.getClass());
    }

    // Class-only classification also works without bootstrapping item registries.
    static boolean bypassesBuffer(Class<?> packetType) {
        // Full inventory snapshots must wait with single-slot updates, so older
        // queued slots cannot overwrite a newer snapshot when replayed.
        for (Class<?> immediate : IMMEDIATE_PACKET_TYPES) {
            if (immediate.isAssignableFrom(packetType)) return true;
        }
        return false;
    }

    public static GrimNoSlowState.Packet describe(Packet<?> packet) {
        var type = GrimNoSlowState.PacketType.OTHER;
        int entityId = -1;
        if (packet instanceof PlayerInteractItemC2SPacket use) {
            return new GrimNoSlowState.Packet(GrimNoSlowState.PacketType.USE_ITEM, use.getHand() == net.minecraft.util.Hand.MAIN_HAND ? GrimNoSlowState.Hand.MAIN_HAND : GrimNoSlowState.Hand.OFF_HAND,
                    use.getSequence(), use.getYaw(), use.getPitch(), -1);
        } else if (packet instanceof PlayerMoveC2SPacket) type = GrimNoSlowState.PacketType.MOVE;
        else if (packet instanceof PlayerInteractBlockC2SPacket) type = GrimNoSlowState.PacketType.USE_BLOCK;
        else if (packet instanceof UpdateSelectedSlotC2SPacket) type = GrimNoSlowState.PacketType.SELECT_SLOT;
        else if (packet instanceof ClickSlotC2SPacket) type = GrimNoSlowState.PacketType.CLICK_SLOT;
        else if (packet instanceof PlayerActionC2SPacket action
                && action.getAction() == PlayerActionC2SPacket.Action.RELEASE_USE_ITEM) type = GrimNoSlowState.PacketType.RELEASE_USE;
        else if (packet instanceof EntityS2CPacket) type = GrimNoSlowState.PacketType.ENTITY_RELATIVE;
        else if (packet instanceof EntityPositionSyncS2CPacket) type = GrimNoSlowState.PacketType.ENTITY_POSITION_SYNC;
        else if (packet instanceof EntityStatusS2CPacket) type = GrimNoSlowState.PacketType.ENTITY_STATUS;
        else if (packet instanceof EntityPositionS2CPacket position) {
            type = GrimNoSlowState.PacketType.ENTITY_POSITION;
            entityId = position.entityId();
        } else if (packet instanceof EntityTrackerUpdateS2CPacket tracker) {
            type = GrimNoSlowState.PacketType.ENTITY_TRACKER;
            entityId = tracker.id();
        } else if (packet instanceof EntityVelocityUpdateS2CPacket velocity) {
            type = GrimNoSlowState.PacketType.ENTITY_VELOCITY;
            entityId = velocity.getEntityId();
        } else if (packet instanceof SetPlayerInventoryS2CPacket) type = GrimNoSlowState.PacketType.SET_PLAYER_INVENTORY;
        else if (packet instanceof ScreenHandlerSlotUpdateS2CPacket) type = GrimNoSlowState.PacketType.SET_SCREEN_SLOT;
        return new GrimNoSlowState.Packet(type, null, 0, 0, 0, entityId);
    }

}
