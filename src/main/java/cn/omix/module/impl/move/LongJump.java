package cn.omix.module.impl.move;

import cn.omix.event.base.annotation.EventPriority;
import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.*;
import cn.omix.management.RotationManager;
import cn.omix.management.movement.MovementCorrection;
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
import cn.omix.util.LongJumpAim;
import cn.omix.util.LongJumpUseSchedule;
import cn.omix.util.Util;
import cn.omix.util.misc.TimerSpeedUtil;
import cn.omix.util.player.RayCastUtil;
import injection.accessor.ClientPlayerEntityAccessor;
import injection.accessor.PlayerMoveC2SPacketAccessor;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;

public class LongJump extends Module {
    private final ModeValue mode = new ModeValue("Mode", "Fireball", "Fireball", "Windcharge");
    private final NumberValue targetPitch = new NumberValue("Target Pitch", 80, -90, 90, 1);
    private final NumberValue targetHeight = new NumberValue("Target Height", 0.5, 0, 2, 0.01);
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
        old.uses.close();
        TimerSpeedUtil.clearTemporaryOverride(old);
        getModule(Velocity.class).setEnabled(wasVelocityEnabled);
        if (sameContext(old)) {
            mc.player.getInventory().setSelectedSlot(originalSlot);
        }
        phase = Phase.WAITING;
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        setEnabled(false);
    }

    @EventTarget
    @EventPriority(0)
    public void onTick(TickEvent event) {
        Session active = session;
        if (!validate(active)) return;
        active.uses.beginTick();
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
            launchYaw = LongJumpAim.behind(mc.player.getYaw(), active.pitch).yaw();
            phase = Phase.AIMING;
        }
        LongJumpAim usedAim = active.uses.getAim();
        if (usedAim != null || phase == Phase.AIMING || phase == Phase.CHARGING) {
            float[] angles = usedAim == null ? new float[]{launchYaw, active.pitch}
                    : new float[]{usedAim.yaw(), usedAim.pitch()};
            event.submit(RotationRequest.builder(getName(), angles, 1200)
                    .speed(usedAim == null ? active.rotationSpeed : 0).silent(true)
                    .movementCorrection(MovementCorrection.Silent).build());
        }
    }

    @EventTarget
    @EventPriority(Integer.MAX_VALUE)
    public void onMotion(MotionEvent event) {
        Session active = session;
        if (!event.isPre() || !validate(active)) return;
        LongJumpAim aim = active.uses.getAim();
        if (aim == null) return;
        // BadPacketsJ compares exact floats, not an angular tolerance. Also keep
        // vanilla's lastYaw/lastPitch cache aligned with the final movement angles.
        event.setYaw(aim.yaw());
        event.setPitch(aim.pitch());
    }

    @EventTarget
    @EventPriority(Integer.MAX_VALUE)
    public void onSendMovement(PacketEvent event) {
        Session active = session;
        if (active == null || event.isCancelled() || event.getType() != PacketEvent.Type.Send
                || !sameContext(active) || !(event.getPacket() instanceof PlayerMoveC2SPacket packet)
                || !packet.changesLook()) return;
        LongJumpAim aim = active.uses.getAim();
        if (aim == null) return;
        // A packet listener (e.g. NoFall) may rewrite rotation after MotionEvent.
        PlayerMoveC2SPacketAccessor accessor = (PlayerMoveC2SPacketAccessor) packet;
        accessor.setYaw(aim.yaw());
        accessor.setPitch(aim.pitch());
    }

    @EventTarget
    @EventPriority(1)
    public void onRotationApplied(RotationAppliedEvent event) {
        Session active = session;
        if (!validate(active) || !active.uses.isPending()
                || (phase != Phase.AIMING && phase != Phase.CHARGING)
                || !RotationManager.isOwner(getName())) return;
        // This is before the current tick's movement. Use the aim already sent by the
        // preceding tick, as ChestArua does, rather than adding a synthetic movement packet.
        ClientPlayerEntityAccessor sent = (ClientPlayerEntityAccessor) mc.player;
        // Sensitivity quantization can prevent exact equality (up to ~0.614 degrees).
        if (Math.abs(sent.getLastPitch() - active.pitch) > 0.65F
                || Math.abs(MathHelper.wrapDegrees(sent.getLastYaw() - launchYaw)) > 0.65F) return;
        if (phase == Phase.AIMING) {
            double height = heightAboveGround();
            if (mc.player.isOnGround() || mc.player.getVelocity().y <= 0
                    || !Double.isFinite(height) || height + 1.0E-4 < active.height) return;
        }
        useItem(active, new LongJumpAim(sent.getLastYaw(), sent.getLastPitch()));
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
            // Restore Timer/apply motion promptly; subsequent uses wait for the interaction phase.
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
            active.uses.requestNextUse();
        }
    }

    private void useItem(Session active, LongJumpAim aim) {
        if (!validate(active) || mc.interactionManager == null) return;
        int slot = findSlot(active);
        if (slot < 0) {
            abort("对应物品已用完");
            return;
        }
        // Fire charges use the block hit by the silent aim. Resolve the real hit
        // position/face before arming Timer or waiting for a velocity response.
        boolean fireball = active.item == Items.FIRE_CHARGE;
        BlockHitResult target = fireball
                ? RayCastUtil.raycastBlock(aim.yaw(), aim.pitch(), mc.player.getBlockInteractionRange())
                : null;
        if (fireball && (target == null || target.getType() != HitResult.Type.BLOCK)) {
            abort("Fireball 目标方向在交互距离内未命中方块");
            return;
        }
        if (!active.uses.beginUse(mc.player.getItemCooldownManager()
                .isCoolingDown(mc.player.getInventory().getStack(slot)), aim)) return;
        if (!active.motions.awaitMotion()) return;
        if (phase == Phase.AIMING) {
            phase = Phase.CHARGING;
            TimerSpeedUtil.setTemporaryOverride(active, 0.02F);
            if (active.scaffold) {
                touchedScaffold = true;
                getModule(Scaffold.class).setEnabled(true);
            }
        }
        lastUseNanos = System.nanoTime();
        if (mc.player.getInventory().getSelectedSlot() != slot) {
            mc.player.getInventory().setSelectedSlot(slot);
        }
        float oldYaw = mc.player.getYaw();
        float oldPitch = mc.player.getPitch();
        try {
            // Like NoFall MLG, let vanilla syncSelectedSlot update its own cache and
            // allocate the interaction sequence. Silent angles exist only inside this call.
            mc.player.setYaw(aim.yaw());
            mc.player.setPitch(aim.pitch());
            // Keep the normal packet event chain: bypassing it leaves other modules'
            // slot trackers stale and can turn a later real switch into a duplicate.
            ActionResult result = fireball
                    ? mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, target)
                    : mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            if (result.isAccepted()) mc.player.swingHand(Hand.MAIN_HAND);
            if (result == ActionResult.FAIL) abort("物品交互失败");
        } catch (RuntimeException exception) {
            abort("物品交互失败");
            throw exception;
        } finally {
            active.player.setYaw(oldYaw);
            active.player.setPitch(oldPitch);
        }
    }

    @EventTarget
    public void onRenderFrame(RenderFrameEvent event) {
        if (!validate(session)) return;
        if (phase == Phase.CHARGING && !session.uses.isPending()
                && System.nanoTime() - lastUseNanos > 10_000_000_000L) {
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
            if (mc.player.getInventory().getStack(slot).isOf(active.item)) return slot;
        }
        return -1;
    }

    /** Prevent Scaffold from changing the held item after USE_ITEM within the same tick. */
    public boolean isUsingItemThisTick() {
        return session != null && session.uses.isUsedThisTick();
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
        final LongJumpUseSchedule uses = new LongJumpUseSchedule();

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
        }
    }
}
