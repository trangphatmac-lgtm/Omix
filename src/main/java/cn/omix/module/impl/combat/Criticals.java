package cn.omix.module.impl.combat;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.AttackEvent;
import cn.omix.event.impl.MotionEvent;
import cn.omix.event.impl.UpdateEvent;
import cn.omix.event.impl.WorldEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.impl.player.Stuck;
import cn.omix.module.impl.world.Scaffold;
import cn.omix.module.impl.world.ScaffoldX;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.module.value.impl.ModeValue;
import cn.omix.module.value.impl.NumberValue;
import cn.omix.util.network.PacketUtil;
import injection.accessor.ClientPlayerEntityAccessor;
import net.minecraft.block.CobwebBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;

public final class Criticals extends Module {
    private final ModeValue mode = new ModeValue("Mode", "Packet", "No Ground", "NCP", "Strict", "Sentinel", "Packet", "Stuck", "1.9+", "Heypixel");
    private final NumberValue waitTicks = new NumberValue("WaitTicks", 1F, 1F, 3F, 1F, () -> mode.is("Stuck"));
    private final BoolValue fallDistance = new BoolValue("FallDistance", false, () -> mode.is("Stuck"));
    private final NumberValue targetTicks = new NumberValue("TargetTicks", 2F, .1F, 3F, .1F, this::usesAttackTiming);
    private final CriticalsTiming timing = new CriticalsTiming();

    public Criticals() {
        super("Criticals", Category.Combat);
    }

    @Override
    public String getSuffix() {
        return mode.getValue();
    }

    @Override
    public void onDisable() {
        cleanupStuck();
        // The reference deliberately retains the damage cache across disables.
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        Stuck stuck = getModule(Stuck.class);
        if (stuck != null) stuck.endCriticalsFreeze(false);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!mode.is("Stuck")) {
            cleanupStuck();
            return;
        }
        if (shouldSkipStuck()) {
            cleanupStuck();
            return;
        }
        getModule(Stuck.class).beginCriticalsFreeze(waitTicks.getValue().intValue());
    }

    public boolean shouldSkipStuck() {
        if (mc.player == null || !mode.is("Stuck")) return true;
        Aura aura = getModule(Aura.class);
        if (aura == null || !aura.isEnabled() || aura.getTarget() == null
                || !aura.getTarget().isAlive() || cannotCrit(false)) return true;
        Velocity velocity = getModule(Velocity.class);
        if (velocity != null && velocity.isEnabled() && velocity.getReduceTicks() > 0) return true;
        if (aura.getTarget().squaredDistanceTo(mc.player) > 9.0) return true;
        Stuck stuck = getModule(Stuck.class);
        Scaffold scaffold = getModule(Scaffold.class);
        ScaffoldX scaffoldX = getModule(ScaffoldX.class);
        if (stuck == null || stuck.isClutchFreezeActive()
                || (scaffold != null && scaffold.isEnabled())
                || (scaffoldX != null && scaffoldX.isEnabled())) return true;
        return CriticalsTiming.skipStuckFall(fallDistance.getValue(), mc.player.fallDistance, mc.player.getVelocity().y);
    }

    public boolean cannotCrit(boolean allowGround) {
        if (mc.player == null) return true;
        if (mc.player.hasStatusEffect(StatusEffects.LEVITATION)
                || mc.player.hasStatusEffect(StatusEffects.BLINDNESS)
                || mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING)) return true;
        if (mc.player.isClimbing() || mc.player.hasNoGravity() || mc.player.hasVehicle()
                || mc.player.getAbilities().flying || mc.player.isTouchingWater()) return true;
        if (!allowGround && mc.player.isOnGround()) return true;
        return intersectsCobweb();
    }

    /** Called by Aura before consuming its attack timer or sending an attack. */
    public boolean shouldDeferAttack(Entity target) {
        if (!isEnabled() || mc.player == null || !(target instanceof LivingEntity living) || !usesAttackTiming()) return false;
        if (mc.player.isGliding() || mc.player.hasVehicle()
                || mc.player.isTouchingWater() || mc.player.isClimbing()) return false;

        boolean cannotCrit = cannotCrit(false);
        double vy = mc.player.getVelocity().y;
        float cooldown = mc.player.getAttackCooldownProgress(.5F);
        float damage = mode.is("1.9+")
                ? CriticalsTiming.estimateDamage(estimatedItemDamage(mc.player.getMainHandStack()), cooldown, cannotCrit, vy)
                : 0F;
        return timing.shouldDefer(mode.is("1.9+"), living.hurtTime, damage, cannotCrit, vy,
                cooldown, mc.player.getAttackCooldownProgressPerTick(), targetTicks.getValue(),
                ticks -> CriticalsLandingPredictor.willLand(mc.player, ticks));
    }

    private boolean usesAttackTiming() {
        return mode.is("1.9+") || mode.is("Heypixel");
    }

    private void cleanupStuck() {
        Stuck stuck = getModule(Stuck.class);
        if (stuck != null) stuck.endCriticalsFreeze(true);
    }

    private boolean intersectsCobweb() {
        if (mc.world == null) return false;
        Box box = mc.player.getBoundingBox();
        for (int x = MathHelper.floor(box.minX); x < MathHelper.ceil(box.maxX); x++) {
            for (int y = MathHelper.floor(box.minY); y < MathHelper.ceil(box.maxY); y++) {
                for (int z = MathHelper.floor(box.minZ); z < MathHelper.ceil(box.maxZ); z++) {
                    if (mc.world.getBlockState(new BlockPos(x, y, z)).getBlock() instanceof CobwebBlock) return true;
                }
            }
        }
        return false;
    }

    private static float estimatedItemDamage(ItemStack stack) {
        if (stack.isEmpty()) return 0F;
        // Match the reference's item estimate, not armor-adjusted server damage.
        float damage = 0F;
        if (stack.isIn(ItemTags.SWORDS)) {
            if (stack.isOf(Items.WOODEN_SWORD) || stack.isOf(Items.GOLDEN_SWORD)) damage = 4F;
            else if (stack.isOf(Items.STONE_SWORD)) damage = 5F;
            else if (stack.isOf(Items.IRON_SWORD)) damage = 6F;
            else if (stack.isOf(Items.DIAMOND_SWORD)) damage = 7F;
            else if (stack.isOf(Items.NETHERITE_SWORD)) damage = 8F;
            else damage = 2F;
        }
        var enchantments = stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
        for (var enchantment : enchantments.getEnchantments()) {
            if (enchantment.matchesKey(Enchantments.SHARPNESS)) {
                int level = enchantments.getLevel(enchantment);
                if (level > 0) damage += .5F + .5F * level;
                break;
            }
        }
        return damage;
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (mc.player == null) return;

        switch (mode.getValue()) {
            case "NCP" -> {
                sendCritPacket(0.000000271875, false);
                sendCritPacket(0, false);
            }

            case "Packet" -> {
                sendCritPacket(0.0625, false);
                sendCritPacket(0, false);
            }

            case "Strict" -> {
                sendCritPacket(0.062600301692775, false);
                sendCritPacket(0.07260029960661, false);
                sendCritPacket(0., false);
                sendCritPacket(0., false);
            }

            case "Sentinel" -> {
                if (!mc.player.isOnGround()) {
                    sendCritPacket(-0.000001, true);
                }
            }
        }
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (mc.player == null) return;

        if (!usesAttackTiming() && !mode.is("Stuck")) event.setOnGround(false);
    }

    private void sendCritPacket(double offset, boolean full) {
        if (mc.player == null) return;

        double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
        boolean h = mc.player.horizontalCollision;
        if (!full) {
            PacketUtil.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + offset, z, false, h));
        } else {
            PacketUtil.sendPacket(new PlayerMoveC2SPacket.Full(x, mc.player.getY() + offset, z, ((ClientPlayerEntityAccessor) mc.player).getLastYaw(), ((ClientPlayerEntityAccessor) mc.player).getLastPitch(), false, h));
        }
    }
}
