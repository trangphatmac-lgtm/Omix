package cn.omix.util.combat;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MaceItem;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Set;

public final class AutoWeaponItems {
    private AutoWeaponItems() {}

    public static AutoWeaponSelection.Kind kind(ItemStack stack) {
        if (stack.isIn(ItemTags.SWORDS)) return AutoWeaponSelection.Kind.SWORD;
        if (stack.isIn(ItemTags.AXES)) return AutoWeaponSelection.Kind.AXE;
        if (stack.getItem() instanceof MaceItem) return AutoWeaponSelection.Kind.MACE;
        if (stack.isIn(ItemTags.SPEARS)) return AutoWeaponSelection.Kind.SPEAR;
        if (stack.isIn(ItemTags.PICKAXES)) return AutoWeaponSelection.Kind.PICKAXE;
        if (stack.isIn(ItemTags.SHOVELS)) return AutoWeaponSelection.Kind.SHOVEL;
        if (stack.isIn(ItemTags.HOES)) return AutoWeaponSelection.Kind.HOE;
        return AutoWeaponSelection.Kind.OTHER;
    }

    public static int enchantment(ItemStack stack, RegistryKey<Enchantment> key) {
        var enchantments = stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
        for (var entry : enchantments.getEnchantments()) {
            if (entry.matchesKey(key)) return enchantments.getLevel(entry);
        }
        return 0;
    }

    public static double score(ItemStack stack) {
        var modifiers = modifiers(stack);
        double damage = modifiers.applyOperations(EntityAttributes.ATTACK_DAMAGE, 1, EquipmentSlot.MAINHAND);
        double speed = modifiers.applyOperations(EntityAttributes.ATTACK_SPEED, 4, EquipmentSlot.MAINHAND);
        int sharpness = enchantment(stack, Enchantments.SHARPNESS);
        if (sharpness > 0) damage += .5 * sharpness + .5;
        // Rank sustained damage with a small allowance for secondary enchantments.
        return damage * Math.max(.01, speed)
                + enchantment(stack, Enchantments.FIRE_ASPECT) * .5
                + enchantment(stack, Enchantments.KNOCKBACK) * .2;
    }

    /** Preserve status effects while replacing equipment modifiers, even before vanilla updates them. */
    public static double attackSpeed(PlayerEntity player, ItemStack weapon) {
        var original = player.getAttributeInstance(EntityAttributes.ATTACK_SPEED);
        if (original == null) return 4;
        Set<Identifier> equipmentIds = new HashSet<>();
        for (int slot = 0; slot < 9; slot++) {
            modifiers(player.getInventory().getStack(slot)).applyModifiers(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
                if (attribute.equals(EntityAttributes.ATTACK_SPEED)) equipmentIds.add(modifier.id());
            });
        }
        var predicted = new EntityAttributeInstance(EntityAttributes.ATTACK_SPEED, ignored -> {});
        predicted.setBaseValue(original.getBaseValue());
        for (var modifier : original.getModifiers()) {
            if (!equipmentIds.contains(modifier.id())) predicted.addTemporaryModifier(modifier);
        }
        modifiers(weapon).applyModifiers(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.equals(EntityAttributes.ATTACK_SPEED)) predicted.updateModifier(modifier);
        });
        return Math.max(.01, predicted.getValue());
    }

    public static boolean wouldBlock(PlayerEntity player, LivingEntity target) {
        if (target == null || target.getBlockingItem() == null) return false;
        Vec3d towardPlayer = player.getEntityPos().subtract(target.getEntityPos()).multiply(1, 0, 1).normalize();
        Vec3d facing = target.getRotationVec(1).multiply(1, 0, 1).normalize();
        return facing.dotProduct(towardPlayer) > 0;
    }

    private static AttributeModifiersComponent modifiers(ItemStack stack) {
        return stack.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT);
    }
}
