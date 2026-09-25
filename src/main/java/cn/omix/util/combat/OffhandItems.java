package cn.omix.util.combat;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import static cn.omix.util.combat.OffhandSelection.Kind.*;

public final class OffhandItems {
    private OffhandItems() {
    }

    /** Returns a main-inventory index, or -1 if the offhand needs no replacement. */
    public static int findBestSlot(Inventory inventory, ItemStack offhand) {
        return OffhandSelection.findBestSlot(inventory.size(), slot -> describe(inventory.getStack(slot)), describe(offhand));
    }

    private static OffhandSelection.Item describe(ItemStack stack) {
        FoodComponent food = stack.get(DataComponentTypes.FOOD);
        return new OffhandSelection.Item(kind(stack), food == null ? 0 : food.saturation());
    }

    private static OffhandSelection.Kind kind(ItemStack stack) {
        if (stack.isEmpty()) return NONE;
        if (stack.isOf(Items.MUSHROOM_STEW)) return MUSHROOM_STEW;
        if (stack.isOf(Items.SPLASH_POTION) && isHealingPotion(stack)) return SPLASH_HEALING_POTION;
        if (stack.isOf(Items.ENCHANTED_GOLDEN_APPLE)) return ENCHANTED_GOLDEN_APPLE;
        if (stack.isOf(Items.GOLDEN_APPLE)) return GOLDEN_APPLE;
        if (stack.isOf(Items.POTION) && isHealingPotion(stack)) return HEALING_POTION;
        return stack.contains(DataComponentTypes.FOOD) ? FOOD : NONE;
    }

    private static boolean isHealingPotion(ItemStack stack) {
        PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (contents == null) return false;
        for (var effect : contents.getEffects()) {
            if (effect.getEffectType().equals(StatusEffects.INSTANT_HEALTH)) return true;
        }
        return false;
    }
}
