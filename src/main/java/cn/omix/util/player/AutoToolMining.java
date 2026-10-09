package cn.omix.util.player;

import injection.accessor.ClientPlayerInteractionManagerAccessor;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;

/** Explicit mining requests bypass mouse/sneak conditions without changing AutoTool settings. */
public final class AutoToolMining {
    private Object owner;
    private ClientPlayerEntity player;
    private ClientWorld world;
    private int originalSlot = -1;
    private int selectedSlot = -1;

    public boolean active() { return owner != null; }

    public boolean select(MinecraftClient mc, Object requester, BlockPos pos) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return false;
        if (active() && owner != requester) return false;
        if (active() && (player != mc.player || world != mc.world
                || mc.player.getInventory().getSelectedSlot() != selectedSlot)) {
            release(mc, requester);
            return false;
        }
        if (!active()) {
            owner = requester;
            player = mc.player;
            world = mc.world;
            originalSlot = player.getInventory().getSelectedSlot();
        }
        selectedSlot = bestSlot(mc.player, mc.world.getBlockState(pos));
        mc.player.getInventory().setSelectedSlot(selectedSlot);
        ((ClientPlayerInteractionManagerAccessor) mc.interactionManager).omix$syncSelectedSlot();
        return true;
    }

    public void release(MinecraftClient mc, Object requester) {
        if (owner != requester) return;
        if (player != null && player == mc.player && world == mc.world
                && player.getInventory().getSelectedSlot() == selectedSlot) {
            player.getInventory().setSelectedSlot(originalSlot);
            if (mc.interactionManager != null)
                ((ClientPlayerInteractionManagerAccessor) mc.interactionManager).omix$syncSelectedSlot();
        }
        clear();
    }

    public void release(MinecraftClient mc) { if (active()) release(mc, owner); }
    public void clear() { owner = null; player = null; world = null; originalSlot = selectedSlot = -1; }

    public static int bestSlot(ClientPlayerEntity player, BlockState state) {
        int best = player.getInventory().getSelectedSlot();
        float bestScore = score(player.getInventory().getStack(best), state);
        for (int slot = 0; slot < 9; slot++) {
            float score = score(player.getInventory().getStack(slot), state);
            if (score > bestScore) { best = slot; bestScore = score; }
        }
        return best;
    }

    private static float score(ItemStack stack, BlockState state) {
        float speed = stack.getMiningSpeedMultiplier(state);
        if (speed > 1) {
            var enchantments = stack.getEnchantments();
            for (var enchantment : enchantments.getEnchantments()) {
                if (enchantment.matchesKey(Enchantments.EFFICIENCY)) {
                    int level = enchantments.getLevel(enchantment);
                    if (level > 0) speed += level * level + 1;
                }
            }
        }
        return speed / (!state.isToolRequired() || stack.isSuitableFor(state) ? 30F : 100F);
    }

    /** Estimate each defense with its prospective tool, without changing the player's held slot. */
    public static float breakingDelta(ClientPlayerEntity player, ClientWorld world, BlockPos pos, boolean autoTool) {
        BlockState state = world.getBlockState(pos);
        float hardness = state.getHardness(world, pos);
        if (hardness < 0) return 0;
        if (hardness == 0 || player.isCreative()) return 1;
        if (!autoTool) return state.calcBlockBreakingDelta(player, world, pos);
        float delta = score(player.getInventory().getStack(bestSlot(player, state)), state) / hardness;
        if (StatusEffectUtil.hasHaste(player)) delta *= 1 + (StatusEffectUtil.getHasteAmplifier(player) + 1) * .2F;
        var fatigue = player.getStatusEffect(StatusEffects.MINING_FATIGUE);
        if (fatigue != null) delta *= switch (fatigue.getAmplifier()) {
            case 0 -> .3F;
            case 1 -> .09F;
            case 2 -> .0027F;
            default -> .00081F;
        };
        delta *= (float) player.getAttributeValue(EntityAttributes.BLOCK_BREAK_SPEED);
        if (player.isSubmergedIn(FluidTags.WATER)) delta *= (float) player.getAttributeValue(EntityAttributes.SUBMERGED_MINING_SPEED);
        if (!player.isOnGround()) delta /= 5;
        return delta;
    }
}
