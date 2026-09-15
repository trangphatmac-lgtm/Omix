package cn.omix.module.impl.move;

import cn.omix.event.base.annotation.EventPriority;
import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.*;
import cn.omix.management.RotationManager;
import cn.omix.management.rotation.RotationRequest;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.impl.combat.Velocity;
import cn.omix.module.impl.world.Scaffold;
import cn.omix.module.impl.world.ScaffoldX;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.module.value.impl.ModeValue;
import cn.omix.module.value.impl.NumberValue;
import cn.omix.util.LongJumpMotionQueue;
import cn.omix.util.Util;
import cn.omix.util.misc.TimerSpeedUtil;
import cn.omix.util.network.PacketUtil;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;

public class LongJump extends Module {
    private final ModeValue mode = new ModeValue("Mode", "Fireball", "Fireball", "Windcharge");
    private final NumberValue targetPitch = new NumberValue("Target Pitch", 80, -90, 90, 1);
    private final NumberValue targetHeight = new NumberValue("Target Height", 0.5, 0, 10, 0.05);
    private final BoolValue multi = new BoolValue("Multi", false);
    private final NumberValue multiTimes = new NumberValue("Multi Times", 3, 1, 10, 1, multi::getValue);
    private final NumberValue rotationSpeed = new NumberValue("Rotation Speed", 180, 0, 180, 5);
    private final BoolValue enableScaffold = new BoolValue("Enable Scaffold", false);

    private enum Phase { WAITING, AIMING, CHARGING, FLYING }
    private Phase phase = Phase.WAITING;
    private volatile Session session;
    private boolean wasVelocityEnabled;
    private boolean wasScaffoldEnabled;
    private boolean wasScaffoldXEnabled;
    private boolean touchedScaffold;
    private int originalSlot;
    private float launchYaw;
    private float appliedYaw;
    private float appliedPitch;
    private long lastUseNanos;

    public LongJump() {
        super("LongJump", Category.Move);
    }

    @Override
    public void onEnable() {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) {
            setEnabled(false);
            return;
        }
        phase = Phase.WAITING;
        touchedScaffold = false;
        originalSlot = mc.player.getInventory().getSelectedSlot();
        wasVelocityEnabled = getModule(Velocity.class).isEnabled();
        wasScaffoldEnabled = getModule(Scaffold.class).isEnabled();
        wasScaffoldXEnabled = getModule(ScaffoldX.class).isEnabled();
        session = new Session(mc.player, mc.world, mc.getNetworkHandler(),
                mode.is("Fireball") ? Items.FIRE_CHARGE : Items.WIND_CHARGE,
                targetPitch.getValue(), targetHeight.getValue(), rotationSpeed.getValue(),
                multi.getValue() ? multiTimes.getValue().intValue() : 1, enableScaffold.getValue());
        getModule(Velocity.class).setEnabled(false);
        if (findSlot(session) < 0) abort("快捷栏中没有 " + mode.getValue());
    }

    @Override
    public void onDisable() {
        Session old = session;
        session = null; // Invalidate already-scheduled callbacks before restoring anything.
        if (old == null) return;
        old.motions.close();
        TimerSpeedUtil.clearTemporaryOverride(old);
        if (touchedScaffold) {
            getModule(Scaffold.class).setEnabled(wasScaffoldEnabled);
            getModule(ScaffoldX.class).setEnabled(wasScaffoldXEnabled);
        }
        getModule(Velocity.class).setEnabled(wasVelocityEnabled);
        if (sameContext(old)) {
            mc.player.getInventory().setSelectedSlot(originalSlot);
            PacketUtil.sendPacket(new UpdateSelectedSlotC2SPacket(originalSlot));
        }
        phase = Phase.WAITING;
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        setEnabled(false);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        Session active = session;
        if (!validate(active)) return;
        getModule(Velocity.class).setEnabled(false);
        double vy = mc.player.getVelocity().y;
        if (phase != Phase.WAITING && LongJumpMotionQueue.hasLanded(mc.player.isOnGround(), vy)) {
            setEnabled(false);
            return;
        }
        if (phase == Phase.FLYING) {
            Vec3d next = active.motions.releaseAtApex(mc.player.isOnGround(), vy, mc.player.age);
            if (next != null) mc.player.setVelocityClient(next);
        }
    }

    @EventTarget
    public void onRotationRequest(RotationRequestEvent event) {
        Session active = session;
        if (!validate(active)) return;
        // Begin aiming on takeoff, independently of the height gate for item use.
        if (phase == Phase.WAITING && !mc.player.isOnGround() && mc.player.getVelocity().y > 0) {
            launchYaw = mc.player.getYaw();
            phase = Phase.AIMING;
        }
        if (phase == Phase.AIMING || phase == Phase.CHARGING) {
            event.submit(RotationRequest.builder(getName(), new float[]{launchYaw, active.pitch}, 1200)
                    .speed(active.rotationSpeed).silent(true).build());
        }
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        Session active = session;
        if (!event.isPost() || event.isCancelled() || !validate(active) || phase != Phase.AIMING || mc.player.isOnGround()
                || mc.player.getVelocity().y <= 0 || !RotationManager.isOwner(getName())) return;
        float[] rotation = RotationManager.currentRotations;
        double height = heightAboveGround();
        // Sensitivity quantization can prevent exact equality (up to ~0.614 degrees).
        if (Math.abs(rotation[1] - active.pitch) > 0.65F
                || Math.abs(MathHelper.wrapDegrees(rotation[0] - launchYaw)) > 0.65F
                || !Double.isFinite(height) || height + 1.0E-4 < active.height) return;
        appliedYaw = rotation[0];
        appliedPitch = rotation[1];
        phase = Phase.CHARGING;
        TimerSpeedUtil.setTemporaryOverride(active, 0.02F);
        if (active.scaffold) {
            touchedScaffold = true;
            getModule(Scaffold.class).setEnabled(true);
        }
        useItem(active);
    }

    @EventTarget
    @EventPriority(-100)
    public void onPacket(PacketEvent event) {
        Session active = session;
        if (active == null || event.isCancelled() || event.getType() != PacketEvent.Type.Received
                || !sameContext(active)) return;
        if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket packet
                && packet.getEntityId() == active.player.getId() && active.motions.capture(packet.getVelocity())) {
            event.setCancelled(true);
            // Received events run off-thread. execute is drained every frame, even at 0.02x.
            mc.execute(() -> onVelocity(active));
        } else if (event.getPacket() instanceof ExplosionS2CPacket explosion && explosion.playerKnockback().isPresent()) {
            // Modern wind charges can deliver their impulse in EXPLODE rather than ENTITY_VELOCITY.
            // Normalize its additive impulse to the same absolute motion representation.
            Vec3d motion = active.player.getVelocity().add(explosion.playerKnockback().get());
            if (!active.motions.capture(motion)) return;
            event.setCancelled(true);
            ExplosionS2CPacket effects = new ExplosionS2CPacket(explosion.center(), explosion.radius(),
                    explosion.blockCount(), Optional.empty(), explosion.explosionParticle(),
                    explosion.explosionSound(), explosion.blockParticles());
            mc.execute(() -> {
                if (sameContext(active)) effects.apply(active.network);
                onVelocity(active);
            });
        }
    }

    private void onVelocity(Session active) {
        if (session != active || !validate(active)) return;
        if (active.motions.isComplete()) {
            TimerSpeedUtil.clearTemporaryOverride(active);
            phase = Phase.FLYING;
            Vec3d first = active.motions.startFlight(mc.player.age);
            if (first != null) mc.player.setVelocityClient(first);
        } else {
            useItem(active);
        }
    }

    private void useItem(Session active) {
        if (!validate(active)) return;
        int slot = findSlot(active);
        if (slot < 0) {
            abort("对应物品已用完");
            return;
        }
        if (!active.motions.awaitMotion()) return;
        active.remaining[slot]--;
        lastUseNanos = System.nanoTime();
        mc.player.getInventory().setSelectedSlot(slot);
        // Explicitly sync each use: Scaffold may have selected a block since the last one.
        PacketUtil.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        PacketUtil.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(appliedYaw, appliedPitch,
                mc.player.isOnGround(), mc.player.horizontalCollision));
        // Use a fresh sequence and explicit silent angles; local cooldowns are tick-based.
        PacketUtil.sendSequencedPacket(sequence -> new PlayerInteractItemC2SPacket(
                Hand.MAIN_HAND, sequence, appliedYaw, appliedPitch));
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    @EventTarget
    public void onRenderFrame(RenderFrameEvent event) {
        if (!validate(session)) return;
        if (phase == Phase.CHARGING && System.nanoTime() - lastUseNanos > 10_000_000_000L) {
            abort("等待 Velocity 超时");
        }
    }

    private boolean validate(Session active) {
        if (active == null || active != session) return false;
        if (!sameContext(active) || !active.player.isAlive()) {
            setEnabled(false);
            return false;
        }
        return true;
    }

    private boolean sameContext(Session active) {
        return mc.player == active.player && mc.world == active.world && mc.getNetworkHandler() == active.network;
    }

    private int findSlot(Session active) {
        for (int slot = 0; slot < 9; slot++) {
            if (active.remaining[slot] > 0 && mc.player.getInventory().getStack(slot).isOf(active.item)) return slot;
        }
        return -1;
    }

    private double heightAboveGround() {
        Box feet = mc.player.getBoundingBox();
        double closest = Double.POSITIVE_INFINITY;
        for (int y = MathHelper.floor(feet.minY); y >= mc.world.getBottomY(); y--) {
            for (int x = MathHelper.floor(feet.minX); x <= MathHelper.floor(feet.maxX); x++) {
                for (int z = MathHelper.floor(feet.minZ); z <= MathHelper.floor(feet.maxZ); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    for (Box shape : mc.world.getBlockState(pos).getCollisionShape(mc.world, pos).getBoundingBoxes()) {
                        Box box = shape.offset(pos);
                        if (box.maxY <= feet.minY + 1.0E-4 && box.maxX > feet.minX && box.minX < feet.maxX
                                && box.maxZ > feet.minZ && box.minZ < feet.maxZ) {
                            closest = Math.min(closest, Math.max(0, feet.minY - box.maxY));
                        }
                    }
                }
            }
            // Include the next layer too: fences/walls can extend above their block.
            if (Double.isFinite(closest) && feet.minY - (y + 1.5) > closest) break;
        }
        return closest;
    }

    private void abort(String reason) {
        Util.log("LongJump: " + reason);
        setEnabled(false);
    }

    @Override
    public String getSuffix() {
        return mode.getValue();
    }

    /** Snapshot configuration/context so GUI edits and old queued packets cannot mix runs. */
    private static final class Session {
        final ClientPlayerEntity player;
        final ClientWorld world;
        final ClientPlayNetworkHandler network;
        final Item item;
        final float pitch;
        final float height;
        final float rotationSpeed;
        final boolean scaffold;
        final LongJumpMotionQueue<Vec3d> motions;
        // Raw sequenced uses do not predict consumption locally. Reserve each shot so a
        // late inventory sync cannot make Multi repeatedly select an exhausted stack.
        final int[] remaining = new int[9];

        Session(ClientPlayerEntity player, ClientWorld world, ClientPlayNetworkHandler network,
                Item item, float pitch, float height, float rotationSpeed, int times, boolean scaffold) {
            this.player = player;
            this.world = world;
            this.network = network;
            this.item = item;
            this.pitch = pitch;
            this.height = height;
            this.rotationSpeed = rotationSpeed;
            this.scaffold = scaffold;
            this.motions = new LongJumpMotionQueue<>(times);
            for (int slot = 0; slot < remaining.length; slot++) {
                var stack = player.getInventory().getStack(slot);
                if (stack.isOf(item)) remaining[slot] = stack.getCount();
            }
        }
    }
}
