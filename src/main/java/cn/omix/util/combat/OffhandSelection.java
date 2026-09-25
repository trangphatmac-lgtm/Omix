package cn.omix.util.combat;

import java.util.function.IntFunction;

/** Inventory selection policy, independent of Minecraft's registry bootstrap. */
public final class OffhandSelection {
    private OffhandSelection() {
    }

    // Ordered from lowest to highest priority.
    public enum Kind { NONE, FOOD, HEALING_POTION, GOLDEN_APPLE, ENCHANTED_GOLDEN_APPLE, SPLASH_HEALING_POTION, MUSHROOM_STEW }

    public record Item(Kind kind, float saturation) {
        private boolean isBetterThan(Item other) {
            if (kind != other.kind) return kind.ordinal() > other.kind.ordinal();
            return kind == Kind.FOOD && saturation > other.saturation;
        }
    }

    /** Returns a main-inventory index, retaining the offhand on ties. */
    public static int findBestSlot(int inventorySize, IntFunction<Item> inventory, Item offhand) {
        Item best = offhand;
        int bestSlot = -1;
        for (int slot = 0; slot < Math.min(36, inventorySize); slot++) {
            Item candidate = inventory.apply(slot);
            if (candidate.isBetterThan(best)) {
                best = candidate;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }
}
