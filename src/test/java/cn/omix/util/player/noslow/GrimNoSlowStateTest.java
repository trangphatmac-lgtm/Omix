package cn.omix.util.player.noslow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static cn.omix.util.player.noslow.GrimNoSlowState.*;
import static org.junit.jupiter.api.Assertions.*;

class GrimNoSlowStateTest {
    @Test
    void noFallGuardCoversEveryActivePhaseAndTheIndependentFlag() {
        for (State phase : State.values()) {
            flow.state = phase;
            flow.activeNoSlow = false;
            assertEquals(phase != State.NONE, flow.isActivePhase(), phase.name());
            flow.activeNoSlow = true;
            assertTrue(flow.isActivePhase(), phase.name());
        }
        flow.discardState();
        assertFalse(flow.isActivePhase());
    }

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
        host.activeHand = hand;
        host.using = true;
    }

    @Test
    void mainHandFoodKeepsItsOriginalUseAndInventory() {
        start(Hand.MAIN_HAND);
        assertEquals(State.USING, flow.state);
        assertEquals(Hand.MAIN_HAND, flow.getUseHand());
        assertTrue(flow.shouldLockHotbar());
        assertTrue(flow.buffering);
        assertEquals(3, flow.lockedSlot);
        assertEquals(List.of("slot:3", "clear"), host.effects);
        flow.onSlowdown();
        assertTrue(host.noSlow);
        assertEquals(FOOD, host.main);
    }

    @Test
    void offhandFoodAndPotionKeepTheirOriginalUseEvenWithABowInMainHand() {
        host.main = BOW;
        for (Item consumable : List.of(FOOD, POTION)) {
            flow.discardState();
            host.effects.clear();
            host.noSlow = false;
            host.off = consumable;
            start(Hand.OFF_HAND);
            assertEquals(Hand.OFF_HAND, flow.getUseHand());
            assertFalse(host.effects.contains("cancel"));
            flow.onSlowdown();
            assertTrue(host.noSlow);
            assertEquals(BOW, host.main);
            assertEquals(consumable, host.off);
        }
    }

    @Test
    void offhandFoodStillWorksWithEmptyMainHand() {
        host.main = EMPTY;
        host.off = FOOD;
        start(Hand.OFF_HAND);
        assertEquals(Hand.OFF_HAND, flow.getUseHand());
        assertFalse(host.effects.contains("cancel"));
    }

    @Test
    void bowAndUnchargedCrossbowKeepOriginalUsePacket() {
        for (Item weapon : List.of(BOW, CROSSBOW)) {
            host.main = weapon;
            flow.discardState();
            host.effects.clear();
            start(Hand.MAIN_HAND);
            assertEquals(State.USING, flow.state);
            assertEquals(Hand.MAIN_HAND, flow.getUseHand());
            assertFalse(host.effects.contains("cancel"));
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
            assertNull(flow.getUseHand());
        }
    }

    @Test
    void releaseStopsNoSlowAndFlushesAfterOneMovementBoundary() {
        start(Hand.MAIN_HAND);
        host.effects.clear();
        flow.onPacket(Direction.SEND, packet(PacketType.RELEASE_USE));
        assertEquals(State.WAIT_RELEASE_BOUNDARY, flow.state);
        assertFalse(flow.activeNoSlow);
        flow.onPacket(Direction.SEND, packet(PacketType.MOVE));
        assertEquals(State.FINISH_AFTER_MOVEMENT, flow.state);
        assertTrue(host.effects.isEmpty());
        flow.onClientTick(true);
        assertEquals(State.NONE, flow.state);
        assertEquals(1, host.effects.stream().filter("flush"::equals).count());
        assertFalse(flow.shouldLockHotbar());
    }

    @Test
    void naturalCompletionPreservesHeldUseKeyAndNeedsOneBoundary() {
        start(Hand.MAIN_HAND);
        host.using = false;
        flow.onClientTick(true);
        assertEquals(State.WAIT_STOP_MOVEMENT, flow.state);
        assertTrue(host.useKeyPressed);
        flow.onPacket(Direction.SEND, packet(PacketType.MOVE));
        flow.onClientTick(true);
        assertEquals(State.NONE, flow.state);
    }

    @Test
    void stationaryReleaseTimeoutIsStrictlyGreaterThanTwo() {
        start(Hand.MAIN_HAND);
        flow.onPacket(Direction.SEND, packet(PacketType.RELEASE_USE));
        host.age = 20; flow.onClientTick(true);
        host.age = 22; flow.onClientTick(true);
        assertEquals(State.WAIT_RELEASE_BOUNDARY, flow.state);
        assertEquals(20, flow.waitStartAge);
        host.age = 23; flow.onClientTick(true);
        assertEquals(State.FINISH_AFTER_MOVEMENT, flow.state);
        assertEquals(-1, flow.waitStartAge);
    }

    @ParameterizedTest
    @EnumSource(Hand.class)
    void heldUseFinishesOneAppleBeforeStartingTheNextInTheSameHand(Hand hand) {
        host.main = hand == Hand.MAIN_HAND ? FOOD : BOW;
        host.off = hand == Hand.OFF_HAND ? FOOD : EMPTY;
        start(hand);
        // Vanilla clears local use in response to the server's CONSUME_ITEM status.
        host.using = false;
        flow.onClientTick(true);
        host.effects.clear();
        flow.onUseItem(hand, FOOD);
        assertEquals(List.of("cancel"), host.effects);
        assertEquals(State.WAIT_STOP_MOVEMENT, flow.state);
        assertTrue(host.useKeyPressed);
        flow.onPacket(Direction.SEND, packet(PacketType.MOVE));
        assertEquals(State.FINISH_AFTER_MOVEMENT, flow.state);
        flow.onClientTick(true);
        assertEquals(State.NONE, flow.state);
        host.effects.clear();
        start(hand);
        assertEquals(State.USING, flow.state);
        assertTrue(host.useKeyPressed);
        assertFalse(host.effects.contains("cancel"));
        assertEquals(hand, flow.getUseHand());
    }

    @Test
    void repeatedUseDuringReleaseNeitherFlushesNorDiscardsPendingAcknowledgements() {
        start(Hand.MAIN_HAND);
        flow.onPacket(Direction.SEND, packet(PacketType.RELEASE_USE));
        host.effects.clear();
        for (int i = 0; i < 3; i++) flow.onUseItem(Hand.MAIN_HAND, FOOD);
        assertEquals(List.of("cancel", "cancel", "cancel"), host.effects);
        assertEquals(State.WAIT_RELEASE_BOUNDARY, flow.state);
        assertEquals(Hand.MAIN_HAND, flow.getUseHand());
        assertTrue(flow.buffering);
    }

    @Test
    void bowReleaseNeedsNoSwapAndKeepsNormalInventoryUpdates() {
        host.main = BOW;
        start(Hand.MAIN_HAND);
        flow.onPacket(Direction.SEND, packet(PacketType.RELEASE_USE));
        host.using = false;
        host.effects.clear();
        flow.onPacket(Direction.SEND, packet(PacketType.MOVE));
        flow.onClientTick(true);
        assertEquals(State.NONE, flow.state);
        assertTrue(host.effects.contains("flush"));
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
    void disableAndAbortFlushExactlyOnceBeforeClearingTheQueue() {
        for (boolean disable : List.of(true, false)) {
            start(Hand.MAIN_HAND); host.effects.clear();
            if (disable) flow.onDisable();
            else flow.abortActive();
            assertTrue(host.effects.indexOf("flush") < host.effects.indexOf("clear"));
            assertEquals(1, host.effects.stream().filter("flush"::equals).count());
            assertEquals(State.NONE, flow.state);
            assertNull(flow.getUseHand());
        }
    }

    @Test
    void blockedAndSuspendedSessionsFlushOnceAndStopCancellingSlowdown() {
        start(Hand.MAIN_HAND); host.effects.clear();
        host.blocked = true;
        flow.onClientTick(true);
        flow.onSlowdown();
        assertEquals(State.NONE, flow.state);
        assertFalse(host.noSlow);
        assertEquals(1, host.effects.stream().filter("flush"::equals).count());
        host.blocked = false;
        start(Hand.MAIN_HAND); host.effects.clear();
        flow.setSuspended(true); flow.setSuspended(true);
        assertEquals(1, host.effects.stream().filter("flush"::equals).count());
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

    private static final class FakeHost implements Host {
        final List<String> effects = new ArrayList<>();
        Item main = FOOD, off = EMPTY;
        Hit hit = Hit.MISS;
        int age = 10;
        boolean using, blocked, noSlow;
        boolean useKeyPressed = true;
        Hand activeHand = Hand.MAIN_HAND;
        public boolean playerPresent() { return true; }
        public boolean worldPresent() { return true; }
        public int age() { return age; }
        public int playerId() { return 7; }
        public int selectedSlot() { return 3; }
        public boolean isUsingItem() { return using; }
        public Item mainHand() { return main; }
        public Item offHand() { return off; }
        public Item activeItem() { return activeHand == Hand.MAIN_HAND ? main : off; }
        public Hit crosshair() { return hit; }
        public double fallDistance() { return 0; }
        public boolean blocked() { return blocked; }
        public boolean playerUpdateBlocked() { return false; }
        public boolean entityIsEnderPearl(int id) { return id == 99; }
        public boolean requestSlot(Object owner, int slot) { effects.add("slot:" + slot); return true; }
        public void releaseSlot(Object owner) { effects.add("release"); }
        public void clearReceiveQueue(Object owner) { effects.add("clear"); }
        public void flushReceiveQueue(Object owner) { effects.add("flush"); }
        public void queueIncoming(Object owner) { effects.add("queue"); }
        public boolean useCopy(Item item, Hand hand) { return false; }
        public void cancelEvent() { effects.add("cancel"); }
        public void disableSlowdown() { noSlow = true; }
    }
}
