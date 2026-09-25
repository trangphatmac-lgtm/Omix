package cn.omix.util.combat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static cn.omix.util.combat.OffhandSelection.Kind.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OffhandSelectionTest {
    private static final OffhandSelection.Item EMPTY = item(NONE);

    @Test
    void selectsEveryPriorityTierRegardlessOfInventoryOrder() {
        List<OffhandSelection.Item> ascending = List.of(
                new OffhandSelection.Item(FOOD, 20), item(HEALING_POTION), item(GOLDEN_APPLE),
                item(ENCHANTED_GOLDEN_APPLE), item(SPLASH_HEALING_POTION), item(MUSHROOM_STEW));
        for (int size = 1; size <= ascending.size(); size++) {
            List<OffhandSelection.Item> inventory = new ArrayList<>(ascending.subList(0, size));
            assertEquals(size - 1, select(inventory, EMPTY));
            assertEquals(-1, select(inventory, ascending.get(size - 1)));
            Collections.reverse(inventory);
            assertEquals(0, select(inventory, EMPTY));
        }
    }

    @Test
    void foodUsesHighestSaturationAndUpgradesExistingFood() {
        var low = new OffhandSelection.Item(FOOD, 1);
        var high = new OffhandSelection.Item(FOOD, 8);
        assertEquals(1, select(List.of(low, high), low));
        assertEquals(-1, select(List.of(low, high), high));
    }

    @Test
    void equalSaturationKeepsOffhandOrFirstInventoryCandidate() {
        var food = new OffhandSelection.Item(FOOD, 12.8f);
        assertEquals(0, select(List.of(food, food), EMPTY));
        assertEquals(-1, select(List.of(food, food), food));
    }

    @Test
    void noCandidatesLeaveOffhandUnchanged() {
        assertEquals(-1, select(List.of(), EMPTY));
        assertEquals(-1, select(List.of(EMPTY, EMPTY), EMPTY));
        assertEquals(-1, select(List.of(EMPTY, EMPTY), item(GOLDEN_APPLE)));
    }

    @Test
    void scansHotbarAndLastMainSlotButExcludesEquipmentSlots() {
        var inventory = new ArrayList<>(Collections.nCopies(41, EMPTY));
        inventory.set(40, item(MUSHROOM_STEW));
        inventory.set(36, item(MUSHROOM_STEW));
        inventory.set(35, item(GOLDEN_APPLE));
        inventory.set(8, item(FOOD));
        assertEquals(35, select(inventory, EMPTY));
        inventory.set(35, EMPTY);
        assertEquals(8, select(inventory, EMPTY));
        inventory.set(8, EMPTY);
        assertEquals(-1, select(inventory, EMPTY));
    }

    @Test
    void consumedOffhandIsReplenishedButHigherPriorityOffhandIsKept() {
        var inventory = List.of(item(GOLDEN_APPLE));
        assertEquals(0, select(inventory, EMPTY));
        assertEquals(-1, select(inventory, item(MUSHROOM_STEW)));
    }

    private static OffhandSelection.Item item(OffhandSelection.Kind kind) {
        return new OffhandSelection.Item(kind, 0);
    }

    private static int select(List<OffhandSelection.Item> inventory, OffhandSelection.Item offhand) {
        return OffhandSelection.findBestSlot(inventory.size(), inventory::get, offhand);
    }
}
