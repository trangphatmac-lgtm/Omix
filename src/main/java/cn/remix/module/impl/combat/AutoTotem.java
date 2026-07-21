package cn.remix.module.impl.combat;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.UpdateEvent;
import cn.remix.event.impl.WorldEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.util.misc.TimerUtil;
import cn.remix.util.player.ClickSlotUtil;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

public final class AutoTotem extends Module {
    private static final long SWAP_DELAY = 150L;
    private static final int OFFHAND_SWAP_BUTTON = 40;

    private final TimerUtil swapTimer = new TimerUtil();

    public AutoTotem() {
        super("Auto Totem", Category.Combat);
    }

    @Override
    public void onDisable() {
        swapTimer.reset();
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        swapTimer.reset();
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null
                || mc.interactionManager == null
                || mc.player.isSpectator()
                || mc.currentScreen instanceof HandledScreen<?>) {
            return;
        }

        if (mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)
                || !swapTimer.hasTimeElapsed(SWAP_DELAY)) {
            return;
        }

        int sourceSlot = findTotemSlot();
        if (sourceSlot == -1) return;

        ClickSlotUtil.clickSlot(sourceSlot, OFFHAND_SWAP_BUTTON, SlotActionType.SWAP);
        swapTimer.reset();
    }

    private int findTotemSlot() {
        int inventorySize = Math.min(36, mc.player.getInventory().size());
        for (int inventorySlot = 0; inventorySlot < inventorySize; inventorySlot++) {
            ItemStack stack = mc.player.getInventory().getStack(inventorySlot);
            if (stack.isOf(Items.TOTEM_OF_UNDYING)) {
                return inventoryIndexToHandlerSlot(inventorySlot);
            }
        }

        return -1;
    }

    private int inventoryIndexToHandlerSlot(int inventorySlot) {
        return inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
    }
}
