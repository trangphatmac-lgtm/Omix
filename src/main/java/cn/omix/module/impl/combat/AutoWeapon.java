package cn.omix.module.impl.combat;

import cn.omix.event.base.annotation.EventPriority;
import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.AttackEvent;
import cn.omix.event.impl.UpdateEvent;
import cn.omix.event.impl.WorldEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.impl.move.LongJump;
import cn.omix.module.impl.move.NoSlowDown;
import cn.omix.module.impl.player.AutoBlockIn;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.module.value.impl.MultiBoolValue;
import cn.omix.module.value.impl.NumberValue;
import cn.omix.util.combat.AutoWeaponItems;
import cn.omix.util.combat.AutoWeaponSelection;
import cn.omix.util.combat.AutoWeaponSlotState;
import injection.accessor.ClientPlayerInteractionManagerAccessor;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MaceItem;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;

import java.util.ArrayList;

/** Adapts LiquidBounce's AutoWeapon selection and delayed return to the local hotbar API. */
public final class AutoWeapon extends Module {
    private final MultiBoolValue preferred = new MultiBoolValue("Preferred",
            new BoolValue("Any", false), new BoolValue("Sword", true),
            new BoolValue("Axe", false), new BoolValue("Mace", false),
            new BoolValue("Spear", false), new BoolValue("Pickaxe", false),
            new BoolValue("Shovel", false), new BoolValue("Hoe", false),
            new BoolValue("Knockback", false), new BoolValue("FireAspect", false));
    private final BoolValue autoShieldBreak = new BoolValue("AutoShieldBreak", true);
    private final BoolValue autoMace = new BoolValue("AutoMace", true);
    private final NumberValue switchBack = new NumberValue("SwitchBack", 20, 1, 300, 1);
    private final MultiBoolValue changeOn = new MultiBoolValue("ChangeOn",
            new BoolValue("OnAttack", true), new BoolValue("OnTarget", false));

    private final AutoWeaponSlotState slots = new AutoWeaponSlotState();
    private ClientPlayerEntity owner;
    private ClientWorld ownerWorld;

    public AutoWeapon() {
        super("Auto Weapon", Category.Combat);
    }

    @EventTarget
    @EventPriority(-100)
    public void onAttack(AttackEvent event) {
        if (!event.isCancelled()) beforeAttack(event.getEntity());
    }

    /** Also used by TPAura, whose attacks bypass the interaction manager. */
    public void beforeAttack(Entity target) {
        if (changeOn.isEnabled("OnAttack")) select(target);
    }

    public void onTarget(Entity target) {
        if (changeOn.isEnabled("OnTarget")) select(target);
    }

    /** Predict the actual attack item without changing slots or refreshing the return timer. */
    public ItemStack getAttackWeapon(LivingEntity target) {
        if (mc.player == null) return ItemStack.EMPTY;
        if (isNativeBehaviorActive() && changeOn.isEnabled("OnAttack") && target != null
                && target.isAlive() && target != mc.player && canSwitch()) {
            int slot = determineSlot(target);
            if (slot >= 0) return mc.player.getInventory().getStack(slot);
        }
        return mc.player.getMainHandStack();
    }

    @EventTarget
    @EventPriority(-100)
    public void onUpdate(UpdateEvent event) {
        validateOwner();
        if (mc.player == null) return;
        slots.observe(mc.player.getInventory().getSelectedSlot());
        if (!canSwitch()) return;
        int restore = slots.expire(mc.player.getInventory().getSelectedSlot(), mc.player.age);
        if (restore >= 0) switchSlot(restore);
        // Aura / TPAura call onTarget immediately after acquiring their own target.
        Aura aura = getModule(Aura.class);
        TPAura tpAura = getModule(TPAura.class);
        if ((aura == null || !aura.isEnabled()) && (tpAura == null || !tpAura.isEnabled())) {
            onTarget(crosshairTarget());
        }
    }

    @Override
    public void onDisable() {
        validateOwner();
        if (owner != null) {
            int restore = slots.restore(owner.getInventory().getSelectedSlot());
            if (restore >= 0) switchSlot(restore);
        }
        clear();
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        clear();
    }

    /** Avoid using the previous item's stale attack-speed attribute on the switching tick. */
    public double getAttackSpeed(double original) {
        if (!isNativeBehaviorActive() || !canSwitch()) return original;
        validateOwner();
        int slot = changeOn.isEnabled("OnAttack") ? determineSlot(currentTarget()) : -1;
        if (slot < 0 && slots.owns(mc.player.getInventory().getSelectedSlot())) {
            slot = mc.player.getInventory().getSelectedSlot();
        }
        return slot < 0 ? original : AutoWeaponItems.attackSpeed(mc.player, mc.player.getInventory().getStack(slot));
    }

    private void select(Entity entity) {
        if (!isNativeBehaviorActive() || !(entity instanceof LivingEntity target) || !target.isAlive()
                || entity == mc.player || !canSwitch()) return;
        validateOwner();
        int slot = determineSlot(target);
        if (slot < 0) return;
        owner = mc.player;
        ownerWorld = mc.world;
        slots.select(owner.getInventory().getSelectedSlot(), slot, owner.age, switchBack.getValue().intValue());
        switchSlot(slot);
    }

    private int determineSlot(LivingEntity target) {
        MaceDamageBooster booster = getModule(MaceDamageBooster.class);
        boolean smash = autoMace.getValue() && (MaceItem.shouldDealAdditionalDamage(mc.player)
                || booster != null && booster.isEnabled());
        boolean shield = autoShieldBreak.getValue() && AutoWeaponItems.wouldBlock(mc.player, target);
        var candidates = new ArrayList<AutoWeaponSelection.Candidate>();
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.isEmpty()) continue;
            var kind = AutoWeaponItems.kind(stack);
            boolean matches = preferred.isEnabled("Any") || preferred.isEnabled(kind.name())
                    || preferred.isEnabled("Knockback") && AutoWeaponItems.enchantment(stack, Enchantments.KNOCKBACK) > 0
                    || preferred.isEnabled("FireAspect") && AutoWeaponItems.enchantment(stack, Enchantments.FIRE_ASPECT) > 0;
            candidates.add(new AutoWeaponSelection.Candidate(slot, kind, matches,
                    AutoWeaponItems.score(stack), stack.getMaxDamage() - stack.getDamage()));
        }
        return AutoWeaponSelection.select(candidates, smash, shield, mc.player.getInventory().getSelectedSlot());
    }

    private boolean canSwitch() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null
                || !mc.player.isAlive() || mc.player.isSpectator() || mc.currentScreen != null) return false;
        if (mc.player.isUsingItem() && mc.player.getActiveHand() == Hand.MAIN_HAND
                && mc.player.getActiveItem().contains(DataComponentTypes.CONSUMABLE)) return false;
        var grim = NoSlowDown.activeGrim();
        if (grim != null && grim.lockSlot()) return false;
        LongJump longJump = getModule(LongJump.class);
        AutoBlockIn blockIn = getModule(AutoBlockIn.class);
        return (longJump == null || !longJump.isUsingItemThisTick())
                && (blockIn == null || !blockIn.isPlacing());
    }

    private LivingEntity currentTarget() {
        TPAura tpAura = getModule(TPAura.class);
        if (tpAura != null && tpAura.isEnabled() && tpAura.getTarget() != null) return tpAura.getTarget();
        Aura aura = getModule(Aura.class);
        if (aura != null && aura.isEnabled() && aura.getTarget() != null) return aura.getTarget();
        return crosshairTarget();
    }

    private LivingEntity crosshairTarget() {
        return mc.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity living
                ? living : null;
    }

    private void switchSlot(int slot) {
        mc.player.getInventory().setSelectedSlot(slot);
        if (mc.interactionManager != null) {
            // Use vanilla's slot cache and normal packet events, including before mace movement packets.
            ((ClientPlayerInteractionManagerAccessor) mc.interactionManager).omix$syncSelectedSlot();
        }
    }

    private void validateOwner() {
        if (owner != mc.player || ownerWorld != mc.world) clear();
    }

    private void clear() {
        slots.clear();
        owner = null;
        ownerWorld = null;
    }
}
