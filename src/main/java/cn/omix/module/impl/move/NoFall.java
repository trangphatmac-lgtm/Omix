package cn.omix.module.impl.move;

import cn.omix.event.base.annotation.EventPriority;
import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.MotionEvent;
import cn.omix.event.impl.MoveInputEvent;
import cn.omix.event.impl.PacketEvent;
import cn.omix.event.impl.PlayerPositionLookEvent;
import cn.omix.event.impl.TickEvent;
import cn.omix.event.impl.UpdateEvent;
import cn.omix.event.impl.WorldEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.module.value.impl.ModeValue;
import cn.omix.module.value.impl.NumberValue;
import cn.omix.util.misc.TimerSpeedUtil;
import cn.omix.util.misc.TimerUtil;
import cn.omix.util.network.PacketUtil;
import cn.omix.util.player.RayCastUtil;
import injection.accessor.LivingEntityAccessor;
import injection.accessor.PlayerMoveC2SPacketAccessor;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class NoFall extends Module {
    private static final long PLACE_DELAY = 500L;
    private static final long PICKUP_WAIT = 20L;
    private static final long CONFIRM_TIMEOUT = 1500L;
    private static final int GRIM_LATENCY_TICKS = 5;
    private static final int GRIM_CONTROL_TICKS = 10;
    private static final double GRIM_SETBACK_RANGE_SQUARED = 1.0;
    private static final double GRIM_COLLISION_TEST_DISTANCE = 0.2;

    private final ModeValue mode = new ModeValue(
            "Mode",
            "Packet",
            "Packet",
            "Blink",
            "NoGround",
            "Spoof",
            "CubeCraft Reduce",
            "MLG",
            "Grim"
    );
    private final NumberValue distance = new NumberValue("Distance", 3.0, 0.0, 20.0, 0.5);
    private final NumberValue delay = new NumberValue("Delay", 0, 0, 10000, 50,
            () -> !mode.is("NoGround")
                    && !mode.is("CubeCraft Reduce")
                    && !mode.is("MLG")
                    && !mode.is("Grim"));
    private final BoolValue rotation = new BoolValue("Rotation", false, () -> mode.is("MLG"));
    private final TimerUtil packetDelayTimer = new TimerUtil();

    private boolean slowFalling;
    private boolean blinking;
    private boolean blinkArmed;
    private String activeMode;
    private int lastSlot = -1;
    private long lastPlace;
    private long lastPickup;
    private long waterContactTime;
    private BlockPos placedWaterPos;
    private boolean awaitingPlaceConfirmation;
    private boolean awaitingPickupConfirmation;
    private boolean mlgActionThisTick;
    private int restoreSlotTicks;

    private GrimStep grimStep = GrimStep.COMMON;
    private int grimTick;
    private int grimWaitStartTick;
    private int grimAcceptedSetbackTick;
    private int grimJumpTick;
    private int grimControlEndTick;
    private int grimDuplicateResync;
    private double grimLastGroundHeight;
    private Vec3d grimPosAtTickStart;
    private Vec3d grimWaitStartPos;
    private Vec3d grimAcceptedSetbackPos;
    private boolean grimOnGroundAtTickStart;
    private boolean grimHorizontalCollisionAtTickStart;
    private boolean grimSuppressInput;
    private boolean grimApplyJumpThisTick;
    private boolean grimFloodRecovery;
    private boolean grimCancelMovementPacket;
    private boolean grimFinishAfterCancelledMotion;
    private boolean grimPendingGroundPacket;
    private boolean grimPendingHorizontalCollision;
    private boolean grimRestoreYaw;
    private float grimPreviousYaw;
    private float grimJumpYaw;

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
            if (!mode.is("Grim")) {
                resetState(true);
            }
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
    @EventPriority(1000)
    public void onGrimPacket(PacketEvent event) {
        if (!event.isCancelled()
                && event.getType() == PacketEvent.Type.Send
                && event.getPacket() instanceof PlayerMoveC2SPacket packet
                && isGrimSilentRotationActive()) {
            ((PlayerMoveC2SPacketAccessor) packet).setPitch(90.0F);
        }
    }

    @EventTarget
    @EventPriority(1)
    public void onTick(TickEvent event) {
        if (mc.player == null) return;

        syncModeState();
        setSuffix(mode.getValue());
        mlgActionThisTick = false;
        handleScheduledSlotRestore();

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

        if (mode.is("MLG")) {
            handleMlgTick();
        }
    }

    @EventTarget
    @EventPriority(1000)
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null) return;

        syncModeState();
        if (mode.is("Grim")) {
            setSuffix(mode.getValue());
            handleGrimTick();
        }
    }

    @EventTarget
    @EventPriority(1000)
    public void onMotion(MotionEvent event) {
        if (mc.player == null) return;

        if (event.isPre()) {
            syncModeState();
        }

        if (mode.is("Grim")) {
            if (event.isPost()) {
                sendPendingGrimGroundPacket();
                if (grimFinishAfterCancelledMotion) {
                    grimFinishAfterCancelledMotion = false;
                    finishGrimJump();
                } else if (grimStep == GrimStep.APPLY_JUMP && grimApplyJumpThisTick) {
                    finishGrimJump();
                }
                restoreGrimYaw();
                return;
            }

            if (grimCancelMovementPacket) {
                event.setCancelled();
                grimFinishAfterCancelledMotion = true;
                return;
            }

            if (shouldStartGrimLanding()) {
                startGrimLanding(event);
                return;
            }

            if (grimApplyJumpThisTick) {
                // The reference overrides the vanilla post-jump cooldown back to zero.
                ((LivingEntityAccessor) mc.player).setJumpingCooldown(0);
                if (grimRestoreYaw) {
                    event.setYaw(grimJumpYaw);
                }
            }
        }

        if (!event.isPre()) return;

        if (mode.is("CubeCraft Reduce")) {
            setSuffix(mode.getValue());
            handleCubeCraftReduce(event);
            return;
        }

        if (mode.is("Blink") && blinking && event.isOnGround()) {
            finishBlink();
        }
        if (!mode.is("MLG")) return;
        setSuffix(mode.getValue());
        applyMlgRotation(event);
    }

    @EventTarget
    @EventPriority(1000)
    public void onMoveInput(MoveInputEvent event) {
        if (mc.player == null || !mode.is("Grim")) return;

        if (grimApplyJumpThisTick) {
            applyGrimJumpInput(event);
        } else if (grimSuppressInput || isGrimControlWindowActive()) {
            event.setForward(0.0F);
            event.setStrafe(0.0F);
            event.setJumping(false);
        }
    }

    @EventTarget
    public void onPlayerPositionLook(PlayerPositionLookEvent event) {
        if (!mode.is("Grim")) return;

        if (grimWaitStartPos == null
                || grimWaitStartPos.squaredDistanceTo(event.getPosition()) >= GRIM_SETBACK_RANGE_SQUARED
                || grimTick >= grimWaitStartTick + GRIM_LATENCY_TICKS) {
            return;
        }

        grimWaitStartPos = null;
        grimAcceptedSetbackPos = event.getPosition();
        grimAcceptedSetbackTick = grimTick;
        grimStep = GrimStep.WAIT_FOR_RESYNC;
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        resetGrimState();
        grimLastGroundHeight = 0.0;
        grimPosAtTickStart = null;
        grimOnGroundAtTickStart = false;
        grimHorizontalCollisionAtTickStart = false;
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

    private boolean shouldStartGrimLanding() {
        // LAZY_GRIM_PLUS_2 compares the tick-start snapshot with the real entity state;
        // packet/motion onGround may already have been spoofed by Criticals or AntiHunger.
        return grimStep == GrimStep.COMMON
                && grimTick > grimWaitStartTick + GRIM_LATENCY_TICKS
                && !grimOnGroundAtTickStart
                && mc.player.isOnGround()
                && isGrimUnsafeLanding();
    }

    private void startGrimLanding(MotionEvent event) {
        Vec3d landingPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());

        event.setCancelled();
        grimPendingGroundPacket = true;
        grimPendingHorizontalCollision = grimHorizontalCollisionAtTickStart;
        grimLastGroundHeight = grimPosAtTickStart == null ? landingPos.y : grimPosAtTickStart.y;
        grimWaitStartPos = landingPos;
        grimWaitStartTick = grimTick;
        grimAcceptedSetbackPos = null;
        grimSuppressInput = true;
        grimApplyJumpThisTick = false;
        grimFloodRecovery = false;
        grimCancelMovementPacket = false;
        grimStep = GrimStep.WAIT_FOR_RESYNC;
        startGrimControlWindow();

        if (grimPosAtTickStart != null) {
            mc.player.setPosition(grimPosAtTickStart.x, landingPos.y, grimPosAtTickStart.z);
        }
        mc.player.setOnGround(true);
    }

    private void sendPendingGrimGroundPacket() {
        if (!grimPendingGroundPacket || mc.player == null) return;

        grimPendingGroundPacket = false;
        mc.player.setOnGround(true);
        PacketUtil.sendPacketNoEvent(new PlayerMoveC2SPacket.OnGroundOnly(
                true,
                grimPendingHorizontalCollision
        ));
    }

    private void handleGrimTick() {
        grimTick++;
        if (grimControlEndTick != 0 && grimTick >= grimControlEndTick) {
            grimControlEndTick = 0;
        }
        grimOnGroundAtTickStart = mc.player.isOnGround();
        grimHorizontalCollisionAtTickStart = mc.player.horizontalCollision;
        grimPosAtTickStart = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        grimSuppressInput = false;

        if (!canUseGrim()) {
            grimLastGroundHeight = mc.player.getY();
            grimControlEndTick = 0;
            resetGrimCycle();
            return;
        }

        double playerY = mc.player.getY();
        if (mc.player.isOnGround() || playerY > grimLastGroundHeight) {
            grimLastGroundHeight = playerY;
        }

        // MotionEvent runs after vanilla has sampled and sent PlayerInput. Arm
        // one movement step before impact so the landing tick is also clean.
        if (shouldPrepareGrimLanding()) {
            startGrimControlWindow();
        }

        boolean acceptedSetback = grimStep == GrimStep.WAIT_FOR_RESYNC
                && grimAcceptedSetbackPos != null
                && grimTick <= grimAcceptedSetbackTick + 1;

        grimDuplicateResync = Math.max(0, grimDuplicateResync + (acceptedSetback ? 2 : -1));

        if (grimStep == GrimStep.WAIT_FOR_RESYNC) {
            if (grimWaitStartTick + GRIM_LATENCY_TICKS * 2 < grimTick) {
                resetGrimCycle();
                return;
            }

            if (grimAcceptedSetbackPos != null && grimTick <= grimAcceptedSetbackTick + 1) {
                Vec3d acceptedPos = grimAcceptedSetbackPos;
                grimAcceptedSetbackPos = null;
                grimWaitStartPos = acceptedPos;
                grimWaitStartTick = grimTick;
                grimJumpTick = grimTick;
                grimStep = GrimStep.APPLY_JUMP;
                grimApplyJumpThisTick = true;
                grimSuppressInput = true;

                mc.player.setPosition(acceptedPos);
                mc.player.setOnGround(true);
                ((LivingEntityAccessor) mc.player).setJumpingCooldown(0);

                if (grimDuplicateResync >= 3) {
                    grimDuplicateResync = 0;
                    grimFloodRecovery = true;
                    grimCancelMovementPacket = true;
                }
            } else if (grimWaitStartTick + 2 >= grimTick) {
                grimSuppressInput = true;
            }
        } else if (grimStep == GrimStep.APPLY_JUMP && grimJumpTick < grimTick) {
            finishGrimJump();
        }
    }

    private void applyGrimJumpInput(MoveInputEvent event) {
        float originalForward = event.getForward();
        float originalStrafe = event.getStrafe();

        event.setForward(grimFloodRecovery ? 1.0F : 0.0F);
        event.setStrafe(0.0F);
        event.setJumping(!grimFloodRecovery);

        PlayerInput input = mc.player.input.playerInput;
        mc.player.input.playerInput = new PlayerInput(
                input.forward(), input.backward(), input.left(), input.right(),
                input.jump(), input.sneak(), false
        );

        // The zero-VL window must not leak either the player's input or the
        // collision/flood recovery input into Grim's movement simulation.
        if (isGrimControlWindowActive()) {
            event.setForward(0.0F);
            event.setStrafe(0.0F);
            return;
        }

        if (grimFloodRecovery) return;

        GrimCollision collision = getGrimCollision();
        if (!collision.hasAnyCollision()) return;

        float yaw = mc.player.getYaw();
        if (originalForward > 0.0F) {
            yaw += 180.0F;
        } else if (originalForward < 0.0F) {
            // Keep the current yaw for backward input, matching LAZY_GRIM_PLUS_2.
        } else if (originalStrafe > 0.0F) {
            yaw += 90.0F;
        } else if (originalStrafe < 0.0F) {
            yaw -= 90.0F;
        } else if (!collision.forward()) {
            // The current facing is clear.
        } else if (!collision.backward()) {
            yaw += 180.0F;
        } else if (!collision.left()) {
            yaw -= 90.0F;
        } else if (!collision.right()) {
            yaw += 90.0F;
        }

        grimPreviousYaw = mc.player.getYaw();
        grimJumpYaw = MathHelper.wrapDegrees(yaw);
        grimRestoreYaw = true;
        mc.player.setYaw(grimJumpYaw);
        event.setForward(1.0F);
    }

    private void startGrimControlWindow() {
        if (!isGrimControlWindowActive()) {
            grimControlEndTick = grimTick + GRIM_CONTROL_TICKS;
        }
    }

    private boolean shouldPrepareGrimLanding() {
        if (grimStep != GrimStep.COMMON
                || grimTick <= grimWaitStartTick + GRIM_LATENCY_TICKS
                || mc.player.isOnGround()
                || mc.player.getVelocity().y >= 0.0) {
            return false;
        }

        double downwardMovement = -mc.player.getVelocity().y;
        double predictedY = mc.player.getY() - downwardMovement;
        if (predictedY > grimLastGroundHeight - distance.getValue()) {
            return false;
        }

        Box landingPath = mc.player.getBoundingBox().stretch(0.0, -downwardMovement - 0.05, 0.0);
        return mc.world.getBlockCollisions(mc.player, landingPath).iterator().hasNext();
    }

    private boolean isGrimControlWindowActive() {
        return grimControlEndTick != 0 && grimTick < grimControlEndTick;
    }

    public boolean isGrimSilentRotationActive() {
        return isEnabled() && mode.is("Grim") && isGrimControlWindowActive();
    }

    private GrimCollision getGrimCollision() {
        float yaw = mc.player.getYaw();
        Vec3d forward = Vec3d.fromPolar(0.0F, yaw).multiply(GRIM_COLLISION_TEST_DISTANCE);
        Vec3d backward = forward.negate();
        Vec3d left = Vec3d.fromPolar(0.0F, yaw + 90.0F).multiply(-GRIM_COLLISION_TEST_DISTANCE);
        Vec3d right = left.negate();
        return new GrimCollision(
                hasGrimHorizontalCollision(forward),
                hasGrimHorizontalCollision(backward),
                hasGrimHorizontalCollision(left),
                hasGrimHorizontalCollision(right)
        );
    }

    private boolean hasGrimHorizontalCollision(Vec3d movement) {
        Box movedBox = mc.player.getBoundingBox().offset(movement.x, 0.0, movement.z);
        return mc.world.getBlockCollisions(mc.player, movedBox).iterator().hasNext();
    }

    private boolean isGrimUnsafeLanding() {
        return canUseGrim()
                && mc.player.getY() <= grimLastGroundHeight - distance.getValue();
    }

    private boolean canUseGrim() {
        return mc.player != null
                && mc.world != null
                && mc.player.isAlive()
                && !mc.player.isSpectator()
                && !mc.player.isClimbing()
                && !isInFluid()
                && !mc.player.getAbilities().invulnerable
                && !mc.player.getAbilities().allowFlying
                && !mc.player.getAbilities().creativeMode;
    }

    private void finishGrimJump() {
        grimStep = GrimStep.COMMON;
        grimSuppressInput = false;
        grimApplyJumpThisTick = false;
        grimFloodRecovery = false;
        grimCancelMovementPacket = false;
        grimFinishAfterCancelledMotion = false;
    }

    private void resetGrimCycle() {
        restoreGrimYaw();
        grimStep = GrimStep.COMMON;
        grimWaitStartTick = -GRIM_LATENCY_TICKS * 2;
        grimAcceptedSetbackTick = 0;
        grimJumpTick = 0;
        grimWaitStartPos = null;
        grimAcceptedSetbackPos = null;
        grimSuppressInput = false;
        grimApplyJumpThisTick = false;
        grimFloodRecovery = false;
        grimCancelMovementPacket = false;
        grimFinishAfterCancelledMotion = false;
        grimPendingGroundPacket = false;
        grimPendingHorizontalCollision = false;
    }

    private void resetGrimState() {
        resetGrimCycle();
        grimTick = 0;
        grimControlEndTick = 0;
        grimDuplicateResync = 0;
        if (mc.player != null) {
            grimLastGroundHeight = mc.player.getY();
            grimPosAtTickStart = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
            grimOnGroundAtTickStart = mc.player.isOnGround();
            grimHorizontalCollisionAtTickStart = mc.player.horizontalCollision;
        } else {
            grimLastGroundHeight = 0.0;
            grimPosAtTickStart = null;
            grimOnGroundAtTickStart = false;
            grimHorizontalCollisionAtTickStart = false;
        }
    }

    private void restoreGrimYaw() {
        if (grimRestoreYaw && mc.player != null) {
            mc.player.setYaw(grimPreviousYaw);
        }
        grimRestoreYaw = false;
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

    private void handleCubeCraftReduce(MotionEvent event) {
        if (mc.player.getVelocity().y >= -0.5) return;

        double distanceToGround = getDistanceToGround();
        if (distanceToGround < 0.0
                || distanceToGround > 2.0
                || hasBlockAbove(2.0)
                || hasPowderSnowNearby(2.0)) {
            return;
        }

        event.setY(event.getY() + 2.0);
        event.setOnGround(false);
    }

    private double getDistanceToGround() {
        if (mc.player == null || mc.world == null) return Double.MAX_VALUE;

        Box playerBox = mc.player.getBoundingBox();
        double playerY = playerBox.minY;
        double closestDistance = Double.MAX_VALUE;

        int minX = MathHelper.floor(playerBox.minX);
        int maxX = MathHelper.floor(playerBox.maxX);
        int minZ = MathHelper.floor(playerBox.minZ);
        int maxZ = MathHelper.floor(playerBox.maxZ);
        int minY = MathHelper.floor(playerY - 20.0);

        for (int x = minX; x <= maxX; x++) {
            for (int y = MathHelper.floor(playerY); y >= minY; y--) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    var state = mc.world.getBlockState(pos);
                    if (state.isAir()) continue;

                    for (Box shapeBox : state.getCollisionShape(mc.world, pos).getBoundingBoxes()) {
                        Box collisionBox = shapeBox.offset(pos);
                        if (collisionBox.maxY > playerY
                                || collisionBox.maxX <= playerBox.minX
                                || collisionBox.minX >= playerBox.maxX
                                || collisionBox.maxZ <= playerBox.minZ
                                || collisionBox.minZ >= playerBox.maxZ) {
                            continue;
                        }

                        closestDistance = Math.min(closestDistance, playerY - collisionBox.maxY);
                    }
                }
            }
        }

        return closestDistance;
    }

    private boolean hasBlockAbove(double distance) {
        if (mc.player == null || mc.world == null) return true;

        Box playerBox = mc.player.getBoundingBox();
        Box checkBox = new Box(
                playerBox.minX,
                playerBox.maxY,
                playerBox.minZ,
                playerBox.maxX,
                playerBox.maxY + distance,
                playerBox.maxZ
        );
        return mc.world.getBlockCollisions(mc.player, checkBox).iterator().hasNext();
    }

    private boolean hasPowderSnowNearby(double distance) {
        if (mc.player == null || mc.world == null) return true;

        Box checkBox = mc.player.getBoundingBox().expand(distance, distance, distance);
        int minX = MathHelper.floor(checkBox.minX);
        int maxX = MathHelper.floor(checkBox.maxX);
        int minY = MathHelper.floor(checkBox.minY);
        int maxY = MathHelper.floor(checkBox.maxY);
        int minZ = MathHelper.floor(checkBox.minZ);
        int maxZ = MathHelper.floor(checkBox.maxZ);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (mc.world.getBlockState(new BlockPos(x, y, z)).isOf(Blocks.POWDER_SNOW)) {
                        return true;
                    }
                }
            }
        }

        return false;
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
        if (rotation.getValue() && mlgActionThisTick) {
            event.setPitch(90.0F);
        }
    }

    private void handleMlgTick() {
        if (awaitingPlaceConfirmation) {
            handlePlaceConfirmation();
            return;
        }

        if (awaitingPickupConfirmation) {
            handlePickupConfirmation();
            return;
        }

        if (placedWaterPos != null) {
            handleMlgPickup();
        } else {
            handleMlgPlace();
        }
    }

    private void handlePlaceConfirmation() {
        if (isItem(mc.player.getMainHandStack(), Items.BUCKET)) {
            awaitingPlaceConfirmation = false;
            return;
        }

        if (elapsed(lastPlace, System.currentTimeMillis()) > CONFIRM_TIMEOUT
                && !isPlacedWaterStillThere()) {
            clearMlgWaterState();
            scheduleSlotRestore();
        }
    }

    private void handlePickupConfirmation() {
        if (isItem(mc.player.getMainHandStack(), Items.WATER_BUCKET)) {
            awaitingPickupConfirmation = false;
            clearMlgWaterState();
            scheduleSlotRestore();
            return;
        }

        if (elapsed(lastPickup, System.currentTimeMillis()) > CONFIRM_TIMEOUT
                && isPlacedWaterStillThere()
                && isItem(mc.player.getMainHandStack(), Items.BUCKET)) {
            awaitingPickupConfirmation = false;
            waterContactTime = System.currentTimeMillis();
        }
    }

    private void handleMlgPickup() {
        long now = System.currentTimeMillis();
        if (placedWaterPos == null) return;

        if (!isPlacedWaterStillThere()) {
            if (elapsed(lastPlace, now) > CONFIRM_TIMEOUT) {
                clearMlgWaterState();
                scheduleSlotRestore();
            }
            return;
        }

        if (waterContactTime == 0L) {
            if (mc.player.isOnGround() || mc.player.isTouchingWater()) {
                waterContactTime = now;
            }
            return;
        }

        if (elapsed(waterContactTime, now) < PICKUP_WAIT) return;

        if (isItem(mc.player.getMainHandStack(), Items.BUCKET)) {
            mlgActionThisTick = true;
            if (useCurrentItem(rotation.getValue())) {
                awaitingPickupConfirmation = true;
                lastPickup = now;
            }
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
        mlgActionThisTick = true;
        if (useWaterBucket(rotation.getValue())) {
            lastPlace = now;
            placedWaterPos = target.getBlockPos().offset(target.getSide());
            awaitingPlaceConfirmation = true;
            waterContactTime = 0L;
        } else {
            scheduleSlotRestore();
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
        restoreSlotTicks = 0;
    }

    private void scheduleSlotRestore() {
        if (lastSlot != -1) {
            restoreSlotTicks = 1;
        }
    }

    private void handleScheduledSlotRestore() {
        if (restoreSlotTicks > 0 && --restoreSlotTicks == 0) {
            restoreSlot();
        }
    }

    private void setSelectedSlot(int slot) {
        mc.player.getInventory().setSelectedSlot(slot);
    }

    private int getWaterBucketSlot() {
        if (mc.player == null) return -1;
        for (int slot = 0; slot < 9; slot++) {
            if (isItem(mc.player.getInventory().getStack(slot), Items.WATER_BUCKET)) return slot;
        }
        return -1;
    }

    private boolean useCurrentItem(boolean silentRotation) {
        if (mc.interactionManager == null || mc.player == null) return false;

        ItemStack originalStack = mc.player.getMainHandStack().copy();
        float oldPitch = mc.player.getPitch();
        if (silentRotation) mc.player.setPitch(90.0F);
        try {
            ActionResult result = PacketUtil.runWithoutEvents(
                    () -> mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND)
            );
            return result.isAccepted();
        } finally {
            mc.player.setStackInHand(Hand.MAIN_HAND, originalStack);
            if (silentRotation) mc.player.setPitch(oldPitch);
        }
    }

    private boolean useWaterBucket(boolean silentRotation) {
        if (mc.interactionManager == null || mc.player == null
                || !isItem(mc.player.getMainHandStack(), Items.WATER_BUCKET)) {
            return false;
        }

        ItemStack originalStack = mc.player.getMainHandStack().copy();
        float oldPitch = mc.player.getPitch();
        if (silentRotation) mc.player.setPitch(90.0F);
        try {
            ActionResult result = PacketUtil.runWithoutEvents(
                    () -> mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND)
            );
            if (result.isAccepted()) mc.player.swingHand(Hand.MAIN_HAND);
            return result.isAccepted();
        } finally {
            mc.player.setStackInHand(Hand.MAIN_HAND, originalStack);
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
        awaitingPlaceConfirmation = false;
        awaitingPickupConfirmation = false;
        waterContactTime = 0L;
        lastPickup = 0L;
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
        lastPlace = 0L;
        mlgActionThisTick = false;
        packetDelayTimer.reset();
        resetGrimState();
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

    private enum GrimStep {
        COMMON,
        WAIT_FOR_RESYNC,
        APPLY_JUMP
    }

    private record GrimCollision(boolean forward, boolean backward, boolean left, boolean right) {
        private boolean hasAnyCollision() {
            return forward || backward || left || right;
        }
    }
}
