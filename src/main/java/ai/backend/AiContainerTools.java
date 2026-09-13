package ai.backend;

import cn.omix.module.impl.player.chest.ChestScreenGuard;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import injection.accessor.ClientPlayerEntityAccessor;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Container discovery and vanilla screen interactions. All world/state access runs on the client thread. */
public final class AiContainerTools {
    private static final Set<String> NAMES = Set.of(
            "getnearbycontainer", "opencontainer", "getcontainer", "clickcontainerslot", "closecontainer");
    private static final Set<String> ACTIONS = Set.of("PICKUP", "QUICK_MOVE", "SWAP");
    // A newly opened handler contains placeholder empty slots until vanilla applies its first inventory packet.
    private static final WeakHashMap<ScreenHandler, Boolean> SYNCHRONIZED = new WeakHashMap<>();
    private AiContainerSnapshot<ItemStack> lastSnapshot;
    private static PendingOpen pendingOpen;

    private static final class PendingOpen {
        final ClientPlayerEntity player;
        final ClientWorld world;
        final BlockPos pos;
        final CompletableFuture<JsonObject> result = new CompletableFuture<>();
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        AiContainerRotation rotation;
        boolean movementCompleted;

        PendingOpen(MinecraftClient client, BlockPos pos) {
            this.player = client.player;
            this.world = client.world;
            this.pos = pos;
        }

        void aim(BlockHitResult hit) {
            // Aim just inside the hit surface to avoid floating-point edge misses.
            Vec3d target = hit.getPos().lerp(pos.toCenterPos(), 0.001);
            rotation = AiContainerRotation.toward(player.getEyePos(), target);
            player.setYaw(rotation.yaw());
            player.setPitch(rotation.pitch());
            movementCompleted = false;
        }
    }

    /** Called only after the normal, non-cancelled player movement update has run. */
    public static void playerMovementUpdated(MinecraftClient client) {
        PendingOpen pending = pendingOpen;
        if (pending != null && pending.player == client.player && pending.world == client.world) {
            pending.movementCompleted = true;
        }
    }

    /** Normal next-tick interaction phase, including when the AI WebUI consumes input events. */
    public static void clientTick(MinecraftClient client) {
        PendingOpen pending = pendingOpen;
        if (pending == null) return;
        if (pending.result.isDone()) {
            pendingOpen = null;
            return;
        }
        try {
            if (System.nanoTime() >= pending.deadline) {
                throw new IllegalStateException("Container rotation synchronization timed out; no interaction was sent.");
            }
            if (client.player != pending.player || client.world != pending.world || client.interactionManager == null) {
                throw new IllegalStateException("World or player changed while turning toward the container.");
            }
            requireOpeningPlayer(client);
            if (!isContainer(client, pending.pos) || !client.player.canInteractWithBlockAt(pending.pos, 0)) {
                throw new IllegalStateException("Container disappeared or moved outside interaction reach while turning.");
            }
            ClientPlayerEntityAccessor sent = (ClientPlayerEntityAccessor) client.player;
            if (pending.rotation.ready(pending.movementCompleted, sent.getLastYaw(), sent.getLastPitch())) {
                Vec3d eye = client.player.getEyePos();
                Vec3d end = eye.add(Vec3d.fromPolar(sent.getLastPitch(), sent.getLastYaw())
                        .multiply(client.player.getBlockInteractionRange()));
                BlockHitResult hit = client.world.raycast(new RaycastContext(eye, end,
                        RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, client.player));
                if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pending.pos)) {
                    // Consume before interacting: callbacks or later ticks must never send a duplicate click.
                    pendingOpen = null;
                    var action = client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hit);
                    JsonObject result = describeBlock(client, pending.pos);
                    result.addProperty("status", "interaction_submitted");
                    result.addProperty("clientAccepted", action.isAccepted());
                    result.addProperty("message", "Rotation update sent before interaction. Use getcontainer to check server opening and inventory synchronization.");
                    pending.result.complete(result);
                    return;
                }
            }
            BlockHitResult visible = visibleHit(client, pending.pos);
            if (visible == null) throw new IllegalStateException("Container became obstructed while turning.");
            pending.aim(visible);
        } catch (Exception exception) {
            pendingOpen = null;
            pending.result.completeExceptionally(exception);
        }
    }

    public static void inventorySynchronized(MinecraftClient client, int syncId) {
        if (client.player != null && syncId != 0 && client.player.currentScreenHandler.syncId == syncId) {
            SYNCHRONIZED.put(client.player.currentScreenHandler, true);
        }
    }

    static boolean supports(String name) {
        return NAMES.contains(name);
    }

    static boolean isAction(String name) {
        return name.equals("opencontainer") || name.equals("clickcontainerslot") || name.equals("closecontainer");
    }

    static void addDefinitions(JsonArray tools) {
        tools.add(tool("getnearbycontainer",
                "Find loaded block containers within a spherical radius, sorted by distance. Includes chests, barrels, "
                        + "shulker boxes, ender chests, and block-entity menus such as furnaces. Contents are unknown until "
                        + "opened; withinReach and visible do not guarantee the server will allow opening.",
                "range", integer(3, 20, "Search radius in blocks; discovery does not extend interaction reach.")));
        tools.add(tool("opencontainer",
                "Turn toward a visible block container within normal player reach, wait for a normal player rotation "
                        + "update, then verify the sent direction hits it before right-clicking on the next tick. "
                        + "Times out after 3 seconds without interacting if rotation cannot synchronize. Requires no other container open "
                        + "and no sneaking. Returns submission status, not contents or guaranteed success. Use getcontainer next.",
                "pos", string("Absolute or player-relative block coordinates, e.g. '100 64 -20' or '~ ~ ~2'.")));
        tools.add(tool("getcontainer",
                "Read the currently open container after initial server inventory sync. Returns all handler slots, "
                        + "player/container ownership, cursor stack, and a fresh snapshotId. Slot IDs differ from getinventory. "
                        + "If awaiting_sync, retry later. Later reads can include vanilla client predictions."));
        JsonObject action = string("PICKUP: left/right click to take/place items; QUICK_MOVE: shift-click; SWAP: hotbar/offhand swap.");
        JsonArray values = new JsonArray();
        ACTIONS.stream().sorted().forEach(values::add);
        action.add("enum", values);
        tools.add(tool("clickcontainerslot",
                "Perform one vanilla slot click using the latest unchanged getcontainer snapshotId. Every click consumes "
                        + "the snapshot; read getcontainer again to inspect the result. Server may reject/correct the action. "
                        + "Only actual enabled slot IDs are accepted; outside clicks and throwing are unsupported.",
                "snapshotId", string("Latest getcontainer snapshotId; invalid after any slot/cursor change or action."),
                "slot", integer(0, Integer.MAX_VALUE, "Handler slot ID from getcontainer, including player slots."),
                "action", action,
                "button", integer(0, 40, "PICKUP: 0 left, 1 right; QUICK_MOVE: 0; SWAP: 0–8 hotbar or 40 offhand.")));
        tools.add(tool("closecontainer",
                "Close the unchanged inspected container using its latest snapshotId. Cursor must be empty; place any "
                        + "held stack into a slot first. Sends vanilla close notification to the server.",
                "snapshotId", string("Latest getcontainer snapshotId.")));
    }

    static void validateArguments(String name, JsonObject arguments) {
        switch (name) {
            case "getnearbycontainer" -> {
                keys(arguments, "range");
                integerArgument(arguments, "range", 3, 20);
            }
            case "opencontainer" -> {
                keys(arguments, "pos");
                MinecraftCommandToolExecutor.parseBlockPosition(stringArgument(arguments, "pos"), 0, 0, 0);
            }
            case "getcontainer" -> keys(arguments);
            case "closecontainer" -> {
                keys(arguments, "snapshotId");
                stringArgument(arguments, "snapshotId");
            }
            case "clickcontainerslot" -> {
                keys(arguments, "snapshotId", "slot", "action", "button");
                stringArgument(arguments, "snapshotId");
                integerArgument(arguments, "slot", 0, Integer.MAX_VALUE);
                String action = stringArgument(arguments, "action");
                int button = integerArgument(arguments, "button", 0, 40);
                if (!ACTIONS.contains(action)) throw new IllegalArgumentException("action must be PICKUP, QUICK_MOVE, or SWAP.");
                boolean validButton = switch (action) {
                    case "PICKUP" -> button <= 1;
                    case "QUICK_MOVE" -> button == 0;
                    case "SWAP" -> button <= 8 || button == 40;
                    default -> false;
                };
                if (!validButton) throw new IllegalArgumentException("Invalid button for " + action + ".");
            }
            default -> throw new IllegalArgumentException("Unknown container tool: " + name);
        }
    }

    CompletableFuture<JsonObject> execute(MinecraftClient client, String name, JsonObject arguments) {
        if (client.player == null || client.world == null || client.interactionManager == null) {
            lastSnapshot = null;
            throw new IllegalStateException("The player is not connected to a world.");
        }
        if (name.equals("opencontainer")) return open(client, arguments.get("pos").getAsString());
        return CompletableFuture.completedFuture(switch (name) {
            case "getnearbycontainer" -> nearby(client, arguments.get("range").getAsInt());
            case "getcontainer" -> inspect(client);
            case "clickcontainerslot" -> click(client, arguments);
            case "closecontainer" -> close(client, arguments.get("snapshotId").getAsString());
            default -> throw new IllegalArgumentException("Unknown container tool: " + name);
        });
    }

    private JsonObject nearby(MinecraftClient client, int range) {
        List<BlockPos> positions = new ArrayList<>();
        BlockPos center = client.player.getBlockPos();
        Vec3d origin = client.player.getEntityPos();
        for (BlockPos pos : BlockPos.iterate(center.add(-range, -range, -range), center.add(range, range, range))) {
            if (pos.toCenterPos().squaredDistanceTo(origin) <= (double) range * range && isContainer(client, pos)) {
                positions.add(pos.toImmutable());
            }
        }
        positions.sort(Comparator.comparingDouble(pos -> pos.toCenterPos().squaredDistanceTo(origin)));
        JsonArray containers = new JsonArray();
        for (BlockPos pos : positions) {
            JsonObject entry = describeBlock(client, pos);
            entry.addProperty("distance", pos.toCenterPos().distanceTo(origin));
            entry.addProperty("withinReach", client.player.canInteractWithBlockAt(pos, 0));
            entry.addProperty("visible", visibleHit(client, pos) != null);
            entry.addProperty("contentsKnown", false);
            containers.add(entry);
        }
        JsonObject result = status("ok");
        result.addProperty("range", range);
        result.addProperty("count", containers.size());
        result.add("containers", containers);
        return result;
    }

    private static void requireOpeningPlayer(MinecraftClient client) {
        if (client.player.currentScreenHandler != client.player.playerScreenHandler) {
            throw new IllegalStateException("Another container is open. Inspect and close it first.");
        }
        if (!client.player.isAlive() || client.player.isSpectator() || client.player.isSneaking()) {
            throw new IllegalStateException("Opening requires an alive, non-spectating player who is not sneaking.");
        }
        if (client.player.hasVehicle() || client.getCameraEntity() != client.player) {
            throw new IllegalStateException("Opening requires the player's own camera and no vehicle.");
        }
    }

    private CompletableFuture<JsonObject> open(MinecraftClient client, String coordinates) {
        requireOpeningPlayer(client);
        if (pendingOpen != null && !pendingOpen.result.isDone()) {
            throw new IllegalStateException("A container opening is already waiting for rotation synchronization.");
        }
        BlockPos pos = MinecraftCommandToolExecutor.parseBlockPosition(coordinates,
                client.player.getX(), client.player.getY(), client.player.getZ());
        if (!isContainer(client, pos)) throw new IllegalArgumentException("No loaded block container at pos.");
        if (!client.player.canInteractWithBlockAt(pos, 0)) {
            throw new IllegalArgumentException("Container is outside the player's interaction reach. Move closer first.");
        }
        BlockHitResult hit = visibleHit(client, pos);
        if (hit == null) throw new IllegalArgumentException("Container is obstructed. Move to a visible face first.");
        lastSnapshot = null;
        PendingOpen pending = new PendingOpen(client, pos);
        pending.aim(hit);
        pendingOpen = pending;
        CompletableFuture.delayedExecutor(3, TimeUnit.SECONDS).execute(() -> client.execute(() -> {
            if (pendingOpen == pending) {
                pendingOpen = null;
                pending.result.completeExceptionally(new IllegalStateException(
                        "Container rotation synchronization timed out; no interaction was sent."));
            }
        }));
        return pending.result;
    }

    private JsonObject inspect(MinecraftClient client) {
        lastSnapshot = null;
        ScreenHandler handler = openHandler(client);
        if (handler == null) return status("no_open_container");
        if (!SYNCHRONIZED.containsKey(handler)) return status("awaiting_sync");
        lastSnapshot = AiContainerSnapshot.capture(handler, handler.getRevision(), slotStacks(handler),
                handler.getCursorStack(), ItemStack::copy);
        JsonObject result = status("open");
        result.addProperty("snapshotId", lastSnapshot.id());
        result.addProperty("syncId", handler.syncId);
        result.addProperty("revision", handler.getRevision());
        try {
            result.addProperty("type", Registries.SCREEN_HANDLER.getId(handler.getType()).toString());
        } catch (UnsupportedOperationException exception) {
            // Entity inventories (e.g. horses) can use handlers without a registered type.
            result.addProperty("type", handler.getClass().getSimpleName());
        }
        result.addProperty("title", client.currentScreen.getTitle().getString());
        result.addProperty("serverConfirmed", false);
        result.add("cursor", MinecraftCommandToolExecutor.itemStack(handler.getCursorStack()));
        JsonArray slots = new JsonArray();
        for (Slot slot : handler.slots) {
            JsonObject entry = MinecraftCommandToolExecutor.itemStack(slot.getStack());
            entry.addProperty("slot", slot.id);
            entry.addProperty("inventoryIndex", slot.getIndex());
            entry.addProperty("owner", slot.inventory == client.player.getInventory() ? "player" : "container");
            entry.addProperty("enabled", slot.isEnabled());
            entry.addProperty("canTake", slot.canTakeItems(client.player));
            entry.addProperty("maxItemCount", slot.getMaxItemCount());
            slots.add(entry);
        }
        result.add("slots", slots);
        return result;
    }

    private JsonObject click(MinecraftClient client, JsonObject arguments) {
        ScreenHandler handler = requireSnapshot(client, arguments.get("snapshotId").getAsString());
        int slotId = arguments.get("slot").getAsInt();
        if (slotId >= handler.slots.size()) throw new IllegalArgumentException("slot is outside the current container.");
        Slot slot = handler.getSlot(slotId);
        if (!slot.isEnabled()) throw new IllegalArgumentException("slot is disabled.");
        if (!ChestScreenGuard.canUseContainer()) throw new IllegalStateException("Container movement synchronization is pending; retry later.");
        lastSnapshot = null;
        client.interactionManager.clickSlot(handler.syncId, slotId, arguments.get("button").getAsInt(),
                SlotActionType.valueOf(arguments.get("action").getAsString()), client.player);
        JsonObject result = status("click_submitted");
        result.addProperty("message", "Read getcontainer again. The server may reject or correct the predicted result.");
        return result;
    }

    private JsonObject close(MinecraftClient client, String snapshotId) {
        ScreenHandler handler = requireSnapshot(client, snapshotId);
        if (!handler.getCursorStack().isEmpty()) {
            throw new IllegalStateException("Cursor holds items. Place them into a slot before closing.");
        }
        lastSnapshot = null;
        ((HandledScreen<?>) client.currentScreen).close();
        return status(openHandler(client) == null ? "closed" : "close_pending");
    }

    private ScreenHandler requireSnapshot(MinecraftClient client, String id) {
        ScreenHandler handler = openHandler(client);
        if (handler == null || lastSnapshot == null || !lastSnapshot.matches(id, handler, handler.getRevision(),
                slotStacks(handler), handler.getCursorStack(), ItemStack::areEqual)) {
            lastSnapshot = null;
            throw new IllegalStateException("Container snapshot is stale or unavailable. Call getcontainer again.");
        }
        if (!client.player.isAlive() || client.player.isSpectator()) {
            throw new IllegalStateException("Container actions require an alive, non-spectating player.");
        }
        return handler;
    }

    private static ScreenHandler openHandler(MinecraftClient client) {
        ScreenHandler handler = client.player.currentScreenHandler;
        return handler != client.player.playerScreenHandler && handler.syncId != 0
                && client.currentScreen instanceof HandledScreen<?> screen && screen.getScreenHandler() == handler
                ? handler : null;
    }

    private static boolean isContainer(MinecraftClient client, BlockPos pos) {
        if (!client.world.isInBuildLimit(pos)
                || !client.world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) return false;
        var entity = client.world.getBlockEntity(pos);
        return entity instanceof NamedScreenHandlerFactory || entity instanceof EnderChestBlockEntity;
    }

    private static BlockHitResult visibleHit(MinecraftClient client, BlockPos pos) {
        // Try the center and each face; a visible side may remain accessible when the center is occluded.
        Vec3d center = pos.toCenterPos();
        List<Vec3d> targets = new ArrayList<>();
        targets.add(center);
        for (var direction : net.minecraft.util.math.Direction.values()) {
            targets.add(center.add(direction.getOffsetX() * 0.49, direction.getOffsetY() * 0.49, direction.getOffsetZ() * 0.49));
        }
        for (Vec3d target : targets) {
            BlockHitResult hit = client.world.raycast(new RaycastContext(client.player.getEyePos(), target,
                    RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, client.player));
            if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos)) return hit;
        }
        return null;
    }

    private static JsonObject describeBlock(MinecraftClient client, BlockPos pos) {
        BlockState state = client.world.getBlockState(pos);
        JsonObject result = new JsonObject();
        result.addProperty("pos", pos.getX() + " " + pos.getY() + " " + pos.getZ());
        result.addProperty("block", Registries.BLOCK.getId(state.getBlock()).toString());
        return result;
    }

    private static List<ItemStack> slotStacks(ScreenHandler handler) {
        return handler.slots.stream().map(Slot::getStack).toList();
    }

    private static JsonObject status(String status) {
        JsonObject result = new JsonObject();
        result.addProperty("status", status);
        return result;
    }

    private static void keys(JsonObject arguments, String... expected) {
        if (!arguments.keySet().equals(Set.of(expected))) {
            throw new IllegalArgumentException("Expected exactly these arguments: " + String.join(", ", expected));
        }
    }

    private static String stringArgument(JsonObject arguments, String name) {
        var value = arguments.get(name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || value.getAsString().isBlank()) {
            throw new IllegalArgumentException(name + " must be a non-empty string.");
        }
        return value.getAsString();
    }

    private static int integerArgument(JsonObject arguments, String name, int min, int max) {
        var value = arguments.get(name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(name + " must be an integer.");
        }
        double number = value.getAsDouble();
        if (!Double.isFinite(number) || number != Math.rint(number) || number < min || number > max) {
            throw new IllegalArgumentException(name + " must be an integer from " + min + " through " + max + ".");
        }
        return (int) number;
    }

    private static JsonObject string(String description) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "string");
        schema.addProperty("minLength", 1);
        schema.addProperty("description", description);
        return schema;
    }

    private static JsonObject integer(int min, int max, String description) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "integer");
        schema.addProperty("minimum", min);
        schema.addProperty("maximum", max);
        schema.addProperty("description", description);
        return schema;
    }

    private static JsonObject tool(String name, String description, Object... fields) {
        JsonObject properties = new JsonObject();
        JsonArray required = new JsonArray();
        for (int i = 0; i < fields.length; i += 2) {
            properties.add((String) fields[i], (JsonObject) fields[i + 1]);
            required.add((String) fields[i]);
        }
        JsonObject parameters = new JsonObject();
        parameters.addProperty("type", "object");
        parameters.add("properties", properties);
        parameters.add("required", required);
        parameters.addProperty("additionalProperties", false);
        JsonObject function = new JsonObject();
        function.addProperty("name", name);
        function.addProperty("description", description);
        function.add("parameters", parameters);
        JsonObject tool = new JsonObject();
        tool.addProperty("type", "function");
        tool.add("function", function);
        return tool;
    }
}
