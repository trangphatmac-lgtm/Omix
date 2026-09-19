package cn.omix.module.impl.move;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import cn.omix.module.impl.move.NoSlowDown.GrimNoSlowState;

import static cn.omix.module.impl.move.NoSlowDown.GrimNoSlowState.*;
import static org.junit.jupiter.api.Assertions.*;

class GrimNoSlowStateTest {
    private static final Item EMPTY = item(true, ItemType.OTHER, Action.OTHER, false, false);
    private static final Item FOOD = item(false, ItemType.OTHER, Action.OTHER, true, false);
    private static final Item BOW = item(false, ItemType.BOW, Action.BOW, false, false);
    private static final Item CROSSBOW = item(false, ItemType.CROSSBOW, Action.CROSSBOW, false, false);
    private static final Item CHARGED = item(false, ItemType.CROSSBOW, Action.CROSSBOW, false, true);
    private static final Item POTION = item(false, ItemType.POTION, Action.DRINK, false, false);
    private final FakeHost host = new FakeHost();
    private final GrimNoSlowState flow = new GrimNoSlowState(host);

    private static Item item(boolean empty, ItemType type, Action action, boolean food, boolean charged) {
        return new Item(empty, type, action, food, charged, false, false, false);
    }

    private static Packet packet(PacketType type) { return new Packet(type, null, 0, 0, 0, -1); }
    private static Packet use(Hand hand) { return new Packet(PacketType.USE_ITEM, hand, 37, 123.5F, -42.25F, -1); }
    private void start(Hand hand) {
        flow.onUseItem(hand, hand == Hand.MAIN_HAND ? host.main : host.off);
        flow.onPacket(Direction.SEND, use(hand));
        host.using = true;
    }

    @Test
    void foodSwapsOnlyServerHandsAndPreservesSequenceAndView() {
        start(Hand.MAIN_HAND);
        assertEquals(State.USING, flow.state);
        assertTrue(flow.handsSwapped);
        assertTrue(flow.shouldLockHotbar());
        assertTrue(flow.buffering);
        assertEquals(3, flow.lockedSlot);
        assertEquals(List.of("slot:3", "cancel", "clear", "swap", "use:OFF_HAND:37:123.5:-42.25"), host.effects);
        flow.onSlowdown();
        assertTrue(host.noSlow);
        assertEquals(FOOD, host.main);
    }

    @Test
    void offhandFoodRewritesToMainHand() {
        host.main = EMPTY;
        host.off = POTION;
        start(Hand.OFF_HAND);
        assertTrue(host.effects.contains("use:MAIN_HAND:37:123.5:-42.25"));
    }

    @Test
    void bowAndUnchargedCrossbowKeepOriginalUsePacket() {
        for (Item weapon : List.of(BOW, CROSSBOW)) {
            host.main = weapon;
            flow.discardState();
            host.effects.clear();
            start(Hand.MAIN_HAND);
            assertEquals(State.USING, flow.state);
            assertFalse(flow.handsSwapped);
            assertFalse(host.effects.contains("cancel"));
            assertFalse(host.effects.contains("swap"));
            assertTrue(flow.buffering);
        }
    }

    @Test
    void chargedCrossbowDualConsumablesAndUnsupportedItemsDoNotEnableNoSlow() {
        for (Item[] hands : List.of(new Item[]{CHARGED, EMPTY}, new Item[]{FOOD, POTION},
                new Item[]{item(false, ItemType.OTHER, Action.OTHER, false, false), EMPTY})) {
            host.main = hands[0]; host.off = hands[1];
            flow.discardState(); host.effects.clear(); host.noSlow = false;
            start(Hand.MAIN_HAND);
            flow.onSlowdown();
            assertEquals(State.NONE, flow.state);
            assertFalse(host.noSlow);
            assertFalse(host.effects.contains("swap"));
        }
    }

    @Test
    void releaseWaitsForTwoMovementBoundariesBeforeFlushing() {
        start(Hand.MAIN_HAND);
        host.effects.clear();
        flow.onPacket(Direction.SEND, packet(PacketType.RELEASE_USE));
        assertEquals(State.WAIT_RELEASE_BOUNDARY, flow.state);
        assertFalse(flow.activeNoSlow);
        flow.onPacket(Direction.SEND, packet(PacketType.MOVE));
        assertEquals(State.WAIT_RESTORE_MOVEMENT, flow.state);
        assertTrue(flow.handsSwapped);
        assertFalse(host.effects.contains("swap"));
        flow.onPacket(Direction.SEND, packet(PacketType.MOVE));
        assertEquals(State.FINISH_AFTER_MOVEMENT, flow.state);
        assertEquals(List.of("swap"), host.effects);
        flow.onClientTick(true);
        assertEquals(State.NONE, flow.state);
        assertTrue(host.effects.indexOf("flush") > host.effects.indexOf("swap"));
        assertFalse(flow.shouldLockHotbar());
    }

    @Test
    void naturalCompletionReleasesKeyAndNeedsOneBoundary() {
        start(Hand.MAIN_HAND);
        host.using = false;
        flow.onClientTick(true);
        assertEquals(State.WAIT_RESTORE_MOVEMENT, flow.state);
        assertTrue(host.effects.contains("key:false"));
        flow.onPacket(Direction.SEND, packet(PacketType.MOVE));
        flow.onClientTick(true);
        assertEquals(State.NONE, flow.state);
    }

    @Test
    void timeoutIsStrictlyGreaterThanTwoAndMovementDoesNotResetAge() {
        start(Hand.MAIN_HAND);
        flow.onPacket(Direction.SEND, packet(PacketType.RELEASE_USE));
        host.age = 20; flow.onClientTick(true);
        host.age = 22; flow.onClientTick(true);
        assertEquals(State.WAIT_RELEASE_BOUNDARY, flow.state);
        flow.onPacket(Direction.SEND, packet(PacketType.MOVE));
        assertEquals(20, flow.waitStartAge);
        host.age = 23; flow.onClientTick(true);
        assertEquals(State.FINISH_AFTER_MOVEMENT, flow.state);
        assertEquals(-1, flow.waitStartAge);
    }

    @Test
    void consecutiveFoodUseInheritsSwapWithoutSwappingTwice() {
        start(Hand.MAIN_HAND);
        flow.onPacket(Direction.SEND, packet(PacketType.RELEASE_USE));
        host.effects.clear();
        start(Hand.MAIN_HAND);
        assertEquals(State.USING, flow.state);
        assertTrue(flow.handsSwapped);
        assertFalse(host.effects.contains("swap"));
        assertTrue(host.effects.contains("use:OFF_HAND:37:123.5:-42.25"));
    }

    @Test
    void abandonedConsecutiveUseRestoresInheritedSwap() {
        start(Hand.MAIN_HAND);
        flow.onPacket(Direction.SEND, packet(PacketType.RELEASE_USE));
        flow.onUseItem(Hand.MAIN_HAND, FOOD);
        assertEquals(State.PREPARING, flow.state);
        assertTrue(flow.handsSwapped);
        host.effects.clear();
        flow.onClientTick(true);
        assertEquals(State.NONE, flow.state);
        assertEquals(1, host.effects.stream().filter("swap"::equals).count());
    }

    @Test
    void rejectedConsecutiveUseCannotLoseServerSwap() {
        start(Hand.MAIN_HAND);
        flow.onPacket(Direction.SEND, packet(PacketType.RELEASE_USE));
        flow.onUseItem(Hand.MAIN_HAND, FOOD);
        host.off = POTION;
        host.effects.clear();
        flow.onPacket(Direction.SEND, use(Hand.MAIN_HAND));
        assertEquals(State.NONE, flow.state);
        assertEquals(1, host.effects.stream().filter("swap"::equals).count());
    }

    @Test
    void consecutiveUseOnBlockRestoresBeforeKeepingVanillaInteraction() {
        start(Hand.MAIN_HAND);
        flow.onPacket(Direction.SEND, packet(PacketType.RELEASE_USE));
        host.hit = Hit.BLOCK;
        host.effects.clear();
        start(Hand.MAIN_HAND);
        assertEquals(State.NONE, flow.state);
        assertFalse(flow.handsSwapped);
        assertEquals(1, host.effects.stream().filter("swap"::equals).count());
    }

    @Test
    void unpreparedUseAndSlotSwitchDoNotEscapeTheActiveLock() {
        start(Hand.MAIN_HAND);
        host.effects.clear();
        flow.onUseItem(Hand.OFF_HAND, EMPTY);
        flow.onPacket(Direction.SEND, packet(PacketType.SELECT_SLOT));
        assertEquals(List.of("cancel", "slot:3", "cancel"), host.effects);
    }

    @Test
    void preparationExpiresAndBlockTargetKeepsVanillaSlowdown() {
        flow.onUseItem(Hand.MAIN_HAND, FOOD);
        assertEquals(State.PREPARING, flow.state);
        flow.onClientTick(false);
        assertEquals(State.PREPARING, flow.state);
        flow.onClientTick(true);
        assertEquals(State.NONE, flow.state);
        host.hit = Hit.BLOCK;
        start(Hand.MAIN_HAND);
        flow.onSlowdown();
        assertEquals(State.NONE, flow.state);
        assertFalse(host.noSlow);
    }

    @Test
    void inventoryClickGuardAppliesOnlyToTheSamePlayerAge() {
        flow.onPacket(Direction.SEND, packet(PacketType.CLICK_SLOT));
        assertTrue(flow.shouldBlockUseAfterInventoryClick());
        host.age++;
        assertFalse(flow.shouldBlockUseAfterInventoryClick());
    }

    @Test
    void incomingWhitelistBuffersOnlyOwnTrackerVelocityAndPearlPosition() {
        start(Hand.MAIN_HAND);
        for (PacketType type : List.of(PacketType.ENTITY_RELATIVE, PacketType.ENTITY_POSITION_SYNC, PacketType.ENTITY_STATUS)) {
            assertTrue(flow.passIncoming(packet(type)));
        }
        for (PacketType type : List.of(PacketType.ENTITY_TRACKER, PacketType.ENTITY_VELOCITY)) {
            assertFalse(flow.passIncoming(new Packet(type, null, 0, 0, 0, 7)));
            assertTrue(flow.passIncoming(new Packet(type, null, 0, 0, 0, 8)));
        }
        assertFalse(flow.passIncoming(new Packet(PacketType.ENTITY_POSITION, null, 0, 0, 0, 99)));
        assertTrue(flow.passIncoming(new Packet(PacketType.ENTITY_POSITION, null, 0, 0, 0, 8)));
        assertFalse(flow.passIncoming(packet(PacketType.SET_PLAYER_INVENTORY)));
        host.effects.clear();
        flow.onQueueEvent(Direction.SEND, packet(PacketType.OTHER));
        assertTrue(host.effects.isEmpty());
        flow.onQueueEvent(Direction.RECEIVE, packet(PacketType.OTHER));
        assertEquals(List.of("queue"), host.effects);
    }

    @Test
    void ordinaryResetFlushesBeforeRestoreAndAbortFiltersStaleSlotUpdates() {
        start(Hand.MAIN_HAND); host.effects.clear();
        flow.onDisable();
        assertTrue(host.effects.indexOf("flush") < host.effects.indexOf("swap"));
        assertTrue(host.filter.test(packet(PacketType.SET_PLAYER_INVENTORY)));
        start(Hand.MAIN_HAND); host.effects.clear();
        flow.resetState(true, true);
        assertTrue(host.effects.indexOf("swap") < host.effects.indexOf("flush"));
        assertFalse(host.filter.test(packet(PacketType.SET_PLAYER_INVENTORY)));
        assertFalse(host.filter.test(packet(PacketType.SET_SCREEN_SLOT)));
        assertTrue(host.filter.test(packet(PacketType.OTHER)));
    }

    @Test
    void blockedAndSuspendedSessionsRestoreOnceAndStopCancellingSlowdown() {
        start(Hand.MAIN_HAND); host.effects.clear();
        host.blocked = true;
        flow.onClientTick(true);
        flow.onSlowdown();
        assertEquals(State.NONE, flow.state);
        assertFalse(host.noSlow);
        assertEquals(1, host.effects.stream().filter("swap"::equals).count());
        host.blocked = false;
        start(Hand.MAIN_HAND); host.effects.clear();
        flow.setSuspended(true); flow.setSuspended(true);
        assertEquals(1, host.effects.stream().filter("swap"::equals).count());
    }

    @Test
    void stateIsNotSharedBetweenModulesOrSessions() {
        start(Hand.MAIN_HAND);
        var other = new GrimNoSlowState(new FakeHost());
        assertEquals(State.NONE, other.state);
        flow.discardState();
        assertEquals(State.NONE, flow.state);
        assertFalse(flow.buffering);
        assertEquals(-1, flow.lockedSlot);
    }

    private static final class FakeHost implements GrimNoSlowHost {
        final List<String> effects = new ArrayList<>();
        Item main = FOOD, off = EMPTY;
        Hit hit = Hit.MISS;
        int age = 10;
        boolean using, blocked, noSlow;
        Predicate<Packet> filter;
        public boolean playerPresent() { return true; }
        public boolean worldPresent() { return true; }
        public int age() { return age; }
        public int playerId() { return 7; }
        public int selectedSlot() { return 3; }
        public boolean isUsingItem() { return using; }
        public Item mainHand() { return main; }
        public Item offHand() { return off; }
        public Item activeItem() { return main; }
        public Hit crosshair() { return hit; }
        public boolean targetedEntityPresent() { return false; }
        public double fallDistance() { return 0; }
        public boolean blocked() { return blocked; }
        public boolean playerUpdateBlocked() { return false; }
        public boolean entityIsEnderPearl(int id) { return id == 99; }
        public boolean requestSlot(Object owner, int slot) { effects.add("slot:" + slot); return true; }
        public void releaseSlot(Object owner) { effects.add("release"); }
        public void clearReceiveQueue(Object owner) { effects.add("clear"); }
        public void flushReceiveQueue(Object owner, Predicate<Packet> filter) { effects.add("flush"); this.filter = filter; }
        public void queueIncoming(Object owner) { effects.add("queue"); }
        public void sendSwap() { effects.add("swap"); }
        public void sendUseItem(Hand hand, int sequence, float yaw, float pitch) {
            effects.add("use:" + hand + ":" + sequence + ":" + yaw + ":" + pitch);
        }
        public boolean useCopy(Item item, Hand hand) { return false; }
        public void cancelEvent() { effects.add("cancel"); }
        public void disableSlowdown() { noSlow = true; }
        public void setUseKeyPressed(boolean pressed) { effects.add("key:" + pressed); }
    }
}
