package cn.omix.module.impl.player;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.MotionEvent;
import cn.omix.event.impl.MoveInputEvent;
import cn.omix.event.impl.Render3DEvent;
import cn.omix.event.impl.UpdateEvent;
import cn.omix.event.impl.WorldEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.util.Util;
import cn.omix.util.network.PacketUtil;
import cn.omix.util.player.SpectatorTeleportState;
import cn.omix.util.player.TeleportTargetUtil;
import cn.omix.util.render.Render3D;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerAbilitiesS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.awt.Color;
import java.util.Locale;

/** TP2Bed's spectator transition, with a LookTP-style user-selected destination. */
public final class SpectatorTP extends Module {
    private final BoolValue alwaysTop = new BoolValue("Always Top", true);
    private final BoolValue autoDisable = new BoolValue("Auto Disable", true);
    private final BoolValue showTarget = new BoolValue("Show Target", true);
    private final BoolValue debug = new BoolValue("Debug", false);
    private final SpectatorTeleportState state = new SpectatorTeleportState();

    private ClientWorld targetWorld;
    private ClientPlayerEntity cyclePlayer;
    private boolean selecting;

    public SpectatorTP() {
        super("SpectatorTP", Category.Player);
    }

    @Override
    public void onEnable() {
        reset();
        updateContext();
        Util.log("SpectatorTP: Left Alt + Use to select a destination");
    }

    @Override
    public void onDisable() {
        reset();
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        reset();
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!updateContext()) return;

        boolean selectPressed = mc.currentScreen == null
                && InputUtil.isKeyPressed(mc.getWindow(), InputUtil.GLFW_KEY_LEFT_ALT)
                && mc.options.useKey.isPressed();
        boolean selectTarget = selectPressed && !selecting;
        selecting = selectPressed;

        if (state.isInGrace()) {
            lockPosition();
            if (state.tickGrace()) {
                debug("Grace period finished");
                setSuffix("Target selected");
                if (autoDisable.getValue()) setEnabled(false);
            }
            return;
        }
        if (!selectTarget) return;

        Vec3d target = TeleportTargetUtil.getTargetPosition(TeleportTargetUtil.raycastTarget(), alwaysTop.getValue());
        if (!state.selectTarget(target)) {
            Util.log("&cSpectatorTP: No valid destination at the crosshair");
            return;
        }
        setSuffix("Target selected");
        Util.log("&aSpectatorTP: Destination selected " + formatPosition(target));
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (event.isPost() || !updateContext() || !state.isInGrace()) return;
        lockPosition();
        Vec3d target = state.getTeleportTarget();
        event.setX(target.x);
        event.setY(target.y);
        event.setZ(target.z);
        event.setOnGround(true);
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (!updateContext() || !state.isInGrace()) return;
        event.setForward(0.0F);
        event.setStrafe(0.0F);
        event.setJumping(false);
        event.setSneaking(false);
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!showTarget.getValue() || mc.world != targetWorld || state.getTarget() == null) return;
        Vec3d target = state.getTarget();
        Render3D.drawBox(event, new Box(
                target.x - 0.3, target.y, target.z - 0.3,
                target.x + 0.3, target.y + 0.1, target.z + 0.3
        ), new Color(85, 170, 255, 75), true, true);
    }

    /** Called after forceMainThread, so state changes follow vanilla packet application order. */
    public boolean handlePacket(Packet<?> packet) {
        if (!isNativeBehaviorActive() || !mc.isOnThread() || !updateContext() || mc.player.age < 20) return false;

        if (packet instanceof PlayerPositionLookS2CPacket correction) {
            boolean alreadyTeleporting = state.isInGrace();
            Vec3d target = state.correctionTarget();
            if (target == null) return false;

            PacketUtil.sendPacketNoEvent(new TeleportConfirmC2SPacket(correction.teleportId()));
            sendPosition(target);
            if (alreadyTeleporting) debug("Intercepted a position correction during grace");
            else notifyTeleport(target);
            return true;
        }

        if (packet instanceof GameStateChangeS2CPacket gameState
                && gameState.getReason() == GameStateChangeS2CPacket.GAME_MODE_CHANGED) {
            if ((int) gameState.getValue() == GameMode.SPECTATOR.getIndex()) {
                if (state.enterSpectator()) debug("Entered spectator mode");
            } else {
                Vec3d target = state.leaveSpectator();
                if (target != null) {
                    sendPosition(target);
                    notifyTeleport(target);
                }
            }
        }

        // Some servers represent temporary spectating through abilities in survival mode.
        if (packet instanceof PlayerAbilitiesS2CPacket abilities
                && state.observeFlying(abilities.isFlying() && !abilities.isCreativeMode())) {
            debug("Entered spectator state through abilities");
        }
        return false;
    }

    private boolean updateContext() {
        if (mc.world == null || mc.player == null) {
            reset();
            return false;
        }
        if (targetWorld != mc.world) {
            reset();
            targetWorld = mc.world;
        }
        if (cyclePlayer != mc.player) {
            cyclePlayer = mc.player;
            state.resetCycle(mc.player.isSpectator(), mc.player.getAbilities().flying && !mc.player.isCreative());
            setSuffix(state.getTarget() == null ? "" : "Target selected");
        }
        return true;
    }

    private void sendPosition(Vec3d target) {
        PacketUtil.sendPacketNoEvent(new PlayerMoveC2SPacket.PositionAndOnGround(target, true, false));
        lockPosition();
    }

    private void lockPosition() {
        Vec3d target = state.getTeleportTarget();
        if (mc.player == null || target == null) return;
        mc.player.setPosition(target);
        mc.player.setVelocity(Vec3d.ZERO);
    }

    private void notifyTeleport(Vec3d target) {
        setSuffix("Grace");
        Util.log("&aSpectatorTP: Teleport sent " + formatPosition(target));
    }

    private String formatPosition(Vec3d position) {
        return String.format(Locale.ROOT, "(%.2f, %.2f, %.2f)", position.x, position.y, position.z);
    }

    private void debug(String message) {
        if (debug.getValue()) Util.log("&7SpectatorTP: " + message);
    }

    private void reset() {
        state.clear();
        targetWorld = null;
        cyclePlayer = null;
        selecting = false;
        setSuffix("");
    }
}
