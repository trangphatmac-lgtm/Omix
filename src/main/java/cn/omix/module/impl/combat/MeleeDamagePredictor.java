package cn.omix.module.impl.combat;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.DamageUtil;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.registry.tag.EntityTypeTags;
import net.minecraft.util.math.MathHelper;

/**
 * Client-side approximation of the vanilla melee damage pipeline. Dynamic
 * server-side enchantments or damage modifications cannot be predicted here.
 */
final class MeleeDamagePredictor {
    private static final float ATTACK_TICK_DELTA = .5f;
    private static final float FULL_CHARGE_THRESHOLD = .9f;

    private MeleeDamagePredictor() {
    }

    static boolean canKill(PlayerEntity player, LivingEntity target) {
        float remainingHealth = target.getHealth() + target.getAbsorptionAmount();
        return remainingHealth > 0 && predictDamage(player, target) >= remainingHealth;
    }

    static float predictDamage(PlayerEntity player, LivingEntity target) {
        if (target.isBlocking()) {
            return 0;
        }

        ItemStack weapon = player.getWeaponStack();
        DamageSource damageSource = weapon.getDamageSource(
                player,
                () -> player.getDamageSources().playerAttack(player)
        );
        float cooldown = player.getAttackCooldownProgress(ATTACK_TICK_DELTA);
        float baseDamage = (float) player.getAttributeValue(EntityAttributes.ATTACK_DAMAGE);
        float enchantmentDamage = getEnchantmentDamage(weapon, target) * cooldown;

        float attackDamage = baseDamage * getCooldownDamageModifier(cooldown);
        attackDamage += weapon.getItem().getBonusAttackDamage(target, attackDamage, damageSource);
        attackDamage += getClientOnlyMaceDamage(weapon, player);

        if (cooldown > FULL_CHARGE_THRESHOLD && isCriticalHit(player)) {
            attackDamage *= 1.5f;
        }

        float damage = attackDamage + enchantmentDamage;
        damage = applyArmor(target, weapon, damageSource, damage);
        damage = applyResistance(target, damageSource, damage);
        return applyProtection(target, damageSource, damage);
    }

    static float getCooldownDamageModifier(float cooldown) {
        return .2f + cooldown * cooldown * .8f;
    }

    private static float getEnchantmentDamage(ItemStack weapon, LivingEntity target) {
        int sharpness = getEnchantmentLevel(weapon, Enchantments.SHARPNESS);
        int smite = getEnchantmentLevel(weapon, Enchantments.SMITE);
        int bane = getEnchantmentLevel(weapon, Enchantments.BANE_OF_ARTHROPODS);
        int impaling = getEnchantmentLevel(weapon, Enchantments.IMPALING);

        float damage = sharpness > 0 ? 1f + (sharpness - 1) * .5f : 0;
        if (target.getType().isIn(EntityTypeTags.SENSITIVE_TO_SMITE)) {
            damage += smite * 2.5f;
        }
        if (target.getType().isIn(EntityTypeTags.SENSITIVE_TO_BANE_OF_ARTHROPODS)) {
            damage += bane * 2.5f;
        }
        if (target.getType().isIn(EntityTypeTags.SENSITIVE_TO_IMPALING)) {
            damage += impaling * 2.5f;
        }
        return damage;
    }

    /**
     * MaceItem can calculate the normal smash bonus on the client, but the
     * data-driven Density enchantment is evaluated only in a ServerWorld.
     */
    private static float getClientOnlyMaceDamage(ItemStack weapon, PlayerEntity player) {
        if (!weapon.isOf(Items.MACE) || player.fallDistance <= 1.5) {
            return 0;
        }

        int density = getEnchantmentLevel(weapon, Enchantments.DENSITY);
        return density * .5f * (float) player.fallDistance;
    }

    private static float applyArmor(
            LivingEntity target,
            ItemStack weapon,
            DamageSource damageSource,
            float damage
    ) {
        if (damageSource.isIn(DamageTypeTags.BYPASSES_ARMOR)) {
            return damage;
        }

        int breach = getEnchantmentLevel(weapon, Enchantments.BREACH);
        float armorEffectiveness = MathHelper.clamp(1f - breach * .15f, 0f, 1f);
        float armor = target.getArmor() * armorEffectiveness;
        float toughness = (float) target.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);
        return DamageUtil.getDamageLeft(target, damage, damageSource, armor, toughness);
    }

    private static float applyResistance(
            LivingEntity target,
            DamageSource damageSource,
            float damage
    ) {
        if (damageSource.isIn(DamageTypeTags.BYPASSES_EFFECTS)
                || damageSource.isIn(DamageTypeTags.BYPASSES_RESISTANCE)) {
            return damage;
        }

        StatusEffectInstance resistance = target.getStatusEffect(StatusEffects.RESISTANCE);
        if (resistance == null) {
            return damage;
        }

        int resistancePoints = (resistance.getAmplifier() + 1) * 5;
        return Math.max(damage * (25 - resistancePoints) / 25f, 0);
    }

    private static float applyProtection(
            LivingEntity target,
            DamageSource damageSource,
            float damage
    ) {
        if (damageSource.isIn(DamageTypeTags.BYPASSES_EFFECTS)
                || damageSource.isIn(DamageTypeTags.BYPASSES_ENCHANTMENTS)
                || damageSource.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return damage;
        }

        int protection = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.isArmorSlot()) {
                protection += getEnchantmentLevel(target.getEquippedStack(slot), Enchantments.PROTECTION);
            }
        }
        return DamageUtil.getInflictedDamage(damage, protection);
    }

    private static int getEnchantmentLevel(
            ItemStack stack,
            RegistryKey<Enchantment> enchantment
    ) {
        ItemEnchantmentsComponent enchantments = stack.getOrDefault(
                DataComponentTypes.ENCHANTMENTS,
                ItemEnchantmentsComponent.DEFAULT
        );
        for (RegistryEntry<Enchantment> entry : enchantments.getEnchantments()) {
            if (entry.matchesKey(enchantment)) {
                return enchantments.getLevel(entry);
            }
        }
        return 0;
    }

    private static boolean isCriticalHit(PlayerEntity player) {
        return player.fallDistance > 0
                && !player.isOnGround()
                && !player.isClimbing()
                && !player.isTouchingWater()
                && !player.hasBlindnessEffect()
                && !player.hasVehicle()
                && !player.isSprinting();
    }
}
