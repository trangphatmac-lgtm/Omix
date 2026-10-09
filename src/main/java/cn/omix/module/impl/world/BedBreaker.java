package cn.omix.module.impl.world;

import cn.omix.Client;
import cn.omix.event.base.annotation.EventPriority;
import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.*;
import cn.omix.management.RotationManager;
import cn.omix.management.movement.MovementCorrection;
import cn.omix.management.rotation.RotationRequest;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.impl.combat.Aura;
import cn.omix.module.impl.exploits.Blink;
import cn.omix.module.impl.move.LongJump;
import cn.omix.module.impl.move.NoSlowDown;
import cn.omix.module.impl.player.AutoBlockIn;
import cn.omix.module.impl.player.AutoTool;
import cn.omix.module.impl.player.Freecam;
import cn.omix.module.impl.render.HUD;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.module.value.impl.ModeValue;
import cn.omix.module.value.impl.MultiBoolValue;
import cn.omix.module.value.impl.NumberValue;
import cn.omix.util.network.PacketUtil;
import cn.omix.util.player.AutoToolMining;
import cn.omix.util.render.Render3D;
import cn.omix.util.world.bed.BedBreakerAim;
import cn.omix.util.world.bed.BedBreakerDigging;
import cn.omix.util.world.bed.BedBreakerProgress;
import cn.omix.util.world.bed.BedBreakerTargeting;
import cn.omix.util.world.bed.BedBreakerTeams;
import injection.accessor.ClientPlayerInteractionManagerAccessor;
import injection.accessor.ClientPlayerEntityAccessor;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.BedPart;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.RaycastContext;

import java.awt.Color;
import java.util.LinkedHashSet;
import java.util.Set;

/** Amunix BedBreaker port, using Omix's rotation arbitration and sequenced mining lifecycle. */
public final class BedBreaker extends Module {
    private final ModeValue mode = new ModeValue("BreakMode", "Hypixel", "Instant", "Hypixel", "Legit");
    private final ModeValue rotationMode = new ModeValue("RotationMode", "Normal", "Normal", "Snap");
    private final ModeValue swingMode = new ModeValue("SwingMode", "Client", "Client", "Server");
    private final ModeValue teams = new ModeValue("Teams", "Hypixel", "None", "Hypixel");
    private final MultiBoolValue box = new MultiBoolValue("Box", new BoolValue("Fill", true), new BoolValue("Outline", true));
    private final NumberValue range = new NumberValue("Range", 4.5, 0, 7, .01);
    private final NumberValue speed = new NumberValue("Speed", 0, 0, 100, 1);
    private final BoolValue noDelay = new BoolValue("Ignore Break Delay", true);
    private final ModeValue priority = new ModeValue("Priority", "KillAura", "BedBreaker", "KillAura");
    private final BoolValue useAutoTool = new BoolValue("Auto Tool", true);

    private final BedBreakerProgress progress = new BedBreakerProgress();
    private final BedBreakerDigging digging = new BedBreakerDigging(progress);
    private final BedBreakerTargeting.RecentBeds recentlyBroken = new BedBreakerTargeting.RecentBeds();
    private final Set<BlockPos> breakPath = new LinkedHashSet<>();
    private BlockPos bedPos;
    private BlockPos pos;
    private BlockState targetState;
    private ClientPlayerEntity targetPlayer;
    private ClientWorld targetWorld;
    private Direction face;
    private RotationRequest rotation;
    private boolean startedAutoTool;
    private boolean waiting;
    private String activeMode;

    public record DiggingTarget(BlockPos position, BlockState state, float progress) { }

    public BedBreaker() { super("BedBreaker", Category.World); }

    private final BedBreakerTargeting.World targeting = new BedBreakerTargeting.World() {
        public Direction partner(BlockPos bed) { return otherPart(mc.world.getBlockState(bed)); }
        public int bedColor(BlockPos bed) {
            return mc.world.getBlockState(bed).getBlock() instanceof BedBlock block ? block.getColor().getIndex() : -1;
        }
        public boolean air(BlockPos block) { return mc.world.getBlockState(block).isAir(); }
        public boolean fullCube(BlockPos block) { return mc.world.getBlockState(block).isFullCube(mc.world, block); }
        public float breakingDelta(BlockPos block) {
            if (mc.world.isOutOfHeightLimit(block) || !mc.world.getWorldBorder().contains(block)) return 0;
            return AutoToolMining.breakingDelta(mc.player, mc.world, block, autoToolActive());
        }
        public BlockPos raycast(BlockPos bed) {
            var hit = mc.world.raycast(new RaycastContext(mc.player.getEyePos(), bed.toCenterPos(),
                    RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
            return hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : null;
        }
    };

    @Override
    public void onEnable() {
        reset(false, true);
        progress.reset();
        recentlyBroken.clear();
        activeMode = mode.getValue();
        syncAutoTool();
    }

    @Override
    public void onDisable() {
        reset(true, true);
        progress.reset();
        recentlyBroken.clear();
        stopManagedAutoTool();
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        reset(false, true);
        progress.reset();
        recentlyBroken.clear();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!canRun() || targetWorld != null && (targetWorld != mc.world || targetPlayer != mc.player)) {
            reset(true, true);
            return;
        }
        // Input now runs before LivingUpdate. Consume cooldown once, before either phase.
        waiting = progress.waiting(noDelay.getValue());
    }

    @EventTarget
    @EventPriority(100)
    public void onLivingUpdate(LivingUpdateEvent event) {
        setSuffix(mode.getValue());
        if (!canRun()) { reset(true, true); return; }
        syncAutoTool();
        if (!mode.getValue().equals(activeMode)) {
            reset(true, true);
            progress.reset();
            activeMode = mode.getValue();
        }
        if (waiting) return;

        long now = System.currentTimeMillis();
        recentlyBroken.prune(now);
        BlockPos nextBed = validBed(bedPos) ? bedPos : findBed();
        if (nextBed == null) { reset(true, true); return; }
        boolean sameBed = nextBed.equals(bedPos);
        // Hypixel locks the selected defense until it is broken, as in the reference.
        BlockPos nextPos = mode.is("Hypixel") && sameBed && validTarget() ? pos
                : BedBreakerTargeting.selectBlock(targeting, mc.player.getEntityPos(), range.getValue(), breakMode(), nextBed);
        if (nextPos == null) { reset(true, true); return; }
        BlockState state = mc.world.getBlockState(nextPos);
        if (!sameBed || !nextPos.equals(pos) || !state.equals(targetState)
                || targetWorld != mc.world || targetPlayer != mc.player) {
            reset(true, !sameBed);
            bedPos = nextBed;
            pos = nextPos;
            targetState = state;
            targetWorld = mc.world;
            targetPlayer = mc.player;
            progress.prepare();
        }

        float delta = targeting.breakingDelta(pos);
        boolean needsRotation = !digging.started() || rotationMode.is("Normal") || progress.willFinish(delta, speed.getValue());
        RotationManager.release(rotation);
        rotation = needsRotation ? RotationRequest.builder(getName(),
                BedBreakerAim.find(mc.world, mc.player, pos, range.getValue(), mode.is("Legit")), 450)
                .speed(0).movementCorrection(MovementCorrection.Silent).build() : null;
    }

    @EventTarget
    public void onRotationRequest(RotationRequestEvent event) {
        if (!waiting && canRun() && validTarget() && rotation != null) event.submit(rotation);
    }

    @EventTarget
    public void onRotationApplied(RotationAppliedEvent event) {
        if (waiting) return;
        if (!canRun() || !validTarget()) { reset(true, true); return; }
        // Normal interaction phase: after the preceding tick ended, before this tick's movement.
        // A queued rotation is not evidence that a cancelled/overridden movement sent it.
        ClientPlayerEntityAccessor sent = (ClientPlayerEntityAccessor) mc.player;
        if (rotation != null && (!RotationManager.isOwner(getName())
                || Math.abs(MathHelper.wrapDegrees(sent.getLastYaw() - rotation.yaw())) > .001F
                || Math.abs(sent.getLastPitch() - rotation.pitch()) > .001F)) {
            reset(true, true);
            return;
        }
        var hit = BedBreakerAim.raycast(mc.world, mc.player, pos, sent.getLastYaw(), sent.getLastPitch(), range.getValue(), mode.is("Legit"));
        if (hit == null && (!digging.started() || rotationMode.is("Normal"))) { reset(true, true); return; }
        if (hit != null) face = hit.getSide();
        AutoTool autoTool = getModule(AutoTool.class);
        if (autoToolActive() && !autoTool.switchSlot(this, pos)) { reset(true, true); return; }
        float delta = targetState.calcBlockBreakingDelta(mc.player, mc.world, pos);
        boolean immediate = mc.player.isCreative() || targetState.getHardness(mc.world, pos) == 0;
        if (!immediate && (delta <= 0 || !Float.isFinite(delta))) { reset(true, true); return; }
        digging.interact(delta, immediate, rotation != null && hit != null, speed.getValue(), diggingEffects);
    }

    private final BedBreakerDigging.Effects diggingEffects = new BedBreakerDigging.Effects() {
        public void start(boolean instant) {
            mc.interactionManager.cancelBlockBreaking();
            ((ClientPlayerInteractionManagerAccessor) mc.interactionManager).omix$syncSelectedSlot();
            send(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, instant);
        }
        public void stop() { send(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, true); }
        public void swing() { BedBreaker.this.swing(); }
        public void progress(float amount) {
            mc.world.spawnBlockBreakingParticle(pos, face);
            mc.world.setBlockBreakingInfo(mc.player.getId(), pos, Math.min(9, (int) (amount * 10)));
        }
        public void complete() { finish(); }
    };

    private void finish() {
        if (targetState.getBlock() instanceof BedBlock) {
            Direction partner = otherPart(targetState);
            recentlyBroken.mark(pos, partner, System.currentTimeMillis());
            if (partner != null) mc.world.setBlockBreakingInfo(mc.player.getId(), pos.offset(partner), -1);
            breakPath.clear();
        } else {
            breakPath.add(pos);
        }
        BlockPos anchor = bedPos;
        boolean bedBroken = targetState.getBlock() instanceof BedBlock;
        reset(false, false);
        if (!bedBroken) bedPos = anchor;
        progress.finish();
        waiting = !noDelay.getValue();
    }

    public DiggingTarget diggingTarget(float tickDelta) {
        return digging.started() && canRun() && validTarget()
                ? new DiggingTarget(pos, targetState, progress.progress(speed.getValue(), tickDelta)) : null;
    }

    public BedBreakerProgress.State getBreakState() { return progress.state(); }

    public BlockPos findBed() {
        if (mc.player == null || mc.world == null) return null;
        long now = System.currentTimeMillis();
        return BedBreakerTargeting.findBed(targeting, mc.player.getEntityPos(), range.getValue(), breakMode(),
                helmetTeam(), block -> recentlyBroken.contains(block, now));
    }

    private BedBreakerTargeting.Mode breakMode() { return BedBreakerTargeting.Mode.valueOf(mode.getValue()); }

    private int helmetTeam() {
        if (!teams.is("Hypixel") || mc.player == null) return -1;
        var helmet = mc.player.getEquippedStack(EquipmentSlot.HEAD);
        if (!helmet.isOf(Items.LEATHER_HELMET)) return -1;
        var color = helmet.get(DataComponentTypes.DYED_COLOR);
        return BedBreakerTeams.nearestColor(color == null ? 0xA06540 : color.rgb());
    }

    private boolean validBed(BlockPos bed) {
        return bed != null && mc.world != null && mc.player != null && targeting.partner(bed) != null
                && !BedBreakerTeams.sameTeam(targeting.bedColor(bed), helmetTeam())
                && !recentlyBroken.contains(bed, System.currentTimeMillis())
                && BedBreakerTargeting.inRange(mc.player.getEntityPos(), bed, range.getValue());
    }

    private boolean validTarget() {
        return pos != null && targetWorld == mc.world && targetPlayer == mc.player && validBed(bedPos)
                && targetState.equals(mc.world.getBlockState(pos)) && !targetState.isAir()
                && BedBreakerTargeting.inRange(mc.player.getEntityPos(), pos, range.getValue());
    }

    private boolean canOperate() {
        if (!isNativeBehaviorActive() || mc.player == null || mc.world == null || mc.interactionManager == null
                || mc.getNetworkHandler() == null || mc.currentScreen != null || !mc.player.isAlive()
                || mc.player.isSpectator() || Freecam.isActive() || active(Blink.class)
                || instance.getPacketManager().getBlink().active || instance.getPacketManager().getDelay().active) return false;
        Aura aura = getModule(Aura.class);
        if (mc.player.isUsingItem() && !(priority.is("BedBreaker") && aura != null && aura.isBlocking())) return false;
        if (active(Scaffold.class) || active(ScaffoldX.class)) return false;
        AutoBlockIn blockIn = getModule(AutoBlockIn.class);
        if (blockIn != null && blockIn.isPlacing()) return false;
        LongJump longJump = getModule(LongJump.class);
        if (longJump != null && longJump.isUsingItemThisTick()) return false;
        NoSlowDown grim = NoSlowDown.activeGrim();
        return grim == null || !grim.lockSlot() && !grim.isGrimActivePhase();
    }

    private boolean canRun() {
        if (!canOperate()) return false;
        Aura aura = getModule(Aura.class);
        return !priority.is("KillAura") || aura == null || !aura.isNativeBehaviorActive()
                || aura.getTarget() == null && aura.getTargets().isEmpty() && !aura.isBlocking();
    }

    private boolean active(Class<? extends Module> type) {
        Module module = getModule(type);
        return module != null && module.isNativeBehaviorActive();
    }

    /** Aura asks before acquiring/attacking so module listener order cannot bypass the priority. */
    public static boolean pausesAura() {
        BedBreaker module = current();
        return module != null && module.priority.is("BedBreaker") && module.canOperate()
                && (module.validTarget() || module.findBed() != null);
    }

    public static boolean blocksBreakingInput() {
        BedBreaker module = current();
        return module != null && module.canRun() && module.validTarget();
    }

    private static BedBreaker current() {
        return Client.instance == null || Client.instance.getModuleManager() == null ? null
                : Client.instance.getModuleManager().getModule(BedBreaker.class);
    }

    private boolean autoToolActive() { return active(AutoTool.class); }

    private void syncAutoTool() {
        AutoTool autoTool = getModule(AutoTool.class);
        if (autoTool == null) return;
        if (useAutoTool.getValue()) {
            if (!autoTool.isEnabled()) { autoTool.setEnabled(true); startedAutoTool = true; }
        } else stopManagedAutoTool();
    }

    private void stopManagedAutoTool() {
        AutoTool autoTool = getModule(AutoTool.class);
        if (autoTool != null && startedAutoTool) autoTool.setEnabled(false);
        startedAutoTool = false;
    }

    private void swing() {
        if (swingMode.is("Client")) mc.player.swingHand(Hand.MAIN_HAND);
        else PacketUtil.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
    }

    private void send(PlayerActionC2SPacket.Action action, boolean predict) {
        BlockPos block = pos;
        Direction side = face;
        PacketUtil.sendSequencedPacket(sequence -> {
            if (predict) mc.interactionManager.breakBlock(block);
            return new PlayerActionC2SPacket(action, block, side, sequence);
        });
    }

    private void reset(boolean abort, boolean clearPath) {
        if (targetWorld != null && targetWorld == mc.world && targetPlayer == mc.player && mc.player != null) {
            if (abort && digging.started() && mc.interactionManager != null && mc.getNetworkHandler() != null)
                send(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, false);
            if (pos != null) mc.world.setBlockBreakingInfo(mc.player.getId(), pos, -1);
        }
        AutoTool autoTool = getModule(AutoTool.class);
        if (autoTool != null) autoTool.releaseTool(this);
        RotationManager.release(rotation);
        rotation = null;
        pos = bedPos = null;
        targetWorld = null;
        targetPlayer = null;
        targetState = null;
        face = null;
        waiting = false;
        digging.reset();
        if (clearPath) breakPath.clear();
    }

    private static Direction otherPart(BlockState state) {
        if (!(state.getBlock() instanceof BedBlock)) return null;
        Direction facing = state.get(BedBlock.FACING);
        return state.get(BedBlock.PART) == BedPart.HEAD ? facing.getOpposite() : facing;
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!canRun() || !box.isEnabled("Fill") && !box.isEnabled("Outline")) return;
        for (BlockPos path : breakPath) if (!path.equals(pos)) drawBox(event, path, Color.WHITE);
        if (validTarget()) drawBox(event, pos, new Color(getModule(HUD.class).getColor()));
    }

    private void drawBox(Render3DEvent event, BlockPos block, Color color) {
        var shape = mc.world.getBlockState(block).getOutlineShape(mc.world, block);
        Box bounds = shape.isEmpty() ? new Box(block) : shape.getBoundingBox().offset(block);
        Render3D.drawBox(event, bounds, new Color(color.getRed(), color.getGreen(), color.getBlue(), 80),
                new Color(color.getRed(), color.getGreen(), color.getBlue(), 160),
                box.isEnabled("Fill"), box.isEnabled("Outline"), 2F);
    }
}
