package cn.omix.module.impl.combat;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.UpdateEvent;
import cn.omix.event.impl.WorldEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.NumberValue;
import cn.omix.util.combat.OffhandItems;
import cn.omix.util.misc.TimerUtil;
import cn.omix.util.player.ClickSlotUtil;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.SlotActionType;

public final class Offhand extends Module {
    private static final int OFFHAND_SWAP_BUTTON = 40;

    private final NumberValue delay = new NumberValue("Delay", 150, 0, 2000, 50);
    private final TimerUtil swapTimer = new TimerUtil();

    public Offhand() {
        super("Offhand", Category.Combat);
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
                || !mc.player.isAlive()
                || mc.player.isSpectator()
                || mc.player.isUsingItem()
                || mc.currentScreen instanceof HandledScreen<?>
                || mc.player.currentScreenHandler != mc.player.playerScreenHandler
                || !mc.player.currentScreenHandler.getCursorStack().isEmpty()
                || !swapTimer.hasTimeElapsed(delay.getValue())) {
            return;
        }

        // Both modules manage the same slot; let AutoTotem retain ownership.
        AutoTotem autoTotem = getModule(AutoTotem.class);
        if (autoTotem != null && autoTotem.isNativeBehaviorActive()) return;

        int inventorySlot = OffhandItems.findBestSlot(mc.player.getInventory(), mc.player.getOffHandStack());
        if (inventorySlot == -1) return;

        int handlerSlot = inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
        ClickSlotUtil.clickSlot(handlerSlot, OFFHAND_SWAP_BUTTON, SlotActionType.SWAP);
        swapTimer.reset();
    }
}
