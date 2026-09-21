package cn.omix.util.network;

import injection.accessor.PlayerInteractEntityC2SPacketAccessor;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.*;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.common.*;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;

/** Modern protocol summaries, with optional bounded read-only field contents. */
public final class PacketLogFormatter {
    private PacketLogFormatter() {}

    public static String name(Packet<?> packet) {
        // Class names become intermediary names in a production jar; protocol identifiers stay readable.
        return packet.getPacketType().id().toString();
    }

    public static String details(Packet<?> packet, boolean detail) {
        String summary = details(packet);
        return detail ? summary + "\nFields: " + PacketLogContent.snapshot(packet) : summary;
    }

    public static String inlineDetails(String details) {
        int firstLine = details.indexOf('\n');
        return firstLine < 0 ? "" : details.substring(firstLine + 1).replace("\n", " | ");
    }

    public static String details(Packet<?> packet) {
        StringBuilder out = new StringBuilder(name(packet));
        if (packet instanceof PlayerMoveC2SPacket p) {
            field(out, "Position", p.changesPosition() ? vector(new Vec3d(p.getX(0), p.getY(0), p.getZ(0))) : "not included");
            field(out, "Rotation", p.changesLook() ? number(p.getYaw(0)) + ", " + number(p.getPitch(0)) : "not included");
            field(out, "On ground", p.isOnGround());
            field(out, "Horizontal collision", p.horizontalCollision());
            field(out, "Changes position", p.changesPosition());
            field(out, "Changes look", p.changesLook());
        } else if (packet instanceof PlayerActionC2SPacket p) {
            field(out, "Action", p.getAction());
            field(out, "Position", p.getPos().toShortString());
            field(out, "Facing", p.getDirection());
            field(out, "Sequence", p.getSequence());
        } else if (packet instanceof PlayerInteractBlockC2SPacket p) {
            var hit = p.getBlockHitResult();
            field(out, "Hand", p.getHand());
            field(out, "Position", hit.getBlockPos().toShortString());
            field(out, "Facing", hit.getSide());
            field(out, "Hit offset", vector(hit.getPos().subtract(Vec3d.of(hit.getBlockPos()))));
            field(out, "Inside block", hit.isInsideBlock());
            field(out, "Sequence", p.getSequence());
        } else if (packet instanceof PlayerInteractItemC2SPacket p) {
            field(out, "Hand", p.getHand());
            field(out, "Sequence", p.getSequence());
            field(out, "Rotation", number(p.getYaw()) + ", " + number(p.getPitch()));
        } else if (packet instanceof PlayerInteractEntityC2SPacket p) {
            if (p instanceof PlayerInteractEntityC2SPacketAccessor accessor) {
                field(out, "Entity ID", accessor.omix$getEntityId());
            }
            field(out, "Sneaking", p.isPlayerSneaking());
            p.handle(new PlayerInteractEntityC2SPacket.Handler() {
                public void interact(Hand hand) {
                    field(out, "Action", "INTERACT");
                    field(out, "Hand", hand);
                }
                public void interactAt(Hand hand, Vec3d pos) {
                    field(out, "Action", "INTERACT_AT");
                    field(out, "Hand", hand);
                    field(out, "Hit vector", vector(pos));
                }
                public void attack() {
                    field(out, "Action", "ATTACK");
                }
            });
        } else if (packet instanceof UpdateSelectedSlotC2SPacket p) {
            field(out, "Slot", p.getSelectedSlot());
        } else if (packet instanceof ClientCommandC2SPacket p) {
            field(out, "Entity ID", p.getEntityId());
            field(out, "Action", p.getMode());
            field(out, "Mount jump height", p.getMountJumpHeight());
        } else if (packet instanceof PlayerInputC2SPacket p) {
            field(out, "Input", p.input());
        } else if (packet instanceof HandSwingC2SPacket p) {
            field(out, "Hand", p.getHand());
        } else if (packet instanceof ChatMessageC2SPacket p) {
            field(out, "Message length", p.chatMessage().length());
        } else if (packet instanceof CustomPayloadC2SPacket p) {
            field(out, "Channel", p.payload().getId().id());
        } else if (packet instanceof CustomPayloadS2CPacket p) {
            field(out, "Channel", p.payload().getId().id());
        } else if (packet instanceof ClientOptionsC2SPacket p) {
            field(out, "Language", p.options().language());
            field(out, "Chat visibility", p.options().chatVisibility());
            field(out, "View distance", p.options().viewDistance());
        } else if (packet instanceof KeepAliveC2SPacket p) {
            field(out, "KeepAlive ID", p.getId());
        } else if (packet instanceof KeepAliveS2CPacket p) {
            field(out, "KeepAlive ID", p.getId());
        } else if (packet instanceof CommonPongC2SPacket p) {
            field(out, "Pong parameter", p.getParameter());
        } else if (packet instanceof CommonPingS2CPacket p) {
            field(out, "Ping parameter", p.getParameter());
        } else if (packet instanceof ClientStatusC2SPacket p) {
            field(out, "Status", p.getMode());
        } else if (packet instanceof CreativeInventoryActionC2SPacket p) {
            field(out, "Slot", p.slot());
            field(out, "Item", Registries.ITEM.getId(p.stack().getItem()));
            field(out, "Count", p.stack().getCount());
        } else if (packet instanceof ClickSlotC2SPacket p) {
            field(out, "Window", p.syncId());
            field(out, "Revision", p.revision());
            field(out, "Slot", p.slot());
            field(out, "Button", p.button());
            field(out, "Action", p.actionType());
            field(out, "Changed slots", p.modifiedStacks().size());
            // 1.21.11 carries ItemStackHash, not the legacy clicked ItemStack/action number.
            field(out, "Stack data", "ItemStackHash (not a full ItemStack)");
        } else if (packet instanceof CloseHandledScreenC2SPacket p) {
            field(out, "Window", p.getSyncId());
        } else if (packet instanceof TeleportConfirmC2SPacket p) {
            field(out, "Teleport ID", p.getTeleportId());
        } else if (packet instanceof PlayerPositionLookS2CPacket p) {
            field(out, "Teleport ID", p.teleportId());
            field(out, "Position change", vector(p.change().position()));
            field(out, "Velocity change", vector(p.change().deltaMovement()));
            field(out, "Rotation", number(p.change().yaw()) + ", " + number(p.change().pitch()));
            field(out, "Relative flags", p.relatives());
        } else if (packet instanceof PlayerActionResponseS2CPacket p) {
            field(out, "Acknowledged sequence", p.sequence());
        } else if (packet instanceof EntityVelocityUpdateS2CPacket p) {
            field(out, "Entity ID", p.getEntityId());
            field(out, "Velocity", vector(p.getVelocity()));
        } else if (packet instanceof ScreenHandlerSlotUpdateS2CPacket p) {
            field(out, "Window", p.getSyncId());
            field(out, "Revision", p.getRevision());
            field(out, "Slot", p.getSlot());
            field(out, "Item", Registries.ITEM.getId(p.getStack().getItem()));
        } else if (packet instanceof InventoryS2CPacket p) {
            field(out, "Window", p.syncId());
            field(out, "Revision", p.revision());
            field(out, "Slot count", p.contents().size());
        } else if (packet instanceof HealthUpdateS2CPacket p) {
            field(out, "Health", p.getHealth());
            field(out, "Food", p.getFood());
            field(out, "Saturation", p.getSaturation());
        } else if (packet instanceof WorldTimeUpdateS2CPacket p) {
            field(out, "World time", p.time());
            field(out, "Time of day", p.timeOfDay());
        }
        return out.toString();
    }

    private static void field(StringBuilder out, String label, Object value) {
        String text = String.valueOf(value).replace('\n', ' ').replace('\r', ' ').replace('§', '?');
        out.append('\n').append(label).append(": ").append(text, 0, Math.min(text.length(), 256));
    }

    private static String vector(Vec3d v) {
        return number(v.x) + ", " + number(v.y) + ", " + number(v.z);
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
