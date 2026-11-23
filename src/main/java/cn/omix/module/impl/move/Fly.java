package cn.omix.module.impl.move;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.LivingUpdateEvent;
import cn.omix.event.impl.MotionEvent;
import cn.omix.event.impl.MoveEvent;
import cn.omix.event.impl.MoveInputEvent;
import cn.omix.event.impl.PacketEvent;
import cn.omix.event.impl.Render2DEvent;
import cn.omix.event.impl.WorldEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.impl.combat.TPAura;
import cn.omix.module.impl.combat.TargetStrafe;
import cn.omix.module.impl.exploits.Disabler;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.module.value.impl.ModeValue;
import cn.omix.module.value.impl.NumberValue;
import cn.omix.util.misc.TimerSpeedUtil;
import cn.omix.util.network.PacketUtil;
import cn.omix.util.player.MovementUtil;
import cn.omix.util.player.fly.GrimFlyState;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityPosition;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ThreadLocalRandom;

public final class Fly extends Module {
    public final ModeValue mode = new ModeValue("Mode", "Vanilla", "Vanilla", "SentinelA", "SentinelC", "Grim", "NCP", "NCPSetback");
    private final NumberValue horizontalSpeed = new NumberValue("Horizontal Speed", 3.5F, .1F, 10, .1F, () -> !mode.is("Grim") && !mode.is("NCP") && !mode.is("NCPSetback"));
    private final NumberValue verticalSpeed = new NumberValue("Vertical Speed", .7F, .1F, 5, .1F, () -> !mode.is("Grim") && !mode.is("NCP") && !mode.is("NCPSetback"));
    private final ModeValue grimMode = new ModeValue("Grim Mode", "Flat", () -> mode.is("Grim"), "Flat", "Free", "Always");
    private final BoolValue velocityHud = new BoolValue("Velocity HUD", true, () -> mode.is("Grim"));
    private final BoolValue selectMaxVelocity = new BoolValue("Select Max Velocity", false,
            () -> mode.is("Grim") && grimMode.is("Free"));
    private final NumberValue ncpSpeed = new NumberValue("NCP Speed", 4.2F, 3.5F, 5.5F, .1F, () -> mode.is("NCP"));
    private final NumberValue ncpDecay = new NumberValue("NCP Decay", 159F, 159F, 600F, 1F, () -> mode.is("NCP"));
    private final NumberValue ncpBoostTimer = new NumberValue("NCP Boost Timer", 2F, 1F, 3.5F, .1F, () -> mode.is("NCP"));
    private final NumberValue ncpBoostTicks = new NumberValue("NCP Boost Ticks", 15F, 1F, 40F, 1F, () -> mode.is("NCP"));
    private final BoolValue ncpTimer = new BoolValue("NCP Timer", false, () -> mode.is("NCP"));
    private final NumberValue setbackSpeed = new NumberValue("NCPSetback Speed", 1F, .1F, 5F, .1F, () -> mode.is("NCPSetback"));
    private final NumberValue setbackDuration = new NumberValue("NCPSetback Duration", 3000F, 500F, 10000F, 100F, () -> mode.is("NCPSetback"));
    private final NumberValue setbackTimer = new NumberValue("NCPSetback Timer", 2F, 1F, 5F, .1F, () -> mode.is("NCPSetback"));
    private final GrimFlyState grim = new GrimFlyState();
    private final Queue<CommonPingS2CPacket> grimPackets = new ArrayDeque<>();
    private ClientPlayNetworkHandler grimConnection;
    private ClientWorld grimWorld;
    private ClientPlayerEntity grimPlayer;
    private int tick;
    private int sentinelCCooldown;
    private enum SetbackStage { JUMP, FREEZE, FLY }
    private volatile SetbackStage setbackStage = SetbackStage.JUMP;
    private int setbackAirTicks;
    private long setbackFlightStart;
    private int ncpPhase;
    private int ncpBoostTick = Integer.MAX_VALUE;
    private double ncpMoveSpeed;
    private Double ncpMotionY;
    private volatile boolean ncpCorrection;

    public Fly() {
        super("Fly", Category.Move);
        mode.onChange((previous, current) -> {
            resetGrim(true);
            if (isEnabled() && mc.player != null
                    && (previous.equalsIgnoreCase("NCP") || previous.equalsIgnoreCase("NCPSetback"))) {
                Vec3d velocity = mc.player.getVelocity();
                mc.player.setVelocity(0.0, velocity.y, 0.0);
            }
            resetNcp();
        });
        grimMode.onChange((previous, current) -> resetGrim(true));
    }

    @Override
    public void onEnable() {
        tick = 0;
        sentinelCCooldown = 0;
        resetGrim(false);
        resetNcp();
    }

    @Override
    public void onDisable() {
        resetGrim(true);
        resetNcp();
        instance.getPacketManager().getBlink().dispatch(this);
        if (mc.player != null && (mode.is("NCP") || mode.is("NCPSetback"))) {
            Vec3d velocity = mc.player.getVelocity();
            mc.player.setVelocity(0.0, velocity.y, 0.0);
        } else {
            MovementUtil.stop();
        }
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (mc.player == null || check()) return;
        setSuffix(mode.getValue());

        if (mode.is("Grim")) {
            if (event.isPre() && grimContext() && grim.onMotion(grimMode.getValue(), selectMaxVelocity.getValue(),
                    mc.player.getY(), mc.player.getVelocity().y,
                    mc.options.jumpKey.isPressed(), mc.options.sneakKey.isPressed())) {
                grimConnection.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            }
            return;
        }
        if (mode.is("NCP") || mode.is("NCPSetback")) return;

        TPAura tpAura = getModule(TPAura.class);
        if (mode.is("SentinelA") && tpAura != null && tpAura.isBlinkAttackActive()) {
            return;
        }

        TargetStrafe ts = getModule(TargetStrafe.class);
        boolean strafing = ts.isEnabled() && ts.getTarget() != null && (!ts.getSpace().getValue() || mc.options.jumpKey.isPressed());

        double targetY = 0.0;
        if (!strafing) {
            if (mc.options.jumpKey.isPressed()) {
                targetY = verticalSpeed.getValue().doubleValue();
            } else if (mc.options.sneakKey.isPressed()) {
                targetY = -verticalSpeed.getValue().doubleValue();
            }
        }

        switch (mode.getValue()) {
            case "Vanilla" -> {
                if (!strafing) {
                    mc.player.setVelocity(0.0, targetY, 0.0);
                } else {
                    mc.player.setVelocity(mc.player.getVelocity().x, 0.0, mc.player.getVelocity().z);
                }
                MovementUtil.strafe(horizontalSpeed.getValue().doubleValue());
            }

            case "SentinelA" -> {
                if (tick++ % 6 == 0) {
                    instance.getPacketManager().getBlink().start(this);
                    mc.player.setVelocity(strafing ? mc.player.getVelocity().x : 0.0, mc.player.getVelocity().y, strafing ? mc.player.getVelocity().z : 0.0);
                    MovementUtil.strafe(horizontalSpeed.getValue().doubleValue());
                } else if (!MovementUtil.isMoving()) {
                    mc.player.setVelocity(0.0, mc.player.getVelocity().y, 0.0);
                } else {
                    instance.getPacketManager().getBlink().dispatch(this);
                }
                mc.player.setVelocity(mc.player.getVelocity().x, targetY, mc.player.getVelocity().z);
            }
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (mc.player != null && mode.is("NCP")) {
            tickNcp();
            return;
        }
        if (mc.player != null && mode.is("NCPSetback")) {
            tickSetback();
            return;
        }
        if (mc.player == null || !mode.is("SentinelC")) return;

        if (sentinelCCooldown > 0) {
            sentinelCCooldown--;
        }

        if (mc.player.isOnGround() || sentinelCCooldown > 0) return;

        double motionY;
        if (mc.options.sneakKey.isPressed()) {
            motionY = -0.4;
        } else if (mc.options.jumpKey.isPressed()) {
            motionY = 0.42;
        } else {
            motionY = 0.2;
        }

        mc.player.setVelocity(mc.player.getVelocity().x, motionY, mc.player.getVelocity().z);
        MovementUtil.strafe(ThreadLocalRandom.current().nextDouble(0.33, 0.34));
        sentinelCCooldown = 6;
    }

    @EventTarget
    public void onMove(MoveEvent event) {
        if (event.getType() == MovementType.SELF && mc.player != null && mode.is("NCP")) {
            double y = ncpMotionY == null ? event.getY() : ncpMotionY;
            if (ncpMotionY != null) {
                Vec3d velocity = mc.player.getVelocity();
                mc.player.setVelocity(velocity.x, y, velocity.z);
                ncpMotionY = null;
            }
            setStrafe(event, Math.max(ncpMoveSpeed, ncpBaseMoveSpeed()));
            event.setY(y);
            return;
        }
        if (event.getType() == MovementType.SELF && mc.player != null && mode.is("NCPSetback")) {
            if (setbackStage == SetbackStage.JUMP) return;
            double speed = setbackSpeed.getValue();
            double y = 0.0;
            if (setbackStage == SetbackStage.FLY) {
                boolean jump = mc.player.input.playerInput.jump();
                boolean sneak = mc.player.input.playerInput.sneak();
                if (jump && !sneak) y = speed;
                else if (sneak && !jump) y = -speed;
                setStrafe(event, speed);
            } else {
                event.setX(0.0);
                event.setZ(0.0);
            }
            event.setY(y);
            mc.player.setVelocity(event.getX(), y, event.getZ());
            return;
        }
        if (mc.player == null || !mode.is("SentinelC")) return;

        if (!MovementUtil.isMoving()) {
            event.setX(0.0);
            event.setZ(0.0);
            return;
        }

        double speed = Math.hypot(event.getX(), event.getZ());
        double direction = MovementUtil.getDirection();
        event.setX(-Math.sin(direction) * speed);
        event.setZ(Math.cos(direction) * speed);
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (mode.is("NCPSetback") && setbackStage == SetbackStage.JUMP
                && mc.player != null && mc.player.isOnGround()) event.setJumping(true);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.player == null || event.isCancelled()) return;
        if (mode.is("NCP")) {
            if (event.getType() == PacketEvent.Type.Received) {
                if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket velocity
                        && velocity.getEntityId() == mc.player.getId()) event.setCancelled();
                else if (event.getPacket() instanceof PlayerPositionLookS2CPacket) ncpCorrection = true;
            }
        } else if (mode.is("NCPSetback") && setbackStage == SetbackStage.FREEZE) {
            if (event.getType() == PacketEvent.Type.Received
                    && event.getPacket() instanceof PlayerPositionLookS2CPacket) setbackStage = SetbackStage.FLY;
            else if (event.getType() == PacketEvent.Type.Send
                    && event.getPacket() instanceof PlayerMoveC2SPacket) event.setCancelled();
        }
    }

    private void tickNcp() {
        if (ncpCorrection) {
            ncpCorrection = false;
            ncpMoveSpeed = ncpBaseMoveSpeed();
            ncpBoostTick = Integer.MAX_VALUE;
        }
        if (ncpPhase == 0) {
            if (mc.player.isOnGround()) {
                ncpSelfDamage();
                ncpPhase = 1;
                return;
            }
            ncpPhase = 4;
            ncpMoveSpeed = ncpBaseMoveSpeed() * ncpSpeed.getValue();
            ncpBoostTick = 0;
        } else if (ncpPhase == 1) {
            ncpMoveSpeed = ncpBaseMoveSpeed(.3);
            ncpPhase = 2;
            return;
        } else if (ncpPhase == 2) {
            ncpMoveSpeed = ncpCurrentSpeed() * 2.149;
            ncpMotionY = .3999;
            ncpPhase = 3;
            return;
        } else if (ncpPhase == 3) {
            ncpMoveSpeed = ncpBaseMoveSpeed() * ncpSpeed.getValue();
            ncpBoostTick = 0;
            ncpPhase = 4;
        } else {
            ncpMoveSpeed = ncpCurrentSpeed() - ncpCurrentSpeed() / ncpDecay.getValue();
            ncpMotionY = 0.0;
            if (!mc.world.getBlockCollisions(mc.player,
                    mc.player.getBoundingBox().offset(0.0, -1.0E-4, 0.0)).iterator().hasNext()) {
                mc.player.setPosition(mc.player.getX(), mc.player.getY() - 1.0E-14, mc.player.getZ());
            }
        }
        float normal = ncpTimer.getValue() ? 1.0766F : 1F;
        float boost = ncpBoostTimer.getValue();
        int ticks = ncpBoostTicks.getValue().intValue();
        float speed = ncpBoostTick < ticks && boost > normal
                ? boost - (boost - normal) * ncpBoostTick / ticks : normal;
        if (ncpBoostTick < Integer.MAX_VALUE) ncpBoostTick++;
        TimerSpeedUtil.setTemporaryOverride(this, speed);
    }

    private void tickSetback() {
        if (setbackStage == SetbackStage.JUMP) {
            setbackAirTicks = mc.player.isOnGround() ? 0 : setbackAirTicks + 1;
            if (setbackAirTicks >= 3) setbackStage = SetbackStage.FREEZE;
        } else if (setbackStage == SetbackStage.FLY) {
            if (setbackFlightStart == 0L) setbackFlightStart = System.currentTimeMillis();
            if (System.currentTimeMillis() - setbackFlightStart >= setbackDuration.getValue().longValue()) {
                setEnabled(false);
            } else {
                TimerSpeedUtil.setTemporaryOverride(this, setbackTimer.getValue());
            }
        }
    }

    private double ncpCurrentSpeed() {
        return mc.player.horizontalCollision || !MovementUtil.isMoving() ? ncpBaseMoveSpeed() : ncpMoveSpeed;
    }

    private double ncpBaseMoveSpeed() { return ncpBaseMoveSpeed(.2873); }

    private double ncpBaseMoveSpeed(double base) {
        var speed = mc.player.getStatusEffect(StatusEffects.SPEED);
        var slowness = mc.player.getStatusEffect(StatusEffects.SLOWNESS);
        int speedLevel = speed == null ? 0 : speed.getAmplifier() + 1;
        int slownessLevel = slowness == null ? 0 : slowness.getAmplifier() + 1;
        return base * (1.0 + .2 * speedLevel) / (1.0 + .2 * slownessLevel);
    }

    private void ncpSelfDamage() {
        double offset = .425;
        int iterations = (int) Math.ceil(3.0 / offset);
        Vec3d pos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        for (int i = 0; i <= iterations; i++) {
            PacketUtil.sendPacketNoEvent(new PlayerMoveC2SPacket.PositionAndOnGround(
                    pos.add(0.0, offset, 0.0), false, mc.player.horizontalCollision));
            PacketUtil.sendPacketNoEvent(new PlayerMoveC2SPacket.PositionAndOnGround(
                    pos, i == iterations, mc.player.horizontalCollision));
        }
    }

    private void setStrafe(MoveEvent event, double speed) {
        if (!MovementUtil.isMoving()) {
            event.setX(0.0);
            event.setZ(0.0);
            return;
        }
        double direction = MovementUtil.getDirection();
        event.setX(-Math.sin(direction) * speed);
        event.setZ(Math.cos(direction) * speed);
    }

    private void resetNcp() {
        TimerSpeedUtil.clearTemporaryOverride(this);
        ncpPhase = 0;
        ncpBoostTick = Integer.MAX_VALUE;
        ncpMoveSpeed = 0.0;
        ncpMotionY = null;
        ncpCorrection = false;
        setbackStage = SetbackStage.JUMP;
        setbackAirTicks = 0;
        setbackFlightStart = 0L;
    }

    private boolean check() {
        return getModule(Disabler.class).isWaiting();
    }

    /** Called at client-thread packet application, after cancelled receives have been filtered. */
    public static Fly activeGrim() {
        if (instance == null || instance.getModuleManager() == null) return null;
        Fly fly = instance.getModuleManager().getModule(Fly.class);
        return fly != null && fly.isNativeBehaviorActive() && fly.mode.is("Grim") ? fly : null;
    }

    public boolean receiveGrimPacket(Packet<?> packet) {
        if (!grimContext()) return false;
        if (packet instanceof CommonPingS2CPacket ping) {
            grimPackets.add(ping);
            return true;
        }
        if (packet instanceof PlayerPositionLookS2CPacket correction) {
            if (grimMode.is("Free")) {
                double y = EntityPosition.apply(EntityPosition.fromEntity(mc.player), correction.change(),
                        correction.relatives()).position().y;
                grim.captureCorrectionHeight(y);
            }
            releaseGrimPackets();
        }
        return false;
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        resetGrim(false);
        resetNcp();
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || !mode.is("Grim") || !velocityHud.getValue()) return;
        var velocity = mc.player.getVelocity();
        String text = String.format(Locale.US, "x: %.3f y: %.3f z: %.3f", velocity.x, velocity.y, velocity.z);
        int x = (mc.getWindow().getScaledWidth() - mc.textRenderer.getWidth(text)) / 2;
        int y = mc.getWindow().getScaledHeight() / 2 + 30;
        event.getContext().drawText(mc.textRenderer, text, x, y, 0xFFFFFFFF, true);
    }

    private boolean grimContext() {
        if (grimConnection != mc.getNetworkHandler() || grimWorld != mc.world || grimPlayer != mc.player) {
            grimPackets.clear();
            grim.reset();
            grimConnection = mc.getNetworkHandler();
            grimWorld = mc.world;
            grimPlayer = mc.player;
        }
        return grimConnection != null && grimWorld != null && grimPlayer != null;
    }

    private void releaseGrimPackets() {
        if (!grimContext()) return;
        CommonPingS2CPacket packet;
        while ((packet = grimPackets.poll()) != null) {
            packet.apply(grimConnection);
        }
    }

    private void resetGrim(boolean releasePackets) {
        if (!mc.isOnThread()) {
            mc.execute(() -> resetGrim(releasePackets));
            return;
        }
        if (releasePackets) releaseGrimPackets();
        grimPackets.clear();
        grim.reset();
        grimConnection = null;
        grimWorld = null;
        grimPlayer = null;
    }
}
