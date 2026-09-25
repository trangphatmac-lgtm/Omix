package cn.omix.util.sigma;

import net.minecraft.item.ItemStack;
import net.minecraft.item.FuelRegistry;

/** Private copies only: never mutate the inventory owned by a packet or screen handler. */
public final class SigmaFurnaceTracker {
    public final int syncId;
    private final ItemStack[] slots = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    private int fuel, fuelTotal, cook, cookTotal = 200;
    private ItemStack lastResult = ItemStack.EMPTY;

    public SigmaFurnaceTracker(int syncId) { this.syncId = syncId; }

    public void slot(int index, ItemStack stack) {
        if (index < 0 || index >= 3) return;
        if (index == 0 && !ItemStack.areItemsAndComponentsEqual(slots[0], stack)) lastResult = ItemStack.EMPTY;
        slots[index] = stack.copy();
        if (index == 2 && !stack.isEmpty()) lastResult = stack.copyWithCount(1);
    }

    public void recipe(SigmaFurnaceRecipes recipes) {
        ItemStack result = recipes.result(slots[0]);
        if (!result.isEmpty() && (lastResult.isEmpty() || slots[2].isEmpty())) lastResult = result;
    }

    public void property(int index, int value) {
        value = Math.max(0, value);
        switch (index) {
            case 0 -> fuel = value;
            case 1 -> fuelTotal = value;
            case 2 -> cook = value;
            case 3 -> cookTotal = value;
        }
    }

    public void tick(boolean screenOpen, FuelRegistry fuels) {
        if (screenOpen) return; // The server remains authoritative while the container is open.
        boolean canSmelt = !slots[0].isEmpty() && !lastResult.isEmpty()
                && (slots[2].isEmpty() || ItemStack.areItemsAndComponentsEqual(slots[2], lastResult))
                && slots[2].getCount() + lastResult.getCount() <= lastResult.getMaxCount();
        if (fuel > 0) fuel--;
        else if (canSmelt && fuels.isFuel(slots[1])) {
            fuelTotal = fuels.getFuelTicks(slots[1]); fuel = fuelTotal;
            ItemStack remainder = slots[1].getItem().getRecipeRemainder();
            slots[1].decrement(1); if (slots[1].isEmpty()) slots[1] = remainder.copy();
        }
        if (fuel > 0 && canSmelt && cookTotal > 0) {
            if (++cook >= cookTotal) {
                cook = 0;
                slots[0].decrement(1);
                if (slots[2].isEmpty()) slots[2] = lastResult.copy();
                else slots[2].increment(lastResult.getCount());
            }
        } else cook = Math.max(0, cook - 2);
    }

    public ItemStack output() { return slots[2].isEmpty() && !slots[0].isEmpty() ? lastResult : slots[2]; }
    public int outputCount() { return slots[2].getCount(); }
    public float fuelProgress() { return fuelTotal <= 0 ? 0 : Math.clamp((float) fuel / fuelTotal, 0, 1); }
    public float cookProgress() { return cookTotal <= 0 ? 0 : Math.clamp((float) cook / cookTotal, 0, 1); }
}
