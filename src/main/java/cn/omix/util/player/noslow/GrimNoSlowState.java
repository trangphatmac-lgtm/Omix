package cn.omix.util.player.noslow;

public final class GrimNoSlowState {
    public enum State { NONE, PREPARING, USING, WAIT_RELEASE_BOUNDARY,
        WAIT_STOP_MOVEMENT, FINISH_AFTER_MOVEMENT }
    public enum Hand { MAIN_HAND, OFF_HAND }
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
    Hand useHand;
    boolean buffering;                              // g
    boolean resetting;                              // S
    volatile boolean suspended;                     // t
    int lockedSlot = -1;                             // h
    int waitStartAge = -1;                           // A
    int inventoryClickAge = Integer.MIN_VALUE;       // r = (int)e
    final Host host;

    public GrimNoSlowState(Host host) { this.host = host; }

    // 生命周期和外部开关
    public void onEnable() { suspended = false; discardState(); }
    public void onDisable() { suspended = false; resetState(); }
    public void onWorldEvent() {
        suspended = false;
        inventoryClickAge = Integer.MIN_VALUE;
        resetState();
    }
    public boolean isSuspended() { return suspended; }
    public void setSuspended(boolean value) {
        if (suspended == value) return;
        suspended = value;
        if (value) resetState();
    }

    public void onClientTick(boolean pre) {
        if (!pre) return;
        if (!host.playerPresent()) { discardState(); return; }
        if (state != State.NONE) claimLockedSlot();
        if (blocked()) {
            if (state != State.NONE) resetState();
            return;
        }
        interactionBlocked = false;
        if (state == State.PREPARING) { abandonPreparation(); return; }
        if (state == State.FINISH_AFTER_MOVEMENT) { resetState(); return; }
        if (state == State.USING && !host.isUsingItem()) {
            activeNoSlow = false;
            state = State.WAIT_STOP_MOVEMENT;
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
            if (state != State.NONE) resetState();
            return;
        }
        if (!host.playerPresent() || state == State.NONE) return;
        if (host.playerUpdateBlocked() || host.mainHand().enderPearl())
            resetState();
    }

    public void onSlowdown() {
        if (blocked() || !host.playerPresent()) return;
        if (state == State.USING && activeNoSlow && !interactionBlocked && eligible(host.activeItem()))
            host.disableSlowdown();
    }

    public void onUseItem(Hand hand, Item stack) {
        if (blocked() || !host.playerPresent() || !host.worldPresent()) return;
        if (waiting() || state == State.FINISH_AFTER_MOVEMENT) {
            // Keep the physical use key held, but replay the previous use's
            // acknowledgements and inventory before vanilla starts the next item.
            host.cancelEvent();
            return;
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
            resetState(); return;
        }
        if (blocked()) {
            if (state != State.NONE) resetState();
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
        if (packet.type() == PacketType.USE_ITEM) {
            if (state != State.NONE && state != State.PREPARING) host.cancelEvent();
            else interceptUsePacket(packet);
        }
    }

    void interceptUsePacket(Packet packet) {
        if (!host.playerPresent()) return;
        Item stack = packet.hand() == Hand.MAIN_HAND ? host.mainHand() : host.offHand();
        if (chargedCrossbow(stack) || !eligible(stack) || bothHandsConsumable()) {
            if (state == State.PREPARING) abandonPreparation();
            return;
        }
        // Use the same metadata/transaction boundary as bows. Swapping first makes
        // Grim's SET_SLOT/WINDOW_ITEMS callbacks clear the real server use when
        // the leading transaction is acknowledged (PacketEntityReplication).
        // Preserve the original packet, including hand, sequence and rotation.
        enterUsing(packet.hand());
    }

    private void abandonPreparation() {
        discardState();
    }

    void prepare() {
        if (state != State.NONE) return;
        captureSlot();
        state = State.PREPARING;
    }
    void enterUsing(Hand hand) {
        captureSlot();
        useHand = hand;
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
        return state == State.WAIT_RELEASE_BOUNDARY || state == State.WAIT_STOP_MOVEMENT;
    }
    void advanceMovementBoundary() {
        if (waiting()) state = State.FINISH_AFTER_MOVEMENT;
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
        useHand = null;
        activeNoSlow = interactionBlocked = buffering = false;
    }
    void discardState() {
        clearFieldsAndReleaseSlot();
        host.clearReceiveQueue(this);
    }
    void resetState() {
        if (resetting) return;
        resetting = true;
        try {
            clearFieldsAndReleaseSlot();
            // Every authoritative slot update must replay, including updates that
            // arrive between the final movement boundary and this tick.
            host.flushReceiveQueue(this);
            host.clearReceiveQueue(this);
        } finally {
            resetting = false;
        }
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

    /** Finish replay before allowing another interaction to change the inventory. */
    public void abortActive() {
        if (state != State.NONE) resetState();
    }

    /** Forget state and the inventory-click guard after the host has cleared its old session. */
    public void discardSession() {
        discardState();
        inventoryClickAge = Integer.MIN_VALUE;
    }

    public boolean isBuffering() {
        return buffering;
    }

    /** Original NoFall guard includes recovery/preparation phases, not just slowdown removal. */
    public boolean isActivePhase() {
        return state != State.NONE || activeNoSlow;
    }

    public Hand getUseHand() {
        return useHand;
    }

    public boolean shouldLockHotbar() {
        return state != State.NONE && lockedSlot >= 0;
    }

    public boolean shouldBlockUseAfterInventoryClick() {
        return host.playerPresent() && !host.isUsingItem() && inventoryClickAge == host.age()
            && (eligible(host.mainHand()) || eligible(host.offHand()));
    }

    public interface Host {
        boolean playerPresent();
        boolean worldPresent();
        int age();
        int playerId();
        int selectedSlot();
        boolean isUsingItem();
        Item mainHand();
        Item offHand();
        Item activeItem();
        Hit crosshair();                  // 可以为 null
        double fallDistance();
        boolean blocked();
        boolean playerUpdateBlocked();
        boolean entityIsEnderPearl(int id);
        boolean requestSlot(Object owner, int slot); // CRITICAL 优先级，返回申请是否成功
        void releaseSlot(Object owner);
        void clearReceiveQueue(Object owner);
        void flushReceiveQueue(Object owner);
        void queueIncoming(Object owner);
        boolean useCopy(Item item, Hand hand); // item.copy().use(world, player, hand).isAccepted()
        void cancelEvent();
        void disableSlowdown();
    }
}
