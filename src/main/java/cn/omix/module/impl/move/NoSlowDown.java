package cn.omix.module.impl.move;

import cn.omix.event.base.annotation.EventPriority;
import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.*;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.module.value.impl.ModeValue;
import cn.omix.module.impl.player.AutoBlockIn;
import cn.omix.module.impl.world.Scaffold;
import cn.omix.util.network.PacketUtil;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.*;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.common.*;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import java.util.ArrayDeque;
import java.util.function.Predicate;

public class NoSlowDown extends Module implements GrimNoSlowHost {
    private final ModeValue mode = new ModeValue("Mode", "Vanilla", "Vanilla", "Grim");
    private final BoolValue keepSprint = new BoolValue("Keep Sprint", true);
    private static final int MAX_BUFFERED_PACKETS = 4096;
    private final GrimNoSlowState flow = new GrimNoSlowState(this);
    private final ArrayDeque<Packet<?>> incoming = new ArrayDeque<>();
    private ClientPlayNetworkHandler connection;
    private ClientWorld world;
    private ClientPlayerEntity player;
    private boolean cancelled;
    private boolean slowdownDisabled;
    private boolean replaying;
    private Packet<?> receiving;

    public NoSlowDown() {
        super("NoSlowDown", Category.Move);
    }

    /** Called by client-thread mixins only; also restores state on a live mode change. */
    public static NoSlowDown activeGrim() {
        if (instance == null || instance.getModuleManager() == null) return null;
        NoSlowDown module = instance.getModuleManager().getModule(NoSlowDown.class);
        if (module == null || !module.isEnabled()) return null;
        if (module.mode.is("Grim")) return module;
        module.reset();
        return null;
    }

    public boolean allowGrimSprint() {
        return isEnabled() && mode.is("Grim") && keepSprint.getValue() && cancelSlowdown();
    }

    @Override
    public void onEnable() {
        discard();
        setSuffix(mode.getValue());
    }

    @Override
    public void onDisable() {
        reset();
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        discard();
    }

    @EventTarget
    @EventPriority(0)
    public void onTick(TickEvent event) {
        setSuffix(mode.getValue());
        if (mode.is("Grim")) tick();
        else reset();
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mode.is("Grim")) update();
    }

    @EventTarget
    @EventPriority(1000)
    public void onPacket(PacketEvent event) {
        if (event.getType() != PacketEvent.Type.Send || !mc.isOnThread() || event.isCancelled()) return;
        if (!mode.is("Grim")) {
            reset();
            return;
        }
        if (outgoing(event.getPacket())) event.setCancelled(true);
    }

    @EventTarget
    public void onScroll(MouseScrollEvent event) {
        if (mc.currentScreen == null && mode.is("Grim") && lockSlot()) event.setCancelled(true);
    }

    @EventTarget
    public void onSlow(SlowEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (mc.player.isUsingItem() && !mc.player.getActiveItem().isEmpty()
                && (mode.is("Vanilla") || cancelSlowdown())) {
            event.setCancelled(true);
            if (keepSprint.getValue()) mc.player.setSprinting(true);
        }
    }

    private boolean context() {
        if (connection != mc.getNetworkHandler() || world != mc.world || player != mc.player) {
            discard();
            connection = mc.getNetworkHandler();
            world = mc.world;
            player = mc.player;
        }
        return connection != null && world != null && player != null;
    }

    private void tick() {
        if (context()) flow.onClientTick(true);
    }

    private void update() {
        if (context()) flow.onPlayerUpdate();
    }

    public void abort() {
        if (context()) flow.abortActive();
    }

    private void reset() {
        if (context()) flow.onDisable();
        else discard();
    }

    /** World changes must never replay old inventory/entity updates into the new world. */
    private void discard() {
        incoming.clear();
        connection = null;
        world = null;
        player = null;
        flow.discardSession();
    }

    public boolean beforeUse(Hand hand) {
        if (!context() || blocked()) return false;
        if (flow.shouldBlockUseAfterInventoryClick()) return true;
        cancelled = false;
        flow.onUseItem(toHand(hand), item(mc.player.getStackInHand(hand)));
        return cancelled;
    }

    public boolean blockUseThisTick() {
        return context() && !blocked() && flow.shouldBlockUseAfterInventoryClick();
    }

    public boolean lockSlot() {
        if (!context() || !flow.shouldLockHotbar()) return false;
        flow.claimLockedSlot();
        return true;
    }

    private boolean cancelSlowdown() {
        if (!context()) return false;
        slowdownDisabled = false;
        flow.onSlowdown();
        return slowdownDisabled;
    }

    private boolean outgoing(Packet<?> packet) {
        if (!context() || replaying) return false;
        cancelled = false;
        // An explicit inventory operation must restore the server hands before it runs.
        if (packet instanceof ClickSlotC2SPacket
                || packet instanceof PlayerActionC2SPacket action
                && action.getAction() != PlayerActionC2SPacket.Action.RELEASE_USE_ITEM) {
            flow.abortActive();
        }
        flow.onPacket(GrimNoSlowState.Direction.SEND, describe(packet));
        return cancelled;
    }

    public boolean receive(Packet<?> packet, PacketListener listener) {
        if (replaying || !context() || listener != connection) return false;
        // Connection/lifecycle control cannot wait for the player to finish eating.
        if (packet.transitionsNetworkState() || packet instanceof DisconnectS2CPacket
                || packet instanceof GameJoinS2CPacket || packet instanceof PlayerRespawnS2CPacket) {
            discard();
            return false;
        }
        if (packet instanceof KeepAliveS2CPacket || packet instanceof CommonPingS2CPacket) return false;
        if (!flow.isBuffering()) return false;
        if (incoming.size() >= MAX_BUFFERED_PACKETS) {
            flow.onDisable();
            return false;
        }
        cancelled = false;
        receiving = packet;
        try {
            flow.onQueueEvent(GrimNoSlowState.Direction.RECEIVE, describe(packet));
            return cancelled;
        } finally {
            receiving = null;
        }
    }

    private static GrimNoSlowState.Hand toHand(Hand hand) {
        return hand == Hand.MAIN_HAND ? GrimNoSlowState.Hand.MAIN_HAND : GrimNoSlowState.Hand.OFF_HAND;
    }

    private static Hand toHand(GrimNoSlowState.Hand hand) {
        return hand == GrimNoSlowState.Hand.MAIN_HAND ? Hand.MAIN_HAND : Hand.OFF_HAND;
    }

    private static GrimNoSlowState.Item item(ItemStack stack) {
        var type = stack.getItem() instanceof BowItem ? GrimNoSlowState.ItemType.BOW
                : stack.getItem() instanceof CrossbowItem ? GrimNoSlowState.ItemType.CROSSBOW
                : stack.getItem() instanceof PotionItem ? GrimNoSlowState.ItemType.POTION
                : GrimNoSlowState.ItemType.OTHER;
        var action = switch (stack.getUseAction()) {
            case BOW -> GrimNoSlowState.Action.BOW;
            case CROSSBOW -> GrimNoSlowState.Action.CROSSBOW;
            case DRINK -> GrimNoSlowState.Action.DRINK;
            default -> GrimNoSlowState.Action.OTHER;
        };
        return new GrimNoSlowState.Item(stack.isEmpty(), type, action, stack.contains(DataComponentTypes.FOOD),
                CrossbowItem.isCharged(stack), stack.isOf(Items.FIRE_CHARGE), stack.isOf(Items.WATER_BUCKET),
                stack.isOf(Items.ENDER_PEARL));
    }

    private static GrimNoSlowState.Packet describe(Packet<?> packet) {
        var type = GrimNoSlowState.PacketType.OTHER;
        int entityId = -1;
        if (packet instanceof PlayerInteractItemC2SPacket use) {
            return new GrimNoSlowState.Packet(GrimNoSlowState.PacketType.USE_ITEM, toHand(use.getHand()),
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

    @Override public boolean playerPresent() { return mc.player != null; }
    @Override public boolean worldPresent() { return mc.world != null; }
    @Override public int age() { return mc.player.age; }
    @Override public int playerId() { return mc.player.getId(); }
    @Override public int selectedSlot() { return mc.player.getInventory().getSelectedSlot(); }
    @Override public boolean isUsingItem() { return mc.player.isUsingItem(); }
    @Override public GrimNoSlowState.Item mainHand() { return item(mc.player.getMainHandStack()); }
    @Override public GrimNoSlowState.Item offHand() { return item(mc.player.getOffHandStack()); }
    @Override public GrimNoSlowState.Item activeItem() { return item(mc.player.getActiveItem()); }
    @Override public GrimNoSlowState.Hit crosshair() {
        return mc.crosshairTarget == null ? null : switch (mc.crosshairTarget.getType()) {
            case MISS -> GrimNoSlowState.Hit.MISS;
            case BLOCK -> GrimNoSlowState.Hit.BLOCK;
            case ENTITY -> GrimNoSlowState.Hit.ENTITY;
        };
    }
    @Override public boolean targetedEntityPresent() { return mc.targetedEntity != null; }
    @Override public double fallDistance() { return mc.player.fallDistance; }
    @Override public boolean blocked() {
        NoFall noFall = instance.getModuleManager().getModule(NoFall.class);
        return mc.player == null || !mc.player.isAlive() || mc.player.isSpectator() || mc.player.hasVehicle()
                || AutoBlockIn.blocksMouseInput() || playerUpdateBlocked()
                || noFall != null && noFall.isGrimSilentRotationActive();
    }
    @Override public boolean playerUpdateBlocked() {
        Scaffold scaffold = instance.getModuleManager().getModule(Scaffold.class);
        return scaffold != null && scaffold.isEnabled();
    }
    @Override public boolean entityIsEnderPearl(int id) {
        return mc.world != null && mc.world.getEntityById(id) instanceof EnderPearlEntity;
    }
    @Override public boolean requestSlot(Object owner, int slot) {
        if (player != null && player == mc.player && world == mc.world) {
            player.getInventory().setSelectedSlot(slot);
            return true;
        }
        return false;
    }
    @Override public void releaseSlot(Object owner) { /* Vanilla slot sync resumes when flow clears its lock. */ }
    @Override public void clearReceiveQueue(Object owner) {
        // The original clears an owner in a shared queue. Release our sole ownership
        // by replaying valid packets; never silently lose updates during consecutive uses.
        flushReceiveQueue(owner, packet -> true);
    }
    @Override public void flushReceiveQueue(Object owner, Predicate<GrimNoSlowState.Packet> predicate) {
        replaying = true;
        try {
            while (!incoming.isEmpty()) {
                Packet<?> packet = incoming.removeFirst();
                if (connection == null || connection != mc.getNetworkHandler() || world != mc.world || player != mc.player) {
                    incoming.clear();
                    break;
                }
                if (predicate.test(describe(packet))) PacketUtil.receivePacket(packet);
            }
        } finally {
            replaying = false;
        }
    }
    @Override public void queueIncoming(Object owner) { incoming.addLast(receiving); cancelled = true; }
    @Override public void sendSwap() {
        if (connection != null && connection == mc.getNetworkHandler() && world == mc.world && player == mc.player) {
            send(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
        }
    }
    @Override public void sendUseItem(GrimNoSlowState.Hand hand, int sequence, float yaw, float pitch) {
        send(new PlayerInteractItemC2SPacket(toHand(hand), sequence, yaw, pitch));
    }
    private void send(Packet<?> packet) {
        PacketUtil.runWithoutEvents(() -> { PacketUtil.sendPacket(packet); return null; });
    }
    @Override public boolean useCopy(GrimNoSlowState.Item ignored, GrimNoSlowState.Hand hand) {
        return mc.player.getStackInHand(toHand(hand)).copy().use(mc.world, mc.player, toHand(hand)).isAccepted();
    }
    @Override public void cancelEvent() { cancelled = true; }
    @Override public void disableSlowdown() { slowdownDisabled = true; }
    @Override public void setUseKeyPressed(boolean pressed) { mc.options.useKey.setPressed(pressed); }

    /**
     * Seven-phase NoSlow flow adapted from the supplied EdNaven 3.4 reverse-engineering
     * model. All calls are confined to the client thread; Minecraft integration is in
     * NoSlowDown. State is per module instance, never shared between sessions.
     */
    static final class GrimNoSlowState {
        public enum State { NONE, PREPARING, USING, WAIT_RELEASE_BOUNDARY,
            WAIT_RESTORE_MOVEMENT, WAIT_STOP_MOVEMENT, FINISH_AFTER_MOVEMENT }
        public enum Hand { MAIN_HAND, OFF_HAND;
            Hand opposite() { return this == MAIN_HAND ? OFF_HAND : MAIN_HAND; }
        }
        public enum Action { OTHER, BOW, CROSSBOW, DRINK }
        public enum ItemType { OTHER, BOW, CROSSBOW, POTION }
        public enum Hit { MISS, BLOCK, ENTITY }
        public enum Direction { SEND, RECEIVE }
        public enum PacketType { OTHER, MOVE, USE_ITEM, USE_BLOCK, SELECT_SLOT, CLICK_SLOT,
            RELEASE_USE, ENTITY_RELATIVE, ENTITY_POSITION_SYNC, ENTITY_POSITION,
            ENTITY_STATUS, ENTITY_TRACKER, ENTITY_VELOCITY, SET_PLAYER_INVENTORY, SET_SCREEN_SLOT }
        public record Item(boolean empty, ItemType type, Action action, boolean food,
                           boolean charged, boolean fireCharge, boolean waterBucket,
                           boolean enderPearl) { }
        public record Packet(PacketType type, Hand hand, int sequence, float yaw,
                             float pitch, int entityId) { }


        State state = State.NONE;
        boolean activeNoSlow;
        boolean interactionBlocked;                      // L
        boolean handsSwapped;                            // I
        boolean releaseKeyWhenUseEnds;                   // C
        boolean buffering;                              // g
        boolean resetting;                              // S
        volatile boolean suspended;                     // t
        int lockedSlot = -1;                             // h
        int waitStartAge = -1;                           // A
        int inventoryClickAge = Integer.MIN_VALUE;       // r = (int)e
        final GrimNoSlowHost host;

        public GrimNoSlowState(GrimNoSlowHost host) { this.host = host; }

        // 生命周期和外部开关
        public void onEnable() { suspended = false; discardState(); }
        public void onDisable() { suspended = false; resetState(true, false); }
        public void onWorldEvent() {
            suspended = false;
            inventoryClickAge = Integer.MIN_VALUE;
            resetState(true, false);
        }
        public boolean isSuspended() { return suspended; }
        public void setSuspended(boolean value) {
            if (suspended == value) return;
            suspended = value;
            if (value) resetState(true, false);
        }

        public void onClientTick(boolean pre) {
            if (!pre) return;
            if (!host.playerPresent()) { discardState(); return; }
            if (state != State.NONE) claimLockedSlot();
            if (blocked()) {
                if (state != State.NONE) resetState(true, false);
                return;
            }
            interactionBlocked = false;
            if (state == State.PREPARING) { abandonPreparation(); return; }
            if (state == State.FINISH_AFTER_MOVEMENT) { resetState(false, false); return; }
            if (state == State.USING && !host.isUsingItem()) {
                activeNoSlow = false;
                if (releaseKeyWhenUseEnds) host.setUseKeyPressed(false);
                state = handsSwapped ? State.WAIT_RESTORE_MOVEMENT : State.WAIT_STOP_MOVEMENT;
            }
            if (waiting()) {
                if (waitStartAge < 0) waitStartAge = host.age();
                else if (host.age() - waitStartAge > 2) {
                    waitStartAge = -1;
                    advanceMovementBoundary();
                }
            }
        }

        public void onPlayerUpdate() {
            if (state != State.NONE) claimLockedSlot();
            if (blocked()) {
                if (state != State.NONE) resetState(true, false);
                return;
            }
            if (!host.playerPresent() || state == State.NONE) return;
            if (host.playerUpdateBlocked() || host.mainHand().enderPearl())
                resetState(true, false);
        }

        public void onSlowdown() {
            if (blocked() || !host.playerPresent()) return;
            if (state == State.USING && activeNoSlow && !interactionBlocked && eligible(host.activeItem()))
                host.disableSlowdown();
        }

        public void onUseItem(Hand hand, Item stack) {
            if (blocked() || !host.playerPresent() || !host.worldPresent()) return;
            if (waiting() || state == State.FINISH_AFTER_MOVEMENT) {
                // 连续使用：仅当仍处于换手状态且主手是消耗品时继承交换状态。
                boolean keepSwap = handsSwapped && consumable(host.mainHand()) && eligible(stack)
                        && !chargedCrossbow(stack) && !bothHandsConsumable()
                        && (host.crosshair() == null || host.crosshair() == Hit.MISS);
                boolean wasSwapped = handsSwapped;
                discardState();
                if (keepSwap) handsSwapped = true;
                else if (wasSwapped) host.sendSwap();
            }
            if (state == State.NONE || state == State.PREPARING) {
                if (hand == Hand.MAIN_HAND && !eligible(stack) && eligible(host.offHand())) {
                    if (stack.fireCharge()) return;
                    if (!host.useCopy(stack, hand)) host.cancelEvent();
                    return;
                }
                if (eligible(stack)) prepare();
            } else if (state == State.USING) {
                host.cancelEvent();
            }
        }

        public void onPacket(Direction direction, Packet packet) {
            if (direction == Direction.SEND && host.playerPresent()) {
                if (packet.type() == PacketType.CLICK_SLOT) inventoryClickAge = host.age();
            }
            if (!host.playerPresent() || resetting) return;
            if (direction == Direction.SEND && state != State.NONE && packet.type() == PacketType.SELECT_SLOT) {
                claimLockedSlot(); host.cancelEvent(); return;
            }
            if (direction == Direction.SEND && state != State.NONE && packet.type() == PacketType.USE_BLOCK) {
                resetState(true, false); return;
            }
            if (blocked()) {
                if (state != State.NONE) resetState(true, false);
                return;
            }
            if (direction == Direction.RECEIVE) return;
            if (packet.type() == PacketType.MOVE) { advanceMovementBoundary(); return; }
            if (packet.type() == PacketType.RELEASE_USE && state == State.USING) {
                activeNoSlow = false;
                state = State.WAIT_RELEASE_BOUNDARY;
                return;
            }
            if (host.crosshair() != null && host.crosshair() != Hit.MISS
                    && (packet.type() == PacketType.USE_ITEM || packet.type() == PacketType.USE_BLOCK)) {
                interactionBlocked = true;
                if (state == State.PREPARING) abandonPreparation();
                return;
            }
            if (interactionBlocked) return;
            if (packet.type() == PacketType.USE_ITEM) interceptUsePacket(packet);
        }

        void interceptUsePacket(Packet packet) {
            if (!host.playerPresent()) return;
            Item stack = packet.hand() == Hand.MAIN_HAND ? host.mainHand() : host.offHand();
            if (chargedCrossbow(stack) || !eligible(stack) || bothHandsConsumable()) {
                if (state == State.PREPARING) abandonPreparation();
                return;
            }
            if (host.targetedEntityPresent() || stack.action() == Action.BOW || stack.action() == Action.CROSSBOW) {
                enterUsing(false); return;
            }
            host.cancelEvent();
            enterUsing(true);
            if (!handsSwapped) { host.sendSwap(); handsSwapped = true; }
            host.sendUseItem(packet.hand().opposite(), packet.sequence(), packet.yaw(), packet.pitch());
        }

        private void abandonPreparation() {
            // A consecutive use can inherit the server swap. If that use is rejected,
            // restore it instead of discarding the only record of the swapped hands.
            if (handsSwapped) resetState(true, false);
            else discardState();
        }

        void prepare() {
            if (state != State.NONE) return;
            captureSlot();
            state = State.PREPARING;
        }
        void enterUsing(boolean releaseKey) {
            captureSlot();
            releaseKeyWhenUseEnds = releaseKey;
            activeNoSlow = true;
            state = State.USING;
            startBuffer();
        }
        void startBuffer() {
            if (buffering) return;
            buffering = true;
            host.clearReceiveQueue(this);
        }
        boolean waiting() {
            return state == State.WAIT_RELEASE_BOUNDARY || state == State.WAIT_RESTORE_MOVEMENT
                || state == State.WAIT_STOP_MOVEMENT;
        }
        void advanceMovementBoundary() {
            if (state == State.WAIT_RELEASE_BOUNDARY) {
                state = handsSwapped ? State.WAIT_RESTORE_MOVEMENT : State.FINISH_AFTER_MOVEMENT;
            } else if (state == State.WAIT_RESTORE_MOVEMENT) {
                handsSwapped = false;
                state = State.FINISH_AFTER_MOVEMENT;
                host.sendSwap();
            } else if (state == State.WAIT_STOP_MOVEMENT) {
                state = State.FINISH_AFTER_MOVEMENT;
            }
        }

        void captureSlot() {
            if (!host.playerPresent() || lockedSlot >= 0) return;
            int slot = host.selectedSlot();
            if (host.requestSlot(this, slot)) lockedSlot = slot;
        }
        public void claimLockedSlot() { if (lockedSlot >= 0) host.requestSlot(this, lockedSlot); }
        void clearFieldsAndReleaseSlot() {
            claimLockedSlot();
            host.releaseSlot(this);
            lockedSlot = waitStartAge = -1;
            state = State.NONE;
            activeNoSlow = interactionBlocked = handsSwapped = releaseKeyWhenUseEnds = buffering = false;
        }
        void discardState() {
            clearFieldsAndReleaseSlot();
            host.clearReceiveQueue(this);
        }
        void resetState(boolean restoreSwap, boolean restoreBeforeFlush) {
            if (resetting) return;
            resetting = true;
            try {
                boolean wasSwapped = handsSwapped;
                clearFieldsAndReleaseSlot();
                if (restoreBeforeFlush) {
                    if (restoreSwap && wasSwapped) host.sendSwap();
                    host.flushReceiveQueue(this, GrimNoSlowState::keepAfterSwap);
                } else {
                    host.flushReceiveQueue(this, p -> true);
                    if (restoreSwap && wasSwapped) host.sendSwap();
                }
                host.clearReceiveQueue(this);
            } finally {
                resetting = false;
            }
        }
        static boolean keepAfterSwap(Packet packet) {
            return packet.type() != PacketType.SET_PLAYER_INVENTORY && packet.type() != PacketType.SET_SCREEN_SLOT;
        }
        public void onQueueEvent(Direction direction, Packet packet) {
            if (direction != Direction.RECEIVE || !host.playerPresent() || !buffering) return;
            if (!passIncoming(packet)) host.queueIncoming(this);
        }
        boolean passIncoming(Packet p) {
            return switch (p.type()) {
                case ENTITY_RELATIVE, ENTITY_POSITION_SYNC, ENTITY_STATUS -> true;
                case ENTITY_POSITION -> !isEnderPearlUpdate(p);
                case ENTITY_TRACKER, ENTITY_VELOCITY -> p.entityId() != host.playerId();
                default -> false;
            };
        }
        boolean isEnderPearlUpdate(Packet p) {
            return host.worldPresent() && host.entityIsEnderPearl(p.entityId());
        }
        static boolean chargedCrossbow(Item s) { return s.type() == ItemType.CROSSBOW && s.charged(); }
        static boolean eligible(Item s) {
            return s != null && !s.empty() && (s.type() == ItemType.BOW || s.type() == ItemType.CROSSBOW
                || (s.type() == ItemType.POTION && s.action() == Action.DRINK) || s.food());
        }
        static boolean consumable(Item s) {
            return s != null && !s.empty() && ((s.type() == ItemType.POTION && s.action() == Action.DRINK) || s.food());
        }
        boolean bothHandsConsumable() { return host.playerPresent() && consumable(host.mainHand()) && consumable(host.offHand()); }
        boolean fallingWithWaterBucket() { return host.playerPresent() && host.mainHand().waterBucket() && host.fallDistance() > 3.0; }
        boolean blocked() {
            return suspended || host.blocked() || fallingWithWaterBucket();
        }

        /** Abort an active use, restoring swapped hands before replay when necessary. */
        public void abortActive() {
            if (state != State.NONE) resetState(true, handsSwapped);
        }

        /** Forget state and the inventory-click guard after the host has cleared its old session. */
        public void discardSession() {
            discardState();
            inventoryClickAge = Integer.MIN_VALUE;
        }

        public boolean isBuffering() {
            return buffering;
        }

        public boolean shouldLockHotbar() {
            return state != State.NONE && lockedSlot >= 0;
        }

        public boolean shouldBlockUseAfterInventoryClick() {
            return host.playerPresent() && !host.isUsingItem() && inventoryClickAge == host.age()
                && (eligible(host.mainHand()) || eligible(host.offHand()));
        }
    }
}

/** Host boundary for the embedded state machine and its isolated tests. */
interface GrimNoSlowHost {
    boolean playerPresent();
    boolean worldPresent();
    int age();
    int playerId();
    int selectedSlot();
    boolean isUsingItem();
    NoSlowDown.GrimNoSlowState.Item mainHand();
    NoSlowDown.GrimNoSlowState.Item offHand();
    NoSlowDown.GrimNoSlowState.Item activeItem();
    NoSlowDown.GrimNoSlowState.Hit crosshair();                  // 可以为 null
    boolean targetedEntityPresent();
    double fallDistance();
    boolean blocked();
    boolean playerUpdateBlocked();
    boolean entityIsEnderPearl(int id);
    boolean requestSlot(Object owner, int slot); // CRITICAL 优先级，返回申请是否成功
    void releaseSlot(Object owner);
    void clearReceiveQueue(Object owner);
    void flushReceiveQueue(Object owner, Predicate<NoSlowDown.GrimNoSlowState.Packet> predicate);
    void queueIncoming(Object owner);
    void sendSwap();                 // SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN
    void sendUseItem(NoSlowDown.GrimNoSlowState.Hand hand, int sequence, float yaw, float pitch);
    boolean useCopy(NoSlowDown.GrimNoSlowState.Item item, NoSlowDown.GrimNoSlowState.Hand hand); // item.copy().use(world, player, hand).isAccepted()
    void cancelEvent();
    void disableSlowdown();
    void setUseKeyPressed(boolean pressed);
}
