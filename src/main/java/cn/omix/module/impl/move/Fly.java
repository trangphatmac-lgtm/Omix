package cn.omix.module.impl.move;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.LivingUpdateEvent;
import cn.omix.event.impl.MotionEvent;
import cn.omix.event.impl.MoveEvent;
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
import cn.omix.util.player.MovementUtil;
import cn.omix.util.player.fly.GrimFlyState;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityPosition;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ThreadLocalRandom;

public final class Fly extends Module {
    public final ModeValue mode = new ModeValue("Mode", "Vanilla", "Vanilla", "SentinelA", "SentinelC", "Grim");
    private final NumberValue horizontalSpeed = new NumberValue("Horizontal Speed", 3.5F, .1F, 10, .1F, () -> !mode.is("Grim"));
    private final NumberValue verticalSpeed = new NumberValue("Vertical Speed", .7F, .1F, 5, .1F, () -> !mode.is("Grim"));
    private final ModeValue grimMode = new ModeValue("Grim Mode", "Flat", () -> mode.is("Grim"), "Flat", "Free", "Always");
    private final BoolValue velocityHud = new BoolValue("Velocity HUD", true, () -> mode.is("Grim"));
    private final BoolValue selectMaxVelocity = new BoolValue("Select Max Velocity", false,
            () -> mode.is("Grim") && grimMode.is("Free"));
    private final GrimFlyState grim = new GrimFlyState();
    private final Queue<CommonPingS2CPacket> grimPackets = new ArrayDeque<>();
    private ClientPlayNetworkHandler grimConnection;
    private ClientWorld grimWorld;
    private ClientPlayerEntity grimPlayer;
    private int tick;
    private int sentinelCCooldown;

    public Fly() {
        super("Fly", Category.Move);
        mode.onChange((previous, current) -> resetGrim(true));
        grimMode.onChange((previous, current) -> resetGrim(true));
    }

    @Override
    public void onEnable() {
        tick = 0;
        sentinelCCooldown = 0;
        resetGrim(false);
    }

    @Override
    public void onDisable() {
        resetGrim(true);
        instance.getPacketManager().getBlink().dispatch(this);
        MovementUtil.stop();
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
