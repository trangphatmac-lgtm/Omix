package cn.remix.module.impl.player;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.TickEvent;
import cn.remix.event.impl.UpdateEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.player.ItemSpoofUtil;
import net.minecraft.block.BlockState;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

public final class AutoTool extends Module {
    private final ModeValue implementation = new ModeValue("Implementation", "Classic", "Classic", "Remix");

    private final NumberValue switchDelay = new NumberValue("Delay", 0, 0, 5, 1, () -> implementation.is("Classic"));
    private final BoolValue switchBack = new BoolValue("Switch Back", true, () -> implementation.is("Classic"));
    private final BoolValue sneakOnly = new BoolValue("Sneak Only", true, () -> implementation.is("Classic"));

    private final ModeValue remixSwitchMode = new ModeValue(
            "Remix Switch Mode",
            "Switch",
            () -> implementation.is("Remix"),
            "Switch",
            "Spoof"
    );

    private int classicCurrentToolSlot = -1;
    private int classicPreviousSlot = -1;
    private int classicTickDelayCounter;

    private boolean remixMining;
    private int remixOldSlot;
    private String activeImplementation = "Classic";

    public AutoTool() {
        super("AutoTool", Category.Player);
    }

    @Override
    public void onEnable() {
        activeImplementation = implementation.getValue();
        if (implementation.is("Remix") && mc.player != null) {
            remixOldSlot = mc.player.getInventory().getSelectedSlot();
            remixMining = false;
        }
    }

    @Override
    public void onDisable() {
        cleanupClassic();
        cleanupRemix();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        syncImplementation();
        setSuffix(implementation.is("Remix") ? "Remix " + remixSwitchMode.getValue() : "Classic");
        if (!implementation.is("Classic")) return;

        if (mc.player == null || mc.world == null) {
            resetClassic();
            return;
        }

        int selectedSlot = mc.player.getInventory().getSelectedSlot();
        if (classicCurrentToolSlot != -1 && classicCurrentToolSlot != selectedSlot) {
            resetClassic();
        }

        if (!isMiningBlock()) {
            restoreClassicSlot();
            resetClassic();
            return;
        }

        if (classicTickDelayCounter >= switchDelay.getValue().intValue()
                && (!sneakOnly.getValue() || mc.options.sneakKey.isPressed())) {
            BlockHitResult blockHit = (BlockHitResult) mc.crosshairTarget;
            BlockState state = mc.world.getBlockState(blockHit.getBlockPos());
            int bestSlot = findClassicBestToolSlot(state, selectedSlot);

            if (bestSlot != selectedSlot) {
                if (classicPreviousSlot == -1) {
                    classicPreviousSlot = selectedSlot;
                }

                mc.player.getInventory().setSelectedSlot(bestSlot);
                classicCurrentToolSlot = bestSlot;
            }
        }

        classicTickDelayCounter++;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        syncImplementation();
        if (!implementation.is("Remix") || mc.player == null || mc.world == null) return;

        setSuffix("Remix " + remixSwitchMode.getValue());

        if (mc.options.attackKey.isPressed()
                && mc.crosshairTarget instanceof BlockHitResult blockHit
                && blockHit.getType() == HitResult.Type.BLOCK) {
            BlockState blockState = mc.world.getBlockState(blockHit.getBlockPos());

            if (blockState.isAir()) {
                resetRemix();
                return;
            }

            int bestSlot = findRemixBestSlot(blockState);
            if (bestSlot == -1) {
                resetRemix();
                return;
            }

            if (!remixMining) {
                remixOldSlot = mc.player.getInventory().getSelectedSlot();
                mc.player.getInventory().setSelectedSlot(bestSlot);
                remixMining = true;

                if (remixSwitchMode.is("Spoof")) {
                    ItemSpoofUtil.startSpoof(remixOldSlot);
                }
            } else if (mc.player.getInventory().getSelectedSlot() != bestSlot) {
                mc.player.getInventory().setSelectedSlot(bestSlot);
            }
        } else {
            resetRemix();
        }
    }

    private void syncImplementation() {
        if (activeImplementation.equalsIgnoreCase(implementation.getValue())) return;

        if (activeImplementation.equalsIgnoreCase("Classic")) {
            cleanupClassic();
        } else {
            cleanupRemix();
        }

        activeImplementation = implementation.getValue();
        if (implementation.is("Remix") && mc.player != null) {
            remixOldSlot = mc.player.getInventory().getSelectedSlot();
        }
    }

    private boolean isMiningBlock() {
        return mc.crosshairTarget instanceof BlockHitResult blockHit
                && blockHit.getType() == HitResult.Type.BLOCK
                && mc.options.attackKey.isPressed()
                && !mc.player.isUsingItem();
    }

    private int findClassicBestToolSlot(BlockState state, int selectedSlot) {
        int bestSlot = selectedSlot;
        float bestSpeed = getClassicBreakingScore(mc.player.getInventory().getStack(selectedSlot), state);

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            float speed = getClassicBreakingScore(stack, state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = slot;
            }
        }

        return bestSlot;
    }

    private float getClassicBreakingScore(ItemStack stack, BlockState state) {
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

    private int findRemixBestSlot(BlockState state) {
        float bestSpeed = 1.0F;
        int bestSlot = -1;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.isEmpty()) continue;

            float speed = stack.getMiningSpeedMultiplier(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = slot;
            }
        }

        return bestSlot;
    }

    private void cleanupClassic() {
        restoreClassicSlot();
        resetClassic();
    }

    private void restoreClassicSlot() {
        if (!switchBack.getValue() || classicPreviousSlot == -1 || mc.player == null) return;

        if (classicCurrentToolSlot == -1
                || mc.player.getInventory().getSelectedSlot() == classicCurrentToolSlot) {
            mc.player.getInventory().setSelectedSlot(classicPreviousSlot);
        }
    }

    private void resetClassic() {
        classicCurrentToolSlot = -1;
        classicPreviousSlot = -1;
        classicTickDelayCounter = 0;
    }

    private void cleanupRemix() {
        if (remixMining && remixSwitchMode.is("Spoof")) {
            ItemSpoofUtil.stopSpoof();
        }

        if (remixMining && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(remixOldSlot);
        }
        remixMining = false;
    }

    private void resetRemix() {
        if (remixMining) {
            cleanupRemix();
        }
    }
}
