package cn.remix.module.impl.player;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.TickEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.NumberValue;
import net.minecraft.block.BlockState;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

public final class AutoTool extends Module {
    private final NumberValue switchDelay = new NumberValue("Delay", 0, 0, 5, 1);
    private final BoolValue switchBack = new BoolValue("Switch Back", true);
    private final BoolValue sneakOnly = new BoolValue("Sneak Only", true);

    private int currentToolSlot = -1;
    private int previousSlot = -1;
    private int tickDelayCounter;

    public AutoTool() {
        super("AutoTool", Category.Player);
    }

    @Override
    public void onDisable() {
        restorePreviousSlot();
        reset();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) {
            reset();
            return;
        }

        int selectedSlot = mc.player.getInventory().getSelectedSlot();
        if (currentToolSlot != -1 && currentToolSlot != selectedSlot) {
            reset();
        }

        if (!isMiningBlock()) {
            restorePreviousSlot();
            reset();
            return;
        }

        if (tickDelayCounter >= switchDelay.getValue().intValue()
                && (!sneakOnly.getValue() || mc.options.sneakKey.isPressed())) {
            BlockHitResult blockHit = (BlockHitResult) mc.crosshairTarget;
            BlockState state = mc.world.getBlockState(blockHit.getBlockPos());
            int bestSlot = findBestToolSlot(state, selectedSlot);

            if (bestSlot != selectedSlot) {
                if (previousSlot == -1) {
                    previousSlot = selectedSlot;
                }

                mc.player.getInventory().setSelectedSlot(bestSlot);
                currentToolSlot = bestSlot;
            }
        }

        tickDelayCounter++;
    }

    private boolean isMiningBlock() {
        return mc.crosshairTarget instanceof BlockHitResult blockHit
                && blockHit.getType() == HitResult.Type.BLOCK
                && mc.options.attackKey.isPressed()
                && !mc.player.isUsingItem();
    }

    private int findBestToolSlot(BlockState state, int selectedSlot) {
        int bestSlot = selectedSlot;
        float bestSpeed = getBreakingScore(mc.player.getInventory().getStack(selectedSlot), state);

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            float speed = getBreakingScore(stack, state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = slot;
            }
        }

        return bestSlot;
    }

    private float getBreakingScore(ItemStack stack, BlockState state) {
        if (stack.isEmpty()) {
            return state.isToolRequired() ? 0.01F : 1.0F / 30.0F;
        }

        float speed = stack.getMiningSpeedMultiplier(state);
        if (speed > 1.0F) {
            int efficiency = getEfficiencyLevel(stack);
            if (efficiency > 0) {
                speed += efficiency * efficiency + 1.0F;
            }
        }

        boolean canHarvest = !state.isToolRequired() || stack.isSuitableFor(state);
        return speed / (canHarvest ? 30.0F : 100.0F);
    }

    private int getEfficiencyLevel(ItemStack stack) {
        ItemEnchantmentsComponent enchantments = stack.getEnchantments();
        for (RegistryEntry<Enchantment> enchantment : enchantments.getEnchantments()) {
            if (enchantment.matchesKey(Enchantments.EFFICIENCY)) {
                return enchantments.getLevel(enchantment);
            }
        }
        return 0;
    }

    private void restorePreviousSlot() {
        if (!switchBack.getValue() || previousSlot == -1 || mc.player == null) {
            return;
        }

        if (currentToolSlot == -1 || mc.player.getInventory().getSelectedSlot() == currentToolSlot) {
            mc.player.getInventory().setSelectedSlot(previousSlot);
        }
    }

    private void reset() {
        currentToolSlot = -1;
        previousSlot = -1;
        tickDelayCounter = 0;
    }
}
