package cn.omix.module.impl.combat;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.AttackEvent;
import cn.omix.event.impl.PacketEvent;
import cn.omix.event.impl.TickEvent;
import cn.omix.event.impl.WorldEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.impl.render.TargetHUD;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.module.value.impl.ModeValue;
import cn.omix.module.value.impl.NumberValue;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.EntityAnimationS2CPacket;
import net.minecraft.registry.tag.ItemTags;

import java.util.concurrent.atomic.AtomicInteger;

public final class AntiAim extends Module {
    private static final int NO_ENTITY = -1;
    private static final float COOLDOWN_EPSILON = 1.0e-3F;

    private final ModeValue mode = new ModeValue("Mode", "Blink", "Blink");
    private final NumberValue blinkTime = new NumberValue("Blink Time", 4, 1, 20, 1,
            () -> mode.is("Blink"));
    private final NumberValue releaseTime = new NumberValue("Release Time", 2, 0, 20, 1,
            () -> mode.is("Blink"));
    private final BoolValue releaseOnTargetLost = new BoolValue("Release On Target Lost", false,
            () -> mode.is("Blink"));

    private final AtomicInteger pendingAttackEntityId = new AtomicInteger(NO_ENTITY);
    private PlayerEntity target;
    private Item heldItem;
    private volatile int targetEntityId = NO_ENTITY;
    private int blinkTicks;
    private boolean trackingCooldown;
    private boolean blinking;

    public AntiAim() {
        super("AntiAim", Category.Combat);
    }

    @Override
    public void onEnable() {
        resetState(false);
        setSuffix(mode.getValue());
    }

    @Override
    public void onDisable() {
        resetState(true);
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        resetState(true);
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        releaseBlink();
        trackingCooldown = false;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != PacketEvent.Type.Received
                || !(event.getPacket() instanceof EntityAnimationS2CPacket packet)
                || packet.getAnimationId() != EntityAnimationS2CPacket.SWING_MAIN_HAND) {
            return;
        }

        int entityId = packet.getEntityId();
        if (entityId == targetEntityId) {
            pendingAttackEntityId.set(entityId);
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        setSuffix(mode.getValue());

        if (!canRun()) {
            resetState(true);
            return;
        }

        PlayerEntity displayedTarget = getDisplayedPlayerTarget();
        if (displayedTarget == null) {
            if (releaseOnTargetLost.getValue()) {
                resetState(true);
            } else {
                tickTrackedTarget(false);
            }
            return;
        }

        if (displayedTarget != target) {
            releaseBlink();
            target = displayedTarget;
            targetEntityId = displayedTarget.getId();
            heldItem = displayedTarget.getMainHandStack().getItem();
            pendingAttackEntityId.set(NO_ENTITY);
            trackingCooldown = false;
            return;
        }

        tickTrackedTarget(true);
    }

    private void tickTrackedTarget(boolean targetDisplayed) {
        if (target == null) {
            pendingAttackEntityId.set(NO_ENTITY);
            return;
        }

        ItemStack currentStack = target.getMainHandStack();
        Item currentItem = currentStack.getItem();
        if (currentItem != heldItem) {
            releaseBlink();
            heldItem = currentItem;
            trackingCooldown = false;
        }

        int attackedEntityId = pendingAttackEntityId.getAndSet(NO_ENTITY);
        if (!isWeapon(currentStack)) {
            releaseBlink();
            trackingCooldown = false;
            return;
        }

        if (targetDisplayed && attackedEntityId == targetEntityId) {
            releaseBlink();
            target.resetTicksSinceLastAttack();
            trackingCooldown = true;
        }

        if (!targetDisplayed && !blinking) {
            trackingCooldown = false;
            return;
        }

        if (!trackingCooldown) {
            return;
        }

        int cooldown = getRemainingCooldownTicks(target);
        int configuredBlinkTicks = blinkTime.getValue().intValue();
        int configuredReleaseTicks = releaseTime.getValue().intValue();

        if (blinking) {
            blinkTicks++;
            if (cooldown <= configuredReleaseTicks || blinkTicks >= configuredBlinkTicks) {
                releaseBlink();
                trackingCooldown = false;
            }
            return;
        }

        if (targetDisplayed && cooldown == configuredBlinkTicks + configuredReleaseTicks) {
            startBlink();
        } else if (cooldown == 0) {
            trackingCooldown = false;
        }
    }

    public int getRemainingCooldownTicks(PlayerEntity player) {
        if (player == null || !isWeapon(player.getMainHandStack())) {
            return 0;
        }

        float cooldownPeriod = player.getAttackCooldownProgressPerTick();
        float cooldownProgress = player.getAttackCooldownProgress(0.0F);
        if (!Float.isFinite(cooldownPeriod)
                || cooldownPeriod <= 0.0F
                || cooldownProgress >= 1.0F - COOLDOWN_EPSILON) {
            return 0;
        }

        float remaining = (1.0F - cooldownProgress) * cooldownPeriod;
        return Math.max(0, (int) Math.ceil(remaining - COOLDOWN_EPSILON));
    }

    private PlayerEntity getDisplayedPlayerTarget() {
        TargetHUD targetHUD = getModule(TargetHUD.class);
        if (targetHUD == null || !targetHUD.isEnabled()) {
            return null;
        }

        LivingEntity displayedTarget = targetHUD.getDisplayedTarget();
        if (!(displayedTarget instanceof PlayerEntity player)
                || player == mc.player
                || player.isRemoved()
                || !player.isAlive()) {
            return null;
        }

        return player;
    }

    private boolean canRun() {
        if (mc.player == null || mc.world == null || !mode.is("Blink")) {
            return false;
        }

        Aura aura = getModule(Aura.class);
        TargetHUD targetHUD = getModule(TargetHUD.class);
        return aura != null
                && aura.getAttackMode().is("1.9+")
                && targetHUD != null
                && targetHUD.isEnabled();
    }

    private boolean isWeapon(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.isIn(ItemTags.WEAPON_ENCHANTABLE);
    }

    private void startBlink() {
        if (blinking) {
            return;
        }

        instance.getPacketManager().getBlink().start(this);
        blinkTicks = 0;
        blinking = true;
    }

    private void releaseBlink() {
        if (blinking && instance.getPacketManager() != null) {
            instance.getPacketManager().getBlink().dispatch(this);
        }

        blinking = false;
        blinkTicks = 0;
    }

    private void resetState(boolean releasePackets) {
        if (releasePackets) {
            releaseBlink();
        } else {
            blinking = false;
            blinkTicks = 0;
        }

        target = null;
        heldItem = null;
        targetEntityId = NO_ENTITY;
        pendingAttackEntityId.set(NO_ENTITY);
        trackingCooldown = false;
    }
}
