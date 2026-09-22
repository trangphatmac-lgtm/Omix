package cn.omix.module.impl.combat;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.PacketEvent;
import cn.omix.event.impl.Render3DEvent;
import cn.omix.event.impl.UpdateEvent;
import cn.omix.event.impl.WorldEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.module.value.impl.ModeValue;
import cn.omix.module.value.impl.NumberValue;
import cn.omix.util.combat.ReachServerRange;
import cn.omix.util.combat.ReachTeleportState;
import cn.omix.util.player.MovementUtil;
import cn.omix.util.network.PacketUtil;
import cn.omix.util.render.Render3D;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;
import java.util.concurrent.ThreadLocalRandom;

public final class Reach extends Module {
    private final ModeValue mode = new ModeValue("Mode", "Normal", "Normal", "Grim");
    private final BoolValue showServerPosition = new BoolValue("ShowServerPosition", true, () -> mode.is("Grim"));
    private final NumberValue minRange = new NumberValue("Min Range", 3.0, 3.0, 6.0, 0.05);
    private final NumberValue maxRange = new NumberValue("Max Range", 3.0, 3.0, 6.0, 0.05);
    private final NumberValue chance = new NumberValue("Chance", 100, 0, 100, 1);
    private final BoolValue onlyMoving = new BoolValue("Only Moving", false);
    private final BoolValue onlySprint = new BoolValue("Only Sprint", false);

    private ClientPlayerEntity sampledPlayer;
    private ClientWorld sampledWorld;
    private int sampledTick;
    private double chanceRoll;
    private double rangeRoll;
    private final ReachTeleportState<Vec3d> teleports = new ReachTeleportState<>();
    private static final Color SERVER_POSITION_COLOR = new Color(80, 180, 255, 80);

    public Reach() {
        super("Reach", Category.Combat);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!isNativeBehaviorActive() || event.getType() != PacketEvent.Type.Send) return;

        if (!mode.is("Grim")) {
            if (event.getPacket() instanceof TeleportConfirmC2SPacket) {
                // A newer vanilla correction supersedes our pending recovery.
                teleports.clear();
            } else if (restorePendingTeleport() && event.getPacket() instanceof PlayerMoveC2SPacket) {
                // This packet was built before recovery moved the player back.
                event.setCancelled();
            }
            return;
        }

        if (event.getPacket() instanceof TeleportConfirmC2SPacket confirm
                && mc.player != null && mc.world != null && mc.getNetworkHandler() != null) {
            // Vanilla has already applied PlayerPositionLook, including relative coordinates.
            teleports.remember(mc.getNetworkHandler(), mc.world, mc.player,
                    confirm.getTeleportId(), mc.player.getEntityPos());
            event.setCancelled();
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!mode.is("Grim")) restorePendingTeleport();
        else teleports.peek(mc.getNetworkHandler(), mc.world, mc.player);
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        teleports.clear();
        clearSample();
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!mode.is("Grim") || !showServerPosition.getValue() || mc.player == null) return;
        var pending = teleports.peek(mc.getNetworkHandler(), mc.world, mc.player);
        if (pending == null) return;

        Vec3d offset = pending.position().subtract(mc.player.getEntityPos());
        Render3D.drawBox(event, mc.player.getBoundingBox().offset(offset), SERVER_POSITION_COLOR, true, true);
    }

    private boolean restorePendingTeleport() {
        var handler = mc.getNetworkHandler();
        var pending = teleports.take(handler, mc.world, mc.player);
        if (pending == null || handler == null || !handler.getConnection().isOpen()) return false;

        Vec3d position = pending.position();
        mc.player.setPosition(position);
        mc.player.setVelocity(Vec3d.ZERO);
        mc.player.fallDistance = 0;
        // Recovery must not be cancelled again or buffered by another packet listener.
        PacketUtil.runWithoutEvents(() -> {
            handler.sendPacket(new TeleportConfirmC2SPacket(pending.id()));
            handler.sendPacket(new PlayerMoveC2SPacket.Full(position.x, position.y, position.z,
                    mc.player.getYaw(), mc.player.getPitch(), false, false));
            return null;
        });
        return true;
    }

    public double getRange(double vanillaRange) {
        if (getScriptMode() != null) return cn.omix.util.script.ModeHost.query(this, cn.omix.script.api.ModeHooks.ENTITY_REACH, vanillaRange, vanillaRange);
        // Both client range mixins use this method. Grim must leave both vanilla values intact.
        if (!isNativeBehaviorActive() || !mode.is("Normal") || mc.player == null || mc.world == null || mc.player.isSpectator()) {
            return vanillaRange;
        }
        return getSampledRange(vanillaRange);
    }

    public boolean shouldBlockAttack(Entity target) {
        if (!isNativeBehaviorActive() || !mode.is("Grim") || mc.player == null || mc.world == null
                || mc.player.isSpectator() || target == null) return false;

        var pending = teleports.peek(mc.getNetworkHandler(), mc.world, mc.player);
        if (pending == null) return false;

        return !ReachServerRange.contains(pending.position(), mc.player.getEyeY() - mc.player.getY(),
                target.getBoundingBox(), getSampledRange(3.0));
    }

    private double getSampledRange(double vanillaRange) {
        // Share the sample between targeting and attack checks, independently of FPS.
        if (sampledPlayer != mc.player || sampledWorld != mc.world || sampledTick != mc.player.age) {
            sampledPlayer = mc.player;
            sampledWorld = mc.world;
            sampledTick = mc.player.age;
            chanceRoll = ThreadLocalRandom.current().nextDouble();
            rangeRoll = ThreadLocalRandom.current().nextDouble();
        }
        if (onlyMoving.getValue() && !MovementUtil.isMoving()
                || onlySprint.getValue() && !mc.player.isSprinting()
                || chanceRoll * 100.0 >= chance.getValue()) {
            return vanillaRange;
        }
        double lower = Math.min(minRange.getValue(), maxRange.getValue());
        double upper = Math.max(minRange.getValue(), maxRange.getValue());
        return Math.max(vanillaRange, lower + (upper - lower) * rangeRoll);
    }

    @Override
    public void onEnable() {
        clearSample();
        teleports.clear();
    }

    @Override
    public void onDisable() {
        restorePendingTeleport();
        clearSample();
    }

    private void clearSample() {
        sampledPlayer = null;
        sampledWorld = null;
    }
}
