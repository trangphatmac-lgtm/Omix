package cn.omix.module.impl.player;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.MotionEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.module.value.impl.NumberValue;
import cn.omix.util.misc.TimerUtil;
import cn.omix.util.player.ClickSlotUtil;
import cn.omix.util.player.ItemUtil;
import lombok.Getter;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.slot.Slot;

public class InventoryManager extends Module {
    private final BoolValue inventoryOnly = new BoolValue("Inventory Only", false);
    private final NumberValue delay = new NumberValue("Delay", 50, 0, 1000, 10);

    private final NumberValue weaponSlot = new NumberValue("Weapon Slot", 1, 0, 9, 1);
    private final NumberValue pickaxeSlot = new NumberValue("Pickaxe Slot", 2, 0, 9, 1);
    private final NumberValue axeSlot = new NumberValue("Axe Slot", 3, 0, 9, 1);
    private final NumberValue shovelSlot = new NumberValue("Shovel Slot", 4, 0, 9, 1);
    private final NumberValue blockSlot = new NumberValue("Block Slot", 5, 0, 9, 1);
    private final NumberValue pearlSlot = new NumberValue("Pearl Slot", 6, 0, 9, 1);
    private final NumberValue projectileSlot = new NumberValue("Projectile Slot", 7, 0, 9, 1);

    private final NumberValue bowSlot = new NumberValue("Bow Slot", 0, 0, 9, 1);
    private final NumberValue fishingRodSlot = new NumberValue("Fishing Rod Slot", 0, 0, 9, 1);
    private final NumberValue waterBucketSlot = new NumberValue("Water Bucket Slot", 0, 0, 9, 1);
    private final NumberValue lavaBucketSlot = new NumberValue("Lava Bucket Slot", 0, 0, 9, 1);

    @Getter
    private final BoolValue keepFood = new BoolValue("Keep Food", false);
    private final NumberValue foodSlot = new NumberValue("Food Slot", 8, 0, 9, 1);

    @Getter
    private final NumberValue foodLimit = new NumberValue("Food Limit", 64, 0, 256, 32, keepFood::getValue);
    @Getter
    private final NumberValue blockLimit = new NumberValue("Block Limit", 128, 0, 512, 64);

    private final TimerUtil sortTimer = new TimerUtil();
    private final TimerUtil dropTimer = new TimerUtil();

    public InventoryManager() {
        super("InventoryManager", Category.Player);
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (mc.player == null || event.isPost()) return;

        boolean instant = delay.getValue().doubleValue() == 0.0;
        setSuffix(String.format("%.1f", delay.getValue()));

        if (inventoryOnly.getValue()) {
            if (!(mc.currentScreen instanceof InventoryScreen)) return;
        } else {
            if (mc.currentScreen != null && !(mc.currentScreen instanceof InventoryScreen)) return;
        }

        boolean sort = false;
        if (instant || sortTimer.hasTimeElapsed(delay.getValue().longValue())) {
            int weapon = weaponSlot.getValue().intValue();
            if (weapon > 0) {
                int targetWeapon = 36 + weapon - 1;
                if (sortItem(ItemUtil.getBestWeaponSlot(targetWeapon), weapon - 1)) sort = true;
            }

            int pickaxe = pickaxeSlot.getValue().intValue();
            if (pickaxe > 0 && (!sort || instant)) {
                int targetPickaxe = 36 + pickaxe - 1;
                if (sortItem(ItemUtil.getBestToolSlot(ItemTags.PICKAXES, targetPickaxe), pickaxe - 1)) sort = true;
            }

            int axe = axeSlot.getValue().intValue();
            if (axe > 0 && (!sort || instant)) {
                int targetAxe = 36 + axe - 1;
                if (sortItem(ItemUtil.getBestToolSlot(ItemTags.AXES, targetAxe), axe - 1)) sort = true;
            }

            int shovel = shovelSlot.getValue().intValue();
            if (shovel > 0 && (!sort || instant)) {
                int targetShovel = 36 + shovel - 1;
                if (sortItem(ItemUtil.getBestToolSlot(ItemTags.SHOVELS, targetShovel), shovel - 1)) sort = true;
            }

            int block = blockSlot.getValue().intValue();
            if (block > 0 && (!sort || instant)) {
                int targetBlock = 36 + block - 1;
                if (sortItem(ItemUtil.getBestBlockSlot(targetBlock), block - 1)) sort = true;
            }

            int pearl = pearlSlot.getValue().intValue();
            if (pearl > 0 && (!sort || instant)) {
                int targetPearl = 36 + pearl - 1;
                if (sortItem(ItemUtil.getBestPearlSlot(targetPearl), pearl - 1)) sort = true;
            }

            int projectile = projectileSlot.getValue().intValue();
            if (projectile > 0 && (!sort || instant)) {
                int targetProjectile = 36 + projectile - 1;
                if (sortItem(ItemUtil.getBestProjectileSlot(targetProjectile), projectile - 1)) sort = true;
            }

            int bow = bowSlot.getValue().intValue();
            if (bow > 0 && (!sort || instant)) {
                int targetBow = 36 + bow - 1;
                if (sortItem(findBestItemSlot(Items.BOW, targetBow), bow - 1)) sort = true;
            }

            int fishingRod = fishingRodSlot.getValue().intValue();
            if (fishingRod > 0 && (!sort || instant)) {
                int targetRod = 36 + fishingRod - 1;
                if (sortItem(findBestItemSlot(Items.FISHING_ROD, targetRod), fishingRod - 1)) sort = true;
            }

            int waterBucket = waterBucketSlot.getValue().intValue();
            if (waterBucket > 0 && (!sort || instant)) {
                int targetWater = 36 + waterBucket - 1;
                if (sortItem(findBestItemSlot(Items.WATER_BUCKET, targetWater), waterBucket - 1)) sort = true;
            }

            int lavaBucket = lavaBucketSlot.getValue().intValue();
            if (lavaBucket > 0 && (!sort || instant)) {
                int targetLava = 36 + lavaBucket - 1;
                if (sortItem(findBestItemSlot(Items.LAVA_BUCKET, targetLava), lavaBucket - 1)) sort = true;
            }

            int food = foodSlot.getValue().intValue();
            if (food > 0 && (!sort || instant)) {
                int targetFood = 36 + food - 1;
                if (sortItem(ItemUtil.getBestFoodSlot(targetFood), food - 1)) sort = true;
            }
        }

        if (sort && !instant) return;

        if (instant || dropTimer.hasTimeElapsed(delay.getValue().longValue())) {
            int prefBow = bowSlot.getValue().intValue() > 0 ? 36 + bowSlot.getValue().intValue() - 1 : -1;
            int prefRod = fishingRodSlot.getValue().intValue() > 0 ? 36 + fishingRodSlot.getValue().intValue() - 1 : -1;
            int prefWater = waterBucketSlot.getValue().intValue() > 0 ? 36 + waterBucketSlot.getValue().intValue() - 1 : -1;
            int prefLava = lavaBucketSlot.getValue().intValue() > 0 ? 36 + lavaBucketSlot.getValue().intValue() - 1 : -1;

            int keepBowSlot = findBestItemSlot(Items.BOW, prefBow);
            int keepRodSlot = findBestItemSlot(Items.FISHING_ROD, prefRod);
            int keepWaterSlot = findBestItemSlot(Items.WATER_BUCKET, prefWater);
            int keepLavaSlot = findBestItemSlot(Items.LAVA_BUCKET, prefLava);

            for (int i = 5; i <= 45; i++) {
                Slot slot = mc.player.currentScreenHandler.getSlot(i);

                if (slot.hasStack()) {
                    var item = slot.getStack().getItem();
                    boolean isDuplicate = false;

                    if (item == Items.BOW && i != keepBowSlot) isDuplicate = true;
                    else if (item == Items.FISHING_ROD && i != keepRodSlot) isDuplicate = true;
                    else if (item == Items.WATER_BUCKET && i != keepWaterSlot) isDuplicate = true;
                    else if (item == Items.LAVA_BUCKET && i != keepLavaSlot) isDuplicate = true;

                    if (isDuplicate || ItemUtil.isUseless(i, slot.getStack())) {
                        ClickSlotUtil.dropAll(i);
                        dropTimer.reset();
                        if (!instant) {
                            return;
                        }
                    }
                }
            }
        }
    }

    private boolean sortItem(int bestSlot, int targetHotbar) {
        if (bestSlot == -1) return false;

        int targetGlobalSlot = 36 + targetHotbar;
        if (bestSlot != targetGlobalSlot) {
            ClickSlotUtil.swap(bestSlot, targetHotbar);
            sortTimer.reset();
            return true;
        }

        return false;
    }

    private int findBestItemSlot(net.minecraft.item.Item targetItem, int preferredSlot) {
        if (mc.player == null) return -1;

        if (preferredSlot >= 5 && preferredSlot <= 45) {
            Slot slot = mc.player.currentScreenHandler.getSlot(preferredSlot);
            if (slot.hasStack() && slot.getStack().getItem() == targetItem) {
                return preferredSlot;
            }
        }

        for (int i = 5; i <= 45; i++) {
            Slot slot = mc.player.currentScreenHandler.getSlot(i);
            if (slot.hasStack() && slot.getStack().getItem() == targetItem) {
                return i;
            }
        }
        return -1;
    }
}