package cn.remix.module.impl.move;

import cn.remix.event.base.annotation.EventPriority;
import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.MotionEvent;
import cn.remix.event.impl.PacketEvent;
import cn.remix.event.impl.TickEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.misc.TimerSpeedUtil;
import cn.remix.util.misc.TimerUtil;
import cn.remix.util.network.PacketUtil;
import cn.remix.util.player.RayCastUtil;
import injection.accessor.PlayerMoveC2SPacketAccessor;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

public final class NoFall extends Module {
    private static final long PLACE_DELAY = 500L;
    private static final long PICKUP_WAIT = 150L;

    private final ModeValue mode = new ModeValue("Mode", "Packet", "Packet", "Blink", "NoGround", "Spoof", "MLG");
    private final NumberValue distance = new NumberValue("Distance", 3.0, 0.0, 20.0, 0.5);
    private final NumberValue delay = new NumberValue("Delay", 0, 0, 10000, 50, () -> !mode.is("NoGround") && !mode.is("MLG"));
    private final BoolValue rotation = new BoolValue("Rotation", false, () -> mode.is("MLG"));
    private final TimerUtil packetDelayTimer = new TimerUtil();

    private boolean slowFalling;
    private boolean blinking;
    private boolean blinkArmed;
    private String activeMode;
    private int lastSlot = -1;
    private long lastPlace;
    private BlockPos placedWaterPos;

    public NoFall() {
        super("NoFall", Category.Move);
    }

    @Override
    public void onEnable() {
        resetState(false);
        activeMode = mode.getValue();
    }

    @Override
    public void onDisable() {
        resetState(true);
        activeMode = null;
    }

    @EventTarget
    @EventPriority(1)
    public void onPacket(PacketEvent event) {
        if (event.getType() == PacketEvent.Type.Received && event.getPacket() instanceof PlayerPositionLookS2CPacket) {
            resetState(true);
            return;
        }

        if (event.isCancelled()
                || event.getType() != PacketEvent.Type.Send
                || !(event.getPacket() instanceof PlayerMoveC2SPacket packet)
                || mc.player == null) {
            return;
        }

        syncModeState();
        setSuffix(mode.getValue());
        switch (mode.getValue()) {
            case "Packet" -> handlePacket(packet);
            case "Blink" -> handleBlink(packet);
            case "NoGround" -> setOnGround(packet, false);
            case "Spoof" -> handleSpoof(packet);
            default -> {
            }
        }
    }

    @EventTarget
    @EventPriority(1)
    public void onTick(TickEvent event) {
        if (mc.player == null) return;

        syncModeState();
        setSuffix(mode.getValue());
        if (mode.is("Packet") && slowFalling) {
            sendGroundPacket();
            mc.player.fallDistance = 0.0F;
        }

        if (mode.is("Blink") && blinking && mc.player.isOnGround()) {
            finishBlink();
        }

        if (mode.is("Blink")
                && !blinking
                && mc.player.isOnGround()
                && canBlink()
                && canTrigger()) {
            blinkArmed = true;
        }
    }

    @EventTarget
    @EventPriority(1000)
    public void onMotion(MotionEvent event) {
        if (mc.player == null) return;

        syncModeState();
        if (event.isPre() && mode.is("Blink") && blinking && event.isOnGround()) {
            finishBlink();
        }
        if (!mode.is("MLG")) return;
        setSuffix(mode.getValue());

        if (event.isPre()) {
            applyMlgRotation(event);
        } else {
            handleMlgPickup();
            handleMlgPlace();
        }
    }

    private void handlePacket(PlayerMoveC2SPacket packet) {
        if (slowFalling) {
            slowFalling = false;
            TimerSpeedUtil.reset();
            return;
        }

        if (!packet.isOnGround() && shouldSpoofFall()) {
            packetDelayTimer.reset();
            slowFalling = true;
            TimerSpeedUtil.setTimerSpeed(0.5F);
        }
    }

    private void handleBlink(PlayerMoveC2SPacket packet) {
        boolean allowed = canBlink();

        if (!allowed) {
            abortBlink();
            return;
        }

        if (blinking) {
            if (isInFluid() || isOverVoid()) {
                abortBlink();
            } else if (packet.isOnGround()) {
                finishBlink();
            }
            return;
        }

        if (packet.isOnGround()) {
            if (canTrigger()) {
                blinkArmed = true;
            }
            return;
        }

        if (blinkArmed
                && canBlinkFall(distance.getValue().intValue())
                && mc.player.getVelocity().y < 0.0
                && !isInFluid()
                && !isOverVoid()) {
            instance.getPacketManager().getBlink().start(this);
            blinking = true;
            blinkArmed = false;
        }
    }

    private void finishBlink() {
        if (!blinking) return;

        for (Packet<?> blinkedPacket : instance.getPacketManager().getBlink().packets) {
            if (blinkedPacket instanceof PlayerMoveC2SPacket movePacket) {
                setOnGround(movePacket, true);
            }
        }
        instance.getPacketManager().getBlink().dispatch(this);
        blinking = false;
        blinkArmed = false;
        packetDelayTimer.reset();
    }

    private void abortBlink() {
        if (blinking) {
            instance.getPacketManager().getBlink().dispatch(this);
        }
        blinking = false;
        blinkArmed = false;
    }

    private void handleSpoof(PlayerMoveC2SPacket packet) {
        if (!packet.isOnGround() && shouldSpoofFall()) {
            packetDelayTimer.reset();
            setOnGround(packet, true);
            mc.player.fallDistance = 0.0F;
        }
    }

    private boolean shouldSpoofFall() {
        return mc.player != null
                && !mc.player.isOnGround()
                && canFall(distance.getValue())
                && !isInFluid()
                && !isOverVoid()
                && canTrigger();
    }

    private boolean canTrigger() {
        return packetDelayTimer.hasTimeElapsed(delay.getValue());
    }

    private boolean canBlink() {
        return mc.player != null
                && !mc.player.isClimbing()
                && !mc.player.getAbilities().allowFlying
                && !mc.player.getAbilities().creativeMode
                && mc.player.hurtTime == 0;
    }

    private boolean canFall(float threshold) {
        if (mc.player == null || mc.world == null
                || mc.player.getAbilities().allowFlying
                || mc.player.getAbilities().creativeMode) {
            return false;
        }

        StatusEffectInstance jumpBoost = mc.player.getStatusEffect(StatusEffects.JUMP_BOOST);
        float jumpBoostLevel = jumpBoost == null ? 0.0F : jumpBoost.getAmplifier() + 1.0F;
        double fallDistance = mc.player.fallDistance;
        if (mc.player.getVelocity().y < -0.67 || !hasCollisionBelow(1.0)) {
            fallDistance -= mc.player.getVelocity().y;
        }
        return MathHelper.ceil(fallDistance - threshold - jumpBoostLevel) > 0;
    }

    private boolean hasCollisionBelow(double distance) {
        if (mc.player == null || mc.world == null) return false;
        Box box = mc.player.getBoundingBox().offset(0.0, -Math.abs(distance), 0.0);
        return mc.world.getBlockCollisions(mc.player, box).iterator().hasNext();
    }

    private boolean canBlinkFall(int checkHeight) {
        if (mc.player == null || mc.world == null
                || mc.player.getAbilities().allowFlying
                || mc.player.getAbilities().creativeMode) {
            return false;
        }

        int playerY = MathHelper.floor(mc.player.getY());
        int x = MathHelper.floor(mc.player.getX());
        int z = MathHelper.floor(mc.player.getZ());
        for (int offset = 0; offset <= checkHeight; offset++) {
            int y = playerY - offset;
            if (y < mc.world.getBottomY()) break;
            if (!mc.world.getBlockState(new BlockPos(x, y, z)).isAir()) return false;
        }
        return true;
    }

    private boolean isInFluid() {
        return mc.player != null && (mc.player.isTouchingWater() || mc.player.isInLava());
    }

    private boolean isOverVoid() {
        if (mc.player == null || mc.world == null) return true;

        Box box = mc.player.getBoundingBox().expand(2.0, 0.0, 2.0);
        int minX = MathHelper.floor(box.minX);
        int maxX = MathHelper.floor(box.maxX + 1.0);
        int minZ = MathHelper.floor(box.minZ);
        int maxZ = MathHelper.floor(box.maxZ + 1.0);
        int minY = MathHelper.floor(box.minY);

        for (int x = minX; x < maxX; x++) {
            for (int z = minZ; z < maxZ; z++) {
                for (int y = minY; y >= mc.world.getBottomY(); y--) {
                    if (!mc.world.getBlockState(new BlockPos(x, y, z)).isReplaceable()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private void sendGroundPacket() {
        if (mc.player != null) {
            PacketUtil.sendPacketNoEvent(new PlayerMoveC2SPacket.OnGroundOnly(true, mc.player.horizontalCollision));
        }
    }

    private void applyMlgRotation(MotionEvent event) {
        long now = System.currentTimeMillis();
        if (rotation.getValue()
                && (fallCheck() || placedWaterPos != null && elapsed(lastPlace, now) < PLACE_DELAY)
                && getWaterBucketSlot() != -1) {
            event.setPitch(90.0F);
        }
    }

    private void handleMlgPickup() {
        long now = System.currentTimeMillis();
        if (placedWaterPos == null || elapsed(lastPlace, now) <= PICKUP_WAIT) return;

        if (!isPlacedWaterStillThere()) {
            clearMlgWaterState();
            restoreSlot();
            return;
        }

        if (isItem(mc.player.getMainHandStack(), Items.BUCKET)) {
            useCurrentItem(rotation.getValue());
            clearMlgWaterState();
            restoreSlot();
        }
    }

    private void handleMlgPlace() {
        if (mc.player == null || placedWaterPos != null || mc.isPaused()
                || mc.player.getAbilities().flying
                || mc.player.getAbilities().creativeMode
                || !fallCheck()) {
            return;
        }

        float pitch = rotation.getValue() ? 90.0F : mc.player.getPitch();
        BlockHitResult target = RayCastUtil.raycastBlock(mc.player.getYaw(), pitch, mc.player.getBlockInteractionRange());
        if (target == null || target.getType() != HitResult.Type.BLOCK || target.getSide() != Direction.UP) return;

        long now = System.currentTimeMillis();
        if (elapsed(lastPlace, now) < PLACE_DELAY) return;

        if (!isItem(mc.player.getMainHandStack(), Items.WATER_BUCKET) && !attemptSwitch()) return;
        if (useWaterBucket(rotation.getValue())) {
            lastPlace = now;
            placedWaterPos = target.getBlockPos().offset(target.getSide());
        } else {
            restoreSlot();
        }
    }

    private boolean attemptSwitch() {
        int slot = getWaterBucketSlot();
        if (slot == -1) return false;

        int currentSlot = mc.player.getInventory().getSelectedSlot();
        if (currentSlot != slot) {
            lastSlot = currentSlot;
            setSelectedSlot(slot);
        }
        return isItem(mc.player.getMainHandStack(), Items.WATER_BUCKET);
    }

    private void restoreSlot() {
        if (lastSlot != -1 && mc.player != null) {
            setSelectedSlot(lastSlot);
        }
        lastSlot = -1;
    }

    private void setSelectedSlot(int slot) {
        mc.player.getInventory().setSelectedSlot(slot);
        PacketUtil.sendPacketNoEvent(new UpdateSelectedSlotC2SPacket(slot));
    }

    private int getWaterBucketSlot() {
        if (mc.player == null) return -1;
        for (int slot = 0; slot < 9; slot++) {
            if (isItem(mc.player.getInventory().getStack(slot), Items.WATER_BUCKET)) return slot;
        }
        return -1;
    }

    private void useCurrentItem(boolean silentRotation) {
        if (mc.interactionManager == null || mc.player == null) return;

        float oldPitch = mc.player.getPitch();
        if (silentRotation) mc.player.setPitch(90.0F);
        try {
            PacketUtil.runWithoutEvents(() -> mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND));
        } finally {
            if (silentRotation) mc.player.setPitch(oldPitch);
        }
    }

    private boolean useWaterBucket(boolean silentRotation) {
        if (mc.interactionManager == null || mc.player == null
                || !isItem(mc.player.getMainHandStack(), Items.WATER_BUCKET)) {
            return false;
        }

        float oldPitch = mc.player.getPitch();
        if (silentRotation) mc.player.setPitch(90.0F);
        try {
            ActionResult result = PacketUtil.runWithoutEvents(
                    () -> mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND)
            );
            if (result.isAccepted()) mc.player.swingHand(Hand.MAIN_HAND);
            return result.isAccepted();
        } finally {
            if (silentRotation) mc.player.setPitch(oldPitch);
        }
    }

    private boolean isPlacedWaterStillThere() {
        return placedWaterPos != null
                && mc.world != null
                && mc.world.getFluidState(placedWaterPos).isIn(FluidTags.WATER);
    }

    private void clearMlgWaterState() {
        placedWaterPos = null;
        lastPlace = 0L;
    }

    private boolean fallCheck() {
        return mc.player != null && !mc.player.isOnGround() && mc.player.fallDistance >= distance.getValue();
    }

    private boolean isItem(ItemStack stack, Item item) {
        return stack != null && stack.isOf(item);
    }

    private void setOnGround(PlayerMoveC2SPacket packet, boolean onGround) {
        ((PlayerMoveC2SPacketAccessor) packet).setOnGround(onGround);
    }

    private void resetState(boolean releaseBlink) {
        blinkArmed = false;
        slowFalling = false;
        TimerSpeedUtil.reset();

        if (releaseBlink && blinking && instance.getPacketManager() != null) {
            instance.getPacketManager().getBlink().dispatch(this);
        }
        blinking = false;

        restoreSlot();
        clearMlgWaterState();
        packetDelayTimer.reset();
    }

    private void syncModeState() {
        String selectedMode = mode.getValue();
        if (activeMode != null && !activeMode.equalsIgnoreCase(selectedMode)) {
            resetState(true);
        }
        activeMode = selectedMode;
    }

    private static long elapsed(long first, long second) {
        return Math.abs(second - first);
    }
}
