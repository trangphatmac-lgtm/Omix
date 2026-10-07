package cn.omix.module.impl.player;

import cn.omix.Client;
import cn.omix.event.base.annotation.EventPriority;
import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.*;
import cn.omix.management.RotationManager;
import cn.omix.management.rotation.RotationRequest;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.impl.combat.Aura;
import cn.omix.module.impl.move.LongJump;
import cn.omix.module.impl.move.NoSlowDown;
import cn.omix.module.impl.world.Scaffold;
import cn.omix.module.impl.world.ScaffoldX;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.module.value.impl.NumberValue;
import cn.omix.util.network.PacketUtil;
import cn.omix.util.player.bed.BedAuraProgress;
import cn.omix.util.player.bed.BedAuraTargeting;
import cn.omix.util.player.bed.BedAuraWhitelist;
import cn.omix.util.render.Render3D;
import injection.accessor.ClientPlayerInteractionManagerAccessor;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.BedPart;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;

/** Automatic bed breaking adapted from Samsara to Omix's rotation and packet lifecycle. */
public final class BedAura extends Module {
    private final NumberValue range = new NumberValue("Range", 5, 1, 8, 0.5);
    private final NumberValue speed = new NumberValue("Speed", 1, 1, 2, 0.05);
    private final NumberValue breakDelay = new NumberValue("Break Delay", 2, 1, 4, 1);
    private final BoolValue surrounding = new BoolValue("Surrounding", false);
    private final BoolValue whitelist = new BoolValue("Whitelist", true);
    private final BoolValue allowKillAura = new BoolValue("Allow KillAura", false);
    private final BoolValue onlyStartStopRotate = new BoolValue("Only S/S Rotate", false);
    private final BoolValue watchdog = new BoolValue("Watchdog Mode", false);

    private final BedAuraProgress progress = new BedAuraProgress();
    private BedAuraTargeting.Target target;
    private ClientWorld targetWorld;
    private ClientPlayerEntity targetPlayer;
    private BlockState targetState;
    private Direction face;
    private RotationRequest rotation;
    private boolean started;
    private int originalSlot = -1;
    private int toolSlot = -1;

    public record DiggingTarget(BlockPos position, BlockState state, float progress) {}

    public BedAura() {
        super("BedAura", Category.Player);
    }

    @Override
    public void onEnable() {
        reset(false);
        progress.reset();
    }

    @Override
    public void onDisable() {
        reset(true);
        progress.reset();
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        reset(false);
        progress.reset();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        // Tick runs even while a GUI prevents the player's movement update.
        if (!canRun() || targetWorld != null && (targetWorld != mc.world || targetPlayer != mc.player)) reset(true);
    }

    @EventTarget
    @EventPriority(100)
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (!canRun()) {
            reset(true);
            return;
        }
        if (targetWorld != null && (targetWorld != mc.world || targetPlayer != mc.player)) {
            reset(false);
            progress.reset();
        }
        if (toolSlot != -1 && mc.player.getInventory().getSelectedSlot() != toolSlot) {
            // A user/other module changed slots; do not overwrite that selection on cleanup.
            reset(true);
            return;
        }
        if (progress.waiting()) return;
        var next = BedAuraTargeting.find(new BedAuraTargeting.World() {
            public Direction partner(BlockPos pos) {
                BlockState state = mc.world.getBlockState(pos);
                if (!(state.getBlock() instanceof BedBlock)) return null;
                Direction facing = state.get(BedBlock.FACING);
                return state.get(BedBlock.PART) == BedPart.HEAD ? facing.getOpposite() : facing;
            }
            public boolean air(BlockPos pos) { return mc.world.getBlockState(pos).isAir(); }
            public boolean breakable(BlockPos pos) {
                return !mc.world.isOutOfHeightLimit(pos) && mc.world.getWorldBorder().contains(pos)
                        && mc.world.getBlockState(pos).getHardness(mc.world, pos) >= 0;
            }
        }, mc.player.getEntityPos(), range.getValue(), surrounding.getValue(), target == null ? null : target.bed());
        if (next == null) {
            reset(true);
            return;
        }
        BlockState state = mc.world.getBlockState(next.block());
        if (!next.equals(target) || !state.equals(targetState)) {
            reset(true);
            target = next;
            targetState = state;
            targetWorld = mc.world;
            targetPlayer = mc.player;
            if (!next.block().equals(next.bed())) selectDefenseTool();
        }
        Vec3d aim = target.block().toCenterPos().subtract(mc.player.getEyePos());
        float yaw = (float) Math.toDegrees(Math.atan2(aim.z, aim.x)) - 90;
        float pitch = (float) -Math.toDegrees(Math.atan2(aim.y, Math.sqrt(aim.x * aim.x + aim.z * aim.z)));
        face = Direction.getFacing(aim.x, aim.y, aim.z).getOpposite();
        rotation = !onlyStartStopRotate.getValue() || !started || progress.readyToFinish(rate())
                ? RotationRequest.builder(getName(), new float[]{yaw, pitch}, 450).speed(0).build() : null;
    }

    @EventTarget
    public void onRotationRequest(RotationRequestEvent event) {
        if (canRun() && rotation != null) event.submit(rotation);
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (event.isPre()) return;
        if (!canRun() || event.isCancelled() || !validTarget()) {
            reset(true);
            return;
        }
        if (rotation != null && (!RotationManager.isOwner(getName())
                || Math.abs(MathHelper.wrapDegrees(event.getYaw() - rotation.yaw())) > .001f
                || Math.abs(event.getPitch() - rotation.pitch()) > .001f)) {
            reset(true);
            return;
        }
        BedAuraProgress.Rate rate = rate();
        if (rate.delta() <= 0) {
            reset(true);
            return;
        }
        if (!started) {
            mc.interactionManager.cancelBlockBreaking();
            syncSlot();
            send(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, false);
            started = true;
        }
        if (progress.readyToFinish(rate)) {
            // Landing or changing tools can alter the threshold after living-update planning.
            // Wait for the next collection cycle if this STOP has not been aimed yet.
            if (rotation == null) return;
            send(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, true);
            mc.player.swingHand(Hand.MAIN_HAND);
            started = false; // A completed STOP must never be followed by ABORT.
            reset(false);
            progress.finish(breakDelay.getValue().intValue());
            return;
        }
        if (mc.player.age % 4 == 0) {
            var sound = targetState.getSoundGroup();
            mc.getSoundManager().play(new PositionedSoundInstance(sound.getHitSound(), SoundCategory.BLOCKS,
                    (sound.getVolume() + 1) * 0.125f, sound.getPitch() * 0.5f,
                    SoundInstance.createRandom(), target.block()));
        }
        progress.advance(rate);
        mc.world.setBlockBreakingInfo(mc.player.getId(), target.block(), Math.min(9, (int) (progress.interpolated(1) * 10)));
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    public DiggingTarget diggingTarget(float tickDelta) {
        if (!started || !canRun() || !validTarget()) return null;
        return new DiggingTarget(target.block(), targetState, progress.interpolated(tickDelta));
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        DiggingTarget digging = diggingTarget(event.getTickDelta());
        if (digging == null) return;
        var shape = digging.state().getOutlineShape(mc.world, digging.position());
        if (!shape.isEmpty()) Render3D.drawBox(event, shape.getBoundingBox().offset(digging.position()).expand(0.002),
                new Color(255, 0, 0, 90), true, true, 1.5f);
    }

    /** Suppress vanilla left-click mining without blocking Aura's direct entity attacks. */
    public static boolean blocksBreakingInput() {
        if (Client.instance == null || Client.instance.getModuleManager() == null) return false;
        BedAura module = Client.instance.getModuleManager().getModule(BedAura.class);
        return module != null && module.canRun() && module.validTarget();
    }

    private boolean canRun() {
        if (!isNativeBehaviorActive() || mc.player == null || mc.world == null || mc.interactionManager == null
                || mc.getNetworkHandler() == null || mc.currentScreen != null || !mc.player.isAlive()
                || mc.player.isSpectator() || mc.player.isUsingItem() || Freecam.isActive()) return false;
        if (whitelist.getValue() && BedAuraWhitelist.INSTANCE.isProtected(mc.player.getEntityPos())) return false;
        if (active(Scaffold.class) || active(ScaffoldX.class)) return false;
        AutoBlockIn blockIn = getModule(AutoBlockIn.class);
        if (blockIn != null && blockIn.isPlacing()) return false;
        LongJump longJump = getModule(LongJump.class);
        if (longJump != null && longJump.isUsingItemThisTick()) return false;
        NoSlowDown grim = NoSlowDown.activeGrim();
        if (grim != null && (grim.lockSlot() || grim.isGrimActivePhase())) return false;
        Aura aura = getModule(Aura.class);
        return allowKillAura.getValue() || aura == null || !aura.isNativeBehaviorActive()
                || aura.getTarget() == null && !aura.isBlocking();
    }

    private boolean active(Class<? extends Module> type) {
        Module module = getModule(type);
        return module != null && module.isNativeBehaviorActive();
    }

    private boolean validTarget() {
        return target != null && targetWorld == mc.world && targetPlayer == mc.player && mc.world != null && mc.player != null
                && mc.world.getBlockState(target.bed()).getBlock() instanceof BedBlock
                && targetState.equals(mc.world.getBlockState(target.block()))
                && (toolSlot == -1 || mc.player.getInventory().getSelectedSlot() == toolSlot)
                && BedAuraTargeting.inRange(mc.player.getEntityPos(), target.bed(), range.getValue())
                && BedAuraTargeting.inRange(mc.player.getEntityPos(), target.block(), range.getValue());
    }

    private BedAuraProgress.Rate rate() {
        return BedAuraProgress.rate(targetState.calcBlockBreakingDelta(mc.player, mc.world, target.block()),
                speed.getValue(), watchdog.getValue(), mc.player.isSubmergedIn(FluidTags.WATER), mc.player.isOnGround());
    }

    private void selectDefenseTool() {
        int selected = mc.player.getInventory().getSelectedSlot();
        int best = selected;
        float bestSpeed = Math.max(1, mc.player.getInventory().getStack(selected).getMiningSpeedMultiplier(targetState));
        for (int slot = 0; slot < 9; slot++) {
            float miningSpeed = mc.player.getInventory().getStack(slot).getMiningSpeedMultiplier(targetState);
            if (miningSpeed > bestSpeed) {
                best = slot;
                bestSpeed = miningSpeed;
            }
        }
        if (best != selected) {
            originalSlot = selected;
            toolSlot = best;
            mc.player.getInventory().setSelectedSlot(best);
            syncSlot();
        }
    }

    private void syncSlot() {
        ((ClientPlayerInteractionManagerAccessor) mc.interactionManager).omix$syncSelectedSlot();
    }

    private void send(PlayerActionC2SPacket.Action action, boolean predictBreak) {
        BlockPos position = target.block();
        Direction direction = face;
        PacketUtil.sendSequencedPacket(sequence -> {
            if (predictBreak) mc.interactionManager.breakBlock(position);
            return new PlayerActionC2SPacket(action, position, direction, sequence);
        });
    }

    private void reset(boolean abort) {
        boolean sameWorld = targetWorld != null && targetWorld == mc.world && targetPlayer == mc.player && mc.player != null;
        if (sameWorld) {
            if (abort && started && mc.interactionManager != null) send(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, false);
            if (target != null) mc.world.setBlockBreakingInfo(mc.player.getId(), target.block(), -1);
            if (originalSlot != -1 && mc.player.getInventory().getSelectedSlot() == toolSlot) {
                mc.player.getInventory().setSelectedSlot(originalSlot);
                if (mc.interactionManager != null) syncSlot();
            }
        }
        RotationManager.release(rotation);
        rotation = null;
        target = null;
        targetState = null;
        targetWorld = null;
        targetPlayer = null;
        face = null;
        started = false;
        originalSlot = toolSlot = -1;
        progress.resetProgress();
    }
}
