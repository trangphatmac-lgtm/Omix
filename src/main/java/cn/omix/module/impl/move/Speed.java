package cn.omix.module.impl.move;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.base.annotation.EventPriority;
import cn.omix.event.impl.LivingUpdateEvent;
import cn.omix.event.impl.MotionEvent;
import cn.omix.event.impl.MoveInputEvent;
import cn.omix.event.impl.PacketEvent;
import cn.omix.event.impl.StrafeEvent;
import cn.omix.event.impl.TickEvent;
import cn.omix.management.RotationManager;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.impl.combat.Aura;
import cn.omix.module.impl.world.Scaffold;
import cn.omix.module.impl.world.ScaffoldX;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.module.value.impl.ModeValue;
import cn.omix.module.value.impl.NumberValue;
import cn.omix.util.Util;
import cn.omix.util.misc.TimerSpeedUtil;
import cn.omix.util.player.MovementUtil;
import cn.omix.util.network.PacketUtil;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;

public class Speed extends Module {
    private final ModeValue mode = new ModeValue("Mode", "Ground", "Ground", "Vulcan", "Prediction", "Prediction2", "Normal",
            "Vanilla", "Smooth Vanilla", "Hypixel NCP Hop", "Modern MMC",
            "Boost", "Flag Boost", "NCP", "Verus", "Miniblox");
    private final BoolValue damageBoost = new BoolValue("Damage Boost", false,  () -> mode.is("Vulcan"));
    private final NumberValue vanillaSpeed = new NumberValue("Speed", 1, 0.1f, 10, 0.1f,
            () -> mode.is("Ground") || mode.is("Vanilla") || mode.is("Smooth Vanilla") || mode.is("Flag Boost"));
    private final BoolValue vanillaAutoBHop = new BoolValue("Auto BHop", true, () -> mode.is("Vanilla"));
    private final NumberValue timerBoostMultiplier = new NumberValue("Timer Boost Multiplier", 0.75F, 0.1F, 1.0F, 0.05F, this::isPredictionMode);
    private final NumberValue lowTimerTicks = new NumberValue("Low Timer Ticks", 6, 1, 10, 1, this::isPredictionMode);
    private final BoolValue rotation = new BoolValue("Rotation", false, this::isPredictionMode);
    private final NumberValue multiplier = new NumberValue("Multiplier", 1.0F, 0.0F, 10.0F, 0.1F, () -> mode.is("Normal"));
    private final NumberValue friction = new NumberValue("Friction", 1.0F, 0.0F, 10.0F, 0.1F, () -> mode.is("Normal"));
    private final NumberValue strafe = new NumberValue("Strafe", 0, 0, 100, 1, () -> mode.is("Normal"));
    private final BoolValue lagBackCheck = new BoolValue("LagBack Check", true);

    private String activeMode;
    private int airTicks;
    private int flagBoostTicks;
    private float boostSpeed;

    private int ticks;
    private float yaw;
    private boolean finished;
    private boolean rotated;
    private float rotationYaw;
    private YawOffsetMode yawOffsetMode = YawOffsetMode.AIR;
    private final PredictionTimerBalance prediction2Timer = new PredictionTimerBalance();
    private String lastPredictionMode;

    public Speed() {
        super("Speed", Category.Move);
    }

    private enum YawOffsetMode {
        GROUND,
        AIR,
        CONSTANT
    }

    @Override
    public void onEnable() {
        activeMode = mode.getValue();
        resetAddedModes();
        ticks = 0;
        finished = false;
        rotated = false;
        prediction2Timer.reset();
        lastPredictionMode = null;
    }

    @Override
    public void onDisable() {
        stopMode(activeMode);
        resetAddedModes();
        activeMode = null;
        resetPredictionTimer();
        rotated = false;
        lastPredictionMode = null;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        syncMode();
        String predictionMode = isPredictionMode() ? mode.getValue() : null;
        if (mc.player == null || predictionMode == null) {
            if (lastPredictionMode != null) {
                resetPredictionTimer();
                lastPredictionMode = null;
            }
            rotated = false;
            return;
        }

        if (!predictionMode.equals(lastPredictionMode)) {
            resetPredictionTimer();
            lastPredictionMode = predictionMode;
        }

        if (mode.is("Prediction2")) {
            handlePrediction2Timer();
        } else {
            handlePredictionTimer();
        }
        handlePredictionRotation();
    }

    private void handlePredictionTimer() {
        if (canBoost()) {
            if (!mc.player.isOnGround()) {
                if (ticks < lowTimerTicks.getValue().intValue()
                        && !finished
                        && mc.player.getVelocity().y < 0.0) {
                    ticks++;
                    TimerSpeedUtil.setTimerSpeed(timerBoostMultiplier.getValue());
                    if (ticks == lowTimerTicks.getValue().intValue()) {
                        finished = true;
                    }
                }

                if (finished && ticks > 0) {
                    ticks--;
                    TimerSpeedUtil.setTimerSpeed(2.0F);
                    if (ticks == 0) {
                        TimerSpeedUtil.reset();
                        finished = false;
                    }
                }
            } else {
                resetPredictionTimer();
            }
        } else {
            resetPredictionTimer();
        }
    }

    private void handlePrediction2Timer() {
        if (mc.player.isOnGround()) {
            TimerSpeedUtil.reset();
            prediction2Timer.prepareNextJump();
            return;
        }

        if (!canBoost()) {
            TimerSpeedUtil.reset();
            return;
        }

        float timerSpeed;
        if (mc.player.getVelocity().y > 0.0) {
            timerSpeed = prediction2Timer.boost();
        } else {
            timerSpeed = prediction2Timer.slow(
                    timerBoostMultiplier.getValue(),
                    lowTimerTicks.getValue().intValue()
            );
        }

        if (PredictionTimerBalance.isNormalSpeed(timerSpeed)) {
            TimerSpeedUtil.reset();
        } else {
            TimerSpeedUtil.setTimerSpeed(timerSpeed);
        }
    }

    private void handlePredictionRotation() {
        rotated = false;
        if (!rotation.getValue() || !canBoost() || isKillAuraEnabled() || isDiggingTargetBlock()) {
            return;
        }

        switch (yawOffsetMode) {
            case GROUND -> yaw = mc.player.isOnGround() ? getYawOffsetFromKeys() : 0.0F;
            case AIR -> yaw = !mc.player.isOnGround()
                    && mc.options.forwardKey.isPressed()
                    && !mc.options.leftKey.isPressed()
                    && !mc.options.rightKey.isPressed() ? -45.0F : 0.0F;
            case CONSTANT -> yaw = getYawOffsetFromKeys();
        }

        rotationYaw = mc.player.getYaw() - yaw;
        rotated = true;
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        syncMode();
        if (mc.player != null && mc.player.isOnGround()
                && (event.getForward() != 0 || event.getStrafe() != 0) && usesAutoHop()) {
            // Suppress only this tick's input; never overwrite the physical jump key.
            event.setJumping(false);
        }
        if (isPredictionMode()
                && rotation.getValue()
                && rotated
                && canBoost()
                && !isKillAuraEnabled()
                && MovementUtil.isForwardPressed()) {
            MovementUtil.fixMovement(event, RotationManager.getAppliedYaw(rotationYaw));
        }
    }

    @EventTarget
    @EventPriority(100)
    public void onStrafe(StrafeEvent event) {
        if (!mode.is("Normal") || !canBoost()) return;

        if (mc.player.isOnGround()) {
            mc.player.setVelocity(mc.player.getVelocity().x, 0.42F, mc.player.getVelocity().z);
            MovementUtil.setSpeed(MovementUtil.getJumpMotion() * multiplier.getValue(), MovementUtil.getMoveYaw());
            return;
        }

        if (friction.getValue() != 1.0F) {
            event.setFriction(event.getFriction() * friction.getValue());
        }

        if (strafe.getValue() > 0.0F) {
            double speed = MovementUtil.getSpeed();
            double strafeRatio = strafe.getValue() / 100.0F;
            MovementUtil.setSpeed(speed * (1.0F - strafeRatio), MovementUtil.getDirectionYaw());
            MovementUtil.addSpeed(speed * strafeRatio, MovementUtil.getMoveYaw());
            MovementUtil.setSpeed(speed, MovementUtil.getDirectionYaw());
        }
    }

    @EventTarget
    @EventPriority(100)
    public void onLivingUpdate(LivingUpdateEvent event) {
        syncMode();
        if (mc.player == null || mc.world == null) {
            resetAddedModes();
            return;
        }
        airTicks = mc.player.isOnGround() ? 0 : airTicks + 1;
        updateAddedModes();
        if (!mode.is("Normal") || !canBoost()) return;

        PlayerInput input = mc.player.input.playerInput;
        mc.player.input.playerInput = new PlayerInput(
                input.forward(),
                input.backward(),
                input.left(),
                input.right(),
                false,
                input.sneak(),
                input.sprint()
        );
    }

    @EventTarget
    public void onMotion(MotionEvent e) {
        if (mc.player == null) return;

        syncMode();
        setSuffix(mode.getValue());
        if (e.isPre()) {
            switch (mode.getValue()) {
                case "Modern MMC" -> {
                    if (MovementUtil.isMoving() && mc.player.isOnGround()) mc.player.jump();
                }
                case "Flag Boost" -> handleFlagBoostMotion(e);
                case "Ground" -> {
                    if (MovementUtil.isMoving() && mc.player.isOnGround()) {
                        MovementUtil.strafe(vanillaSpeed.getValue() / 4);
                    }
                }

                case "Vulcan" -> {
                    if (MovementUtil.isMoving() && mc.player.isOnGround() && !mc.options.jumpKey.isPressed()) {
                        mc.player.jump();
                    }

                    if (damageBoost.getValue() && mc.player.hurtTime == 1) {
                        MovementUtil.strafe(MovementUtil.getSpeed() * 2);
                    }

                    MovementUtil.strafe(MovementUtil.getSpeed());
                }
            }
        }
    }


    @EventTarget
    public void onPacket(PacketEvent event) {
        syncMode();
        if (mc.player == null || mc.world == null) return;
        if (event.getType() == PacketEvent.Type.Send) {
            if (mode.is("Modern MMC") && !event.isCancelled() && event.getPacket() instanceof PlayerMoveC2SPacket) {
                PacketUtil.sendPacket(new PlayerInputC2SPacket(
                        new PlayerInput(false, false, false, false, true, true, false)));
            }
            return;
        }
        if (!(event.getPacket() instanceof PlayerPositionLookS2CPacket) || event.isCancelled()) return;
        // This correction is the handshake that starts Flag Boost's acceleration cycle.
        if (mode.is("Flag Boost") && flagBoostTicks == 3) {
            flagBoostTicks = 4;
            return;
        }
        if (lagBackCheck.getValue()) {
            Util.log("Lag detected!");
            toggle();
        }
    }

    private void updateAddedModes() {
        boolean moving = MovementUtil.isMoving();
        switch (mode.getValue()) {
            case "Vanilla" -> {
                if (vanillaAutoBHop.getValue() && mc.player.isOnGround() && moving) mc.player.jump();
                referenceStrafe(vanillaSpeed.getValue());
            }
            case "Smooth Vanilla" -> {
                if (!mc.player.isOnGround() && moving) {
                    MovementUtil.addSpeed(vanillaSpeed.getValue() / 4.0,
                            (float) Math.toDegrees(MovementUtil.getDirection()));
                }
            }
            case "Hypixel NCP Hop" -> updateHypixelHop();
            case "Boost" -> {
                if (mc.player.isTouchingWater()) return;
                if (!moving) {
                    boostSpeed = 0;
                } else if (mc.player.isOnGround()) {
                    referenceStrafe(Math.max(0.24f, boostSpeed));
                    mc.player.jump();
                    if (!mc.player.horizontalCollision) boostSpeed = Math.min(boostSpeed + 0.1f, 1f);
                } else {
                    referenceStrafe(Math.max(0.24f, MovementUtil.getSpeed()));
                }
            }
            case "Flag Boost" -> {
                if (flagBoostTicks >= 7 && flagBoostTicks != 9 && flagBoostTicks != 10 && flagBoostTicks < 12) {
                    stopHorizontal();
                    flagBoostTicks++;
                }
            }
            case "NCP" -> {
                if (mc.player.isTouchingWater() || !moving) return;
                if (mc.player.isOnGround()) {
                    mc.player.jump();
                    referenceStrafe(Math.max(0.47f + speedEffectLevel() * 0.1, baseMoveSpeed(0.2873)));
                } else {
                    referenceStrafe(Math.max(0.24f, MovementUtil.getSpeed()));
                }
            }
            case "Verus" -> {
                boolean speedEffect = mc.player.hasStatusEffect(StatusEffects.SPEED);
                if (mc.player.isOnGround()) {
                    if (moving) mc.player.jump();
                    referenceStrafe(speedEffect ? 0.53f : 0.48f);
                } else {
                    referenceStrafe(speedEffect ? 0.38f : 0.33f);
                }
            }
            case "Miniblox" -> {
                if (mc.player.isOnGround()) {
                    referenceStrafe(0.36f);
                    if (moving) mc.player.jump();
                } else {
                    switch (airTicks) {
                        case 3 -> setVerticalSpeed(-0.2);
                        case 5 -> {
                            referenceStrafe(0.7f);
                            setVerticalSpeed(0.3);
                        }
                        case 10 -> {
                            referenceStrafe(0.8f);
                            setVerticalSpeed(0.2);
                        }
                        case 18 -> {
                            referenceStrafe(0.6f);
                            setVerticalSpeed(0.2);
                        }
                        default -> referenceStrafe(0.3f);
                    }
                }
            }
        }
    }

    private void updateHypixelHop() {
        if (mc.player.isTouchingWater() || mc.player.isSpectator() || isScaffoldActive()) return;

        // Port the standalone branch: this project has no Hypixel motion-disabler state.
        if (mc.player.isOnGround()) {
            if (MovementUtil.isMoving()) mc.player.jump();
            referenceStrafe(speedAdjustedStrafe(0.481f, 0.036f, 0.12f));
        } else {
            var below = mc.world.getBlockState(mc.player.getBlockPos().down());
            if (below.isAir() || below.getBlock() instanceof SlabBlock || below.getBlock() instanceof StairsBlock) return;
            switch (airTicks) {
                case 1 -> referenceStrafe(baseMoveSpeed(0.2873));
                case 10 -> {
                    if (mc.player.hurtTime == 0) {
                        setVerticalSpeed(-0.28);
                        referenceStrafe(speedAdjustedStrafe(0.305f, 0.036f, 0.044f));
                    }
                }
                case 11 -> referenceStrafe(baseMoveSpeed(0.2713));
                case 12 -> stopHorizontal();
            }
        }
    }

    private void handleFlagBoostMotion(MotionEvent event) {
        if (flagBoostTicks >= 12) {
            flagBoostTicks = 0;
            return;
        }
        if (flagBoostTicks < 3) flagBoostTicks++;
        switch (flagBoostTicks) {
            case 3 -> event.setY(event.getY() - (mc.player.isOnGround() ? 1 : 3));
            case 4, 6 -> flagBoostTicks++;
            case 5 -> {
                event.setCancelled();
                referenceStrafe(vanillaSpeed.getValue());
                flagBoostTicks++;
            }
            case 9, 10 -> {
                referenceStrafe(vanillaSpeed.getValue());
                flagBoostTicks++;
            }
        }
    }

    private boolean usesAutoHop() {
        return switch (mode.getValue()) {
            case "Vanilla" -> vanillaAutoBHop.getValue();
            case "Hypixel NCP Hop" -> !mc.player.isTouchingWater() && !mc.player.isSpectator() && !isScaffoldActive();
            case "Boost", "NCP" -> !mc.player.isTouchingWater();
            case "Modern MMC", "Verus", "Miniblox" -> true;
            default -> false;
        };
    }

    private boolean isScaffoldActive() {
        Scaffold scaffold = getModule(Scaffold.class);
        ScaffoldX scaffoldX = getModule(ScaffoldX.class);
        return (scaffold != null && scaffold.isEnabled()) || (scaffoldX != null && scaffoldX.isEnabled());
    }

    private int speedEffectLevel() {
        var effect = mc.player.getStatusEffect(StatusEffects.SPEED);
        return effect == null ? 0 : effect.getAmplifier() + 1;
    }

    private double baseMoveSpeed(double base) {
        return base * (1.0 + 0.2 * speedEffectLevel());
    }

    private float speedAdjustedStrafe(float base, float firstLevelBonus, float higherLevelBonus) {
        int level = speedEffectLevel();
        return base + (level == 0 ? 0 : level == 1 ? firstLevelBonus : higherLevelBonus);
    }

    private void setVerticalSpeed(double speed) {
        mc.player.setVelocity(mc.player.getVelocity().x, speed, mc.player.getVelocity().z);
    }

    private void stopHorizontal() {
        if (mc.player != null) mc.player.setVelocity(0, mc.player.getVelocity().y, 0);
    }

    private void referenceStrafe(double speed) {
        // Krs strafe stops horizontal motion without input; the shared utility here does not.
        if (MovementUtil.isMoving()) MovementUtil.strafe(speed);
        else stopHorizontal();
    }

    private void resetAddedModes() {
        airTicks = 0;
        flagBoostTicks = 0;
        boostSpeed = 0;
    }

    private void stopMode(String previousMode) {
        if (previousMode == null) return;
        switch (previousMode) {
            case "Vanilla", "Flag Boost", "NCP", "Verus", "Boost", "Miniblox" -> stopHorizontal();
        }
    }

    private void syncMode() {
        if (mode.getValue().equals(activeMode)) return;
        stopMode(activeMode);
        resetAddedModes();
        resetPredictionTimer();
        rotated = false;
        lastPredictionMode = null;
        activeMode = mode.getValue();
    }

    public boolean isPredictionRotationActive() {
        return isEnabled() && isPredictionMode() && rotated;
    }

    public float getPredictionRotationYaw() {
        return rotationYaw;
    }

    private float getYawOffsetFromKeys() {
        if (mc.options.forwardKey.isPressed() && mc.options.leftKey.isPressed()) return 45.0F;
        if (mc.options.forwardKey.isPressed() && mc.options.rightKey.isPressed()) return -45.0F;
        if (mc.options.backKey.isPressed() && mc.options.leftKey.isPressed()) return 135.0F;
        if (mc.options.backKey.isPressed() && mc.options.rightKey.isPressed()) return -135.0F;
        if (mc.options.backKey.isPressed()) return 180.0F;
        if (mc.options.leftKey.isPressed()) return 90.0F;
        if (mc.options.rightKey.isPressed()) return -90.0F;
        return 0.0F;
    }

    private boolean canBoost() {
        if (mc.player == null || mc.world == null) return false;

        ScaffoldX scaffoldX = getModule(ScaffoldX.class);
        Scaffold scaffold = getModule(Scaffold.class);
        return !scaffoldX.isEnabled()
                && !scaffold.isEnabled()
                && MovementUtil.isForwardPressed()
                && mc.player.getHungerManager().getFoodLevel() > 6
                && !mc.player.isSneaking()
                && !mc.player.isTouchingWater()
                && !mc.player.isInLava()
                && !isInCobweb();
    }

    private boolean isPredictionMode() {
        return mode.is("Prediction") || mode.is("Prediction2");
    }

    private boolean isKillAuraEnabled() {
        return getModule(Aura.class).isEnabled();
    }

    private boolean isDiggingTargetBlock() {
        if (mc.crosshairTarget == null || mc.crosshairTarget.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        return mc.options.attackKey.isPressed()
                || (mc.interactionManager != null && mc.interactionManager.isBreakingBlock());
    }

    private boolean isInCobweb() {
        Box box = mc.player.getBoundingBox();
        int minX = MathHelper.floor(box.minX);
        int maxX = MathHelper.floor(box.maxX + 1.0);
        int minY = MathHelper.floor(box.minY);
        int maxY = MathHelper.floor(box.maxY + 1.0);
        int minZ = MathHelper.floor(box.minZ);
        int maxZ = MathHelper.floor(box.maxZ + 1.0);

        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    if (mc.world.getBlockState(new BlockPos(x, y, z)).isOf(Blocks.COBWEB)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void resetPredictionTimer() {
        finished = false;
        TimerSpeedUtil.reset();
        ticks = 0;
        prediction2Timer.reset();
    }
}
