package cn.omix.module.impl.world;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.KeyInputEvent;
import cn.omix.event.impl.PacketEvent;
import cn.omix.event.impl.PacketLogEvent;
import cn.omix.event.impl.Render2DEvent;
import cn.omix.event.impl.TickEvent;
import cn.omix.event.impl.WorldEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.impl.player.AntiBot;
import cn.omix.module.impl.player.Teams;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.module.value.impl.KeyValue;
import cn.omix.module.value.impl.ModeValue;
import cn.omix.module.value.impl.NumberValue;
import cn.omix.module.value.impl.MultiBoolValue;
import cn.omix.util.Util;
import cn.omix.util.world.anticheat.*;
import cn.omix.util.world.anticheat.raven.*;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.PlantBlock;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockBreakingProgressS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/** Client-side heuristics ported from OpenB4's AntiCheatModule, not server verdicts. */
public final class AntiCheat extends Module {
    private final NumberValue flagInterval = new NumberValue("Flag interval", 10, 0, 60, 1);
    private final BoolValue autoReport = new BoolValue("Auto report", false);
    private final BoolValue disableOutsideMinigames = new BoolValue("Disable outside minigames", false);
    private final BoolValue hideChatAlerts = new BoolValue("Hide chat alerts", false);
    private final BoolValue ignoreTeammates = new BoolValue("Ignore teammates", true);
    private final BoolValue sendPartyChat = new BoolValue("Send party chat", false);
    private final BoolValue shouldPing = new BoolValue("Should ping", true);
    private final BoolValue cheatUI = new BoolValue("CheatUI", true);
    private final KeyValue cheatUIKey = new KeyValue("CheatUI Key", GLFW.GLFW_KEY_N, () -> cheatUI.getValue());
    private final NumberValue cheatUIX = new NumberValue("CheatUI X", 10, 0, 1000, 1, () -> cheatUI.getValue());
    private final NumberValue cheatUIY = new NumberValue("CheatUI Y", 10, 0, 1000, 1, () -> cheatUI.getValue());
    private final BoolValue thresholdAlarms = new BoolValue("Threshold Alarms", true);
    private final BoolValue alarm50 = new BoolValue("Alarm at 50%", true, () -> thresholdAlarms.getValue());
    private final BoolValue alarm80 = new BoolValue("Alarm at 80%", true, () -> thresholdAlarms.getValue());
    private final BoolValue alarm100 = new BoolValue("Alarm at 100%", true, () -> thresholdAlarms.getValue());
    private final ModeValue alarmSound = new ModeValue("Alarm Sound", "Pling", () -> thresholdAlarms.getValue(),
            "Pling", "Bass", "Orb", "Harp", "Break");
    private final BoolValue suspicionReport = new BoolValue("Suspicion Report", false, () -> autoReport.getValue());
    private final NumberValue reportAt = new NumberValue("Report at %", 100, 1, 100, 1,
            () -> autoReport.getValue() && suspicionReport.getValue());
    private final BoolValue autoBlock = new BoolValue("AutoBlock", true);
    private final BoolValue legitScaffold = new BoolValue("Legit Scaffold", true);
    private final BoolValue noSlow = new BoolValue("NoSlow", true);
    private final BoolValue nuker = new BoolValue("Nuker", true);
    private final BoolValue bedNuker = new BoolValue("Bed Nuker", true);
    private final BoolValue scaffold = new BoolValue("Scaffold", true);
    private final BoolValue aimAssist = new BoolValue("AimAssist", false);
    private final BoolValue autoClicker = new BoolValue("AutoClicker", false);
    private final BoolValue blink = new BoolValue("Blink", false);
    private final BoolValue fastMine = new BoolValue("FastMine", false);
    private final BoolValue noFall = new BoolValue("NoFall", false);
    private final BoolValue protocol = new BoolValue("Protocol", false);

    // The source thresholds target 1.8 Hypixel: explicitly opt into the added experimental suite.
    private final BoolValue ravenChecks = new BoolValue("Raven Checks", false);
    private final NumberValue ravenVlScale = new NumberValue("Raven VL Scale", 1, 0.1F, 5, 0.1F, () -> ravenChecks.getValue());
    private final NumberValue ravenCooldown = new NumberValue("Raven Cooldown", 0, 0, 60, 1, () -> ravenChecks.getValue());
    private final NumberValue ravenRange = new NumberValue("Raven Range", 64, 8, 128, 1, () -> ravenChecks.getValue());
    private final NumberValue ravenPlayerLimit = new NumberValue("Raven Player Limit", 32, 2, 64, 1, () -> ravenChecks.getValue());
    private final MultiBoolValue ravenMovement = new MultiBoolValue("Raven Movement", () -> ravenChecks.getValue(),
            new BoolValue("Grim Sprint", true),
            new BoolValue("NCP Velocity", true),
            new BoolValue("Grim Speed", true),
            new BoolValue("NCP Fly", true),
            new BoolValue("Vulcan Glide", true),
            new BoolValue("Grim Elude", true),
            new BoolValue("Grim Vertical", true),
            new BoolValue("Grim NoFall", true),
            new BoolValue("NCP Teleport", true),
            new BoolValue("Grim VClip", true),
            new BoolValue("Verus Velocity", true),
            new BoolValue("NCP Spider", true),
            new BoolValue("Matrix AirJump", true),
            new BoolValue("Vulcan Step", true),
            new BoolValue("Intave Jump", true),
            new BoolValue("Grim JumpPrediction", true),
            new BoolValue("AAC BunnyHop", true),
            new BoolValue("Grim Jesus", true),
            new BoolValue("Vulcan Speed", true),
            new BoolValue("Matrix Acceleration", true),
            new BoolValue("NCP Sprint", true),
            new BoolValue("Grim Swim", true),
            new BoolValue("NCP Ladder", true),
            new BoolValue("NCP Tower", true),
            new BoolValue("AAC Freecam", true),
            new BoolValue("Pika Velocity", true),
            new BoolValue("Grim Velocity", true),
            new BoolValue("Grim GroundSpoof", true),
            new BoolValue("Grim NegativeTimer", true),
            new BoolValue("Grim Timer", true),
            new BoolValue("NCP MoveConsistency", true),
            new BoolValue("NCP Hover", true),
            new BoolValue("Grim Strafe", true),
            new BoolValue("Grim AirSpeed", true)
    );
    private final MultiBoolValue ravenAim = new MultiBoolValue("Raven Aim", () -> ravenChecks.getValue(),
            new BoolValue("Grim Aim", true),
            new BoolValue("Vulcan Aim", true),
            new BoolValue("Karhu SmoothAim", true),
            new BoolValue("Matrix Angle", true),
            new BoolValue("Intave Heuristic", true),
            new BoolValue("Grim SimAim", true),
            new BoolValue("Grim AimGCD", true),
            new BoolValue("Vulcan RotationSpeed", true),
            new BoolValue("Vulcan AimFlick", true),
            new BoolValue("Karhu PitchLock", true),
            new BoolValue("Matrix SilentAim", true),
            new BoolValue("Grim AimAccel", true),
            new BoolValue("NCP AimSnap", true),
            new BoolValue("Karhu AimTracking", true),
            new BoolValue("Grim LookAway", true),
            new BoolValue("Grim LegitScaff", true),
            new BoolValue("Intave GCD", true),
            new BoolValue("Vulcan DivisorY", true),
            new BoolValue("Intave StdDev", true),
            new BoolValue("Intave Reset", true)
    );
    private final MultiBoolValue ravenCombat = new MultiBoolValue("Raven Combat", () -> ravenChecks.getValue(),
            new BoolValue("Vulcan AutoBlock", true),
            new BoolValue("Grim Reach", true),
            new BoolValue("NCP KillAura", true),
            new BoolValue("Vulcan KillAura", true),
            new BoolValue("Hit Miss Ratio", true),
            new BoolValue("Matrix Combine", true),
            new BoolValue("Grim Hitbox", true),
            new BoolValue("Grim InvisibleHit", true),
            new BoolValue("Karhu MultiAura", true),
            new BoolValue("Verus Reach", true),
            new BoolValue("Intave AutoBlock", true),
            new BoolValue("NCP AutoClicker", true),
            new BoolValue("Karhu AutoClicker", true),
            new BoolValue("Hawk AutoBlock", true),
            new BoolValue("AAC Reach", true),
            new BoolValue("Intave Rhythm", true),
            new BoolValue("Grim SimHitbox", true),
            new BoolValue("NCP WallHit", true),
            new BoolValue("NCP HitboxV", true),
            new BoolValue("Karhu FastHit", true),
            new BoolValue("Vulcan Criticals", true),
            new BoolValue("Matrix AutoClicker", true),
            new BoolValue("Grim MultiAuraB", true),
            new BoolValue("Intave ClickStats", true),
            new BoolValue("Intave PreAttack", true)
    );
    private final MultiBoolValue ravenWorld = new MultiBoolValue("Raven World", () -> ravenChecks.getValue(),
            new BoolValue("NCP Scaffold", true),
            new BoolValue("Vulcan Scaffold", true),
            new BoolValue("NCP Nuker", true),
            new BoolValue("NCP BedNuker", true),
            new BoolValue("Grim BedSnap", true),
            new BoolValue("Karhu BedNuker", true),
            new BoolValue("Vulcan FastPlace", true),
            new BoolValue("Grim BreakThrough", true),
            new BoolValue("NCP AutoTool", true),
            new BoolValue("Grim FastMine", true)
    );
    private final MultiBoolValue ravenPlayer = new MultiBoolValue("Raven Player", () -> ravenChecks.getValue(),
            new BoolValue("NCP NoSlow", true),
            new BoolValue("Grim Rotation", true),
            new BoolValue("Vulcan Rotation", true),
            new BoolValue("Verus NoSlow", true),
            new BoolValue("NCP NoSwing", true),
            new BoolValue("Grim Backtrack", true),
            new BoolValue("NCP FakeLag", true),
            new BoolValue("Verus VelocityDir", true),
            new BoolValue("NCP WrongTurn", true),
            new BoolValue("NCP FastEat", true),
            new BoolValue("NCP Regen", true),
            new BoolValue("Karhu FastInventory", true)
    );

    // Entity identity prevents respawns with a reused UUID/id inheriting motion history.
    private final Map<PlayerEntity, AntiCheatTracker> trackers = new IdentityHashMap<>();
    private final AntiCheatMining<BlockPos> mining = new AntiCheatMining<>();
    private final AntiCheatAlerts alerts = new AntiCheatAlerts();
    private final AntiCheatSuspicion suspicion = new AntiCheatSuspicion();
    private final RavenDetector raven = new RavenDetector(mc, this::flagRaven);
    private final Set<UUID> reported = new HashSet<>();
    private final Map<UUID, String> pendingReports = new LinkedHashMap<>();
    private final Map<UUID, String> pendingParty = new LinkedHashMap<>();
    private ClientWorld observedWorld;
    private PlayerEntity observedPlayer;
    private volatile long lastPacket;
    private long lastPing, nextCommand;
    private boolean cheatUIVisible = true;

    public AntiCheat() { super("AntiCheat", Category.World); }

    /** Main-thread lookup for consumers such as AutoL; dead/removed victims still have scores. */
    public int getSuspicionScore(UUID player) {
        if (!isNativeBehaviorActive() || mc.world == null || mc.player == null || mc.getNetworkHandler() == null) return 0;
        suspicion.synchronizeSession(mc.getNetworkHandler().getConnection());
        return suspicion.score(player);
    }

    @Override
    public void onEnable() { reset(); suspicion.clear(); cheatUIVisible = true; }

    @Override
    public void onDisable() { reset(); suspicion.clear(); }

    @EventTarget
    public void onWorld(WorldEvent event) {
        reset();
        if (event.getWorld() == null) suspicion.clear();
    }

    private void reset() {
        raven.clear();
        trackers.clear();
        mining.clear();
        alerts.clear();
        reported.clear();
        pendingReports.clear();
        pendingParty.clear();
        lastPacket = lastPing = nextCommand = 0;
        observedWorld = null;
        observedPlayer = null;
    }

    @EventTarget
    public void onPacketObserved(PacketLogEvent event) {
        // Network thread only writes a timestamp; all entity/block access stays on the client thread.
        if (event.getDirection() == PacketEvent.Type.Received && !event.isPacketCancelled()) lastPacket = now();
    }

    private boolean active() {
        return isNativeBehaviorActive() && mc.player != null && mc.world != null && !mc.isInSingleplayer()
                && (!disableOutsideMinigames.getValue() || AntiCheatEnvironment.hypixel(mc)
                && AntiCheatEnvironment.inMinigame(AntiCheatEnvironment.sidebar(mc)));
    }

    private void synchronizeWorld() {
        suspicion.synchronizeSession(mc.getNetworkHandler() == null ? null : mc.getNetworkHandler().getConnection());
        if (observedWorld != mc.world || observedPlayer != mc.player) {
            reset();
            observedWorld = mc.world;
            observedPlayer = mc.player;
        }
    }

    @EventTarget
    public void onKey(KeyInputEvent event) {
        if (active() && mc.currentScreen == null && cheatUI.getValue() && cheatUIKey.getValue() > 0
                && event.getAction() == GLFW.GLFW_PRESS && event.getKey() == cheatUIKey.getValue())
            cheatUIVisible = !cheatUIVisible;
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!active() || !cheatUI.getValue() || !cheatUIVisible || mc.options.hudHidden) return;
        Map<UUID, PlayerEntity> visible = new HashMap<>();
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (!ignored(player, false)) visible.put(player.getUuid(), player);
        }
        List<AntiCheatScoreHud.Row> rows = new ArrayList<>();
        for (var row : suspicion.ranking(visible.keySet(), 15)) {
            PlayerEntity player = visible.get(row.player());
            Formatting color = player.getScoreboardTeam() == null ? Formatting.GRAY : player.getScoreboardTeam().getColor();
            if (!color.isColor()) color = Formatting.GRAY;
            rows.add(new AntiCheatScoreHud.Row(row.score(), Text.literal(player.getGameProfile().name()).formatted(color)));
        }
        AntiCheatScoreHud.render(mc, event.getContext(), rows, cheatUIX.getValue().intValue(), cheatUIY.getValue().intValue());
    }

    @EventTarget
    public void onTick(TickEvent event) {
        synchronizeWorld();
        if (!active()) { reset(); return; }
        long time = now();
        Set<UUID> present = new HashSet<>();
        if (ravenChecks.getValue()) {
            Set<String> selected = ravenSelection();
            if (!selected.isEmpty()) {
                raven.tick(new RavenChecks.Settings(selected, ravenVlScale.getValue().doubleValue(),
                                ravenCooldown.getValue().longValue() * 1000),
                        player -> !ignored(player, false), this::teammate, this::bot,
                        ravenRange.getValue().doubleValue(), ravenPlayerLimit.getValue().intValue(), time);
                for (PlayerEntity player : mc.world.getPlayers()) if (!ignored(player, false)) present.add(player.getUuid());
            } else raven.clear();
        } else raven.clear();
        Set<PlayerEntity> eligible = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<AntiCheatCheck> checks = enabledChecks();
        String sidebar = AntiCheatEnvironment.sidebar(mc);
        boolean adventure = mc.interactionManager != null && mc.interactionManager.getCurrentGameMode() == GameMode.ADVENTURE;
        boolean noFallAllowed = !adventure && !sidebar.contains("lobby") && !sidebar.contains("pit")
                && !sidebar.contains("replay") && !sidebar.contains("skywars") && !sidebar.contains("build battle");
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (ignored(player)) continue;
            eligible.add(player);
            present.add(player.getUuid());
            AntiCheatTracker tracker = trackers.computeIfAbsent(player, ignored -> new AntiCheatTracker());
            BlockPos below = BlockPos.ofFloored(player.getX(), player.getY() - 1, player.getZ());
            boolean voidBelow = true;
            for (int depth = 2; depth <= 5; depth++) {
                BlockPos pos = player.getBlockPos().down(depth);
                if (!mc.world.isChunkLoaded(pos) || !mc.world.getBlockState(pos).isAir()) { voidBelow = false; break; }
            }
            AntiCheatCheck check = tracker.update(new AntiCheatTracker.Sample(
                    player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch(),
                    player.isSneaking(), player.isSprinting(), player.isUsingItem(), player.isBlocking(),
                    player.handSwinging, player.handSwingTicks, player.getMainHandStack().getItem() instanceof BlockItem,
                    player.getMainHandStack().isOf(Items.FISHING_ROD), mc.world.getBlockState(below).isAir(), voidBelow,
                    player.hurtTime > 0, player.getMainHandStack().isOf(Items.LADDER), adventure,
                    lastPacket != 0 && time - lastPacket < 100, noFallAllowed,
                    !sidebar.contains("build battle"), mc.player.squaredDistanceTo(player)), checks);
            if (check != null) flag(player, check, "Client tick observation");
        }
        trackers.keySet().retainAll(eligible);
        alerts.retain(present);
        pendingReports.keySet().retainAll(present);
        pendingParty.keySet().retainAll(present);
        mining.expire(time);
        flushCommand(time);
    }

    private boolean ignored(PlayerEntity player) {
        return ignored(player, true);
    }

    private boolean ignored(PlayerEntity player, boolean legacyMovement) {
        if (player == mc.player || !player.isAlive() || player.isRemoved() || player.age < 40
                || player.isSpectator() || player.isCreative() || player.getAbilities().flying
                || player.isGliding() || player.hasVehicle()
                || legacyMovement && (player.isTouchingWater() || player.isInLava() || player.isClimbing())) return true;
        String name = player.getGameProfile().name();
        if (instance.getFriendManager().isFriend(name)) return true;
        if (bot(player)) return true;
        return ignoreTeammates.getValue() && teammate(player);
    }

    private boolean teammate(PlayerEntity player) {
        Teams teams = getModule(Teams.class);
        return mc.player.isTeammate(player) || teams != null && teams.isTeam(player);
    }

    private boolean bot(PlayerEntity player) {
        AntiBot module = getModule(AntiBot.class);
        return module != null && module.isEnabled() && module.isBot(player);
    }

    private Set<String> ravenSelection() {
        Set<String> selected = new HashSet<>();
        for (MultiBoolValue group : List.of(ravenMovement, ravenAim, ravenCombat, ravenWorld, ravenPlayer)) {
            for (BoolValue value : group.getValues()) if (value.getValue()) selected.add(value.getName());
        }
        return selected;
    }

    public void observeRavenPacket(Packet<?> packet) {
        synchronizeWorld();
        if (ravenChecks.getValue() && active()) raven.packet(packet);
    }

    private Set<AntiCheatCheck> enabledChecks() {
        EnumSet<AntiCheatCheck> result = EnumSet.noneOf(AntiCheatCheck.class);
        for (AntiCheatCheck check : AntiCheatCheck.values()) if (enabled(check)) result.add(check);
        return result;
    }

    private boolean enabled(AntiCheatCheck check) {
        return switch (check) {
            case AUTO_BLOCK -> autoBlock.getValue(); case LEGIT_SCAFFOLD -> legitScaffold.getValue();
            case NO_SLOW -> noSlow.getValue(); case NUKER -> nuker.getValue();
            case BED_NUKER -> nuker.getValue() && bedNuker.getValue(); case SCAFFOLD -> scaffold.getValue();
            case AIM_ASSIST -> aimAssist.getValue(); case AUTO_CLICKER -> autoClicker.getValue();
            case BLINK -> blink.getValue(); case FAST_MINE -> fastMine.getValue();
            case NO_FALL -> noFall.getValue(); case PROTOCOL -> protocol.getValue();
        };
    }

    /** Called after forceMainThread but BEFORE vanilla changes blocks. Never cancels a packet. */
    public void observeBlockPacket(Packet<?> packet) {
        synchronizeWorld();
        if (!active()) return;
        if (packet instanceof BlockBreakingProgressS2CPacket progress) {
            if (mc.world.getEntityById(progress.getEntityId()) instanceof PlayerEntity player && !ignored(player)) {
                trackers.computeIfAbsent(player, ignored -> new AntiCheatTracker()).breakAnimation(progress.getProgress());
                if (fastMine.getValue() || nuker.getValue()) mining.animation(player.getId(), progress.getPos().toImmutable(), progress.getProgress(), now());
            }
        } else if (packet instanceof BlockUpdateS2CPacket update) {
            observeBlock(update.getPos(), update.getState());
        } else if (packet instanceof ChunkDeltaUpdateS2CPacket update) {
            // Snapshot each old state before vanilla applies ANY update in this batch.
            Map<BlockPos, BlockState> changes = new LinkedHashMap<>();
            update.visitUpdates((pos, state) -> changes.put(pos.toImmutable(), state));
            observeBed(changes);
            changes.forEach(this::observeBlock);
        }
    }

    private void observeBlock(BlockPos pos, BlockState replacement) {
        if (!mc.world.isChunkLoaded(pos)) return;
        BlockState old = mc.world.getBlockState(pos);
        if (ravenChecks.getValue() && !localBedBreaker(pos)) raven.block(pos, old, replacement, now());
        if (!replacement.isAir()) return;
        var evidence = mining.consume(pos, now());
        if (old.isAir() || old.getBlock() instanceof BedBlock || old.getBlock() instanceof PlantBlock
                || old.isIn(BlockTags.LEAVES) || old.isIn(BlockTags.CLIMBABLE) || !old.getFluidState().isEmpty()) return;
        PlayerEntity breaker = null;
        if (evidence != null && mc.world.getEntityById(evidence.entityId()) instanceof PlayerEntity player
                && !ignored(player) && distanceSquared(player, pos) <= 36) {
            breaker = player;
            if (fastMine.getValue() && evidence.progress() > 4 && evidence.progress() < 10) {
                flag(player, AntiCheatCheck.FAST_MINE, "Break stage: " + evidence.progress());
            }
        }
        if (!nuker.getValue() || exposed(pos) || localBedBreaker(pos)) return;
        if (breaker == null) breaker = nearest(pos);
        if (breaker != null) flag(breaker, AntiCheatCheck.NUKER, Registries.BLOCK.getId(old.getBlock()) + " " + pos.toShortString());
    }

    private void observeBed(Map<BlockPos, BlockState> changes) {
        if (!enabled(AntiCheatCheck.BED_NUKER) || changes.size() != 2) return;
        Iterator<BlockPos> positions = changes.keySet().iterator();
        BlockPos a = positions.next(), b = positions.next();
        if (!changes.get(a).isAir() || !changes.get(b).isAir() || !mc.world.isChunkLoaded(a) || !mc.world.isChunkLoaded(b)) return;
        BlockState first = mc.world.getBlockState(a), second = mc.world.getBlockState(b);
        if (!(first.getBlock() instanceof BedBlock) || first.getBlock() != second.getBlock()
                || first.get(BedBlock.PART) == second.get(BedBlock.PART)
                || first.get(BedBlock.FACING) != second.get(BedBlock.FACING)
                || !a.offset(first.get(BedBlock.PART) == net.minecraft.block.enums.BedPart.FOOT
                ? first.get(BedBlock.FACING) : first.get(BedBlock.FACING).getOpposite()).equals(b)
                || exposed(a, b) || exposed(b, a) || localBedBreaker(a) || localBedBreaker(b)) return;
        PlayerEntity breaker = nearest(a);
        if (breaker == null) breaker = nearest(b);
        if (breaker != null) flag(breaker, AntiCheatCheck.BED_NUKER, a.toShortString());
    }

    private boolean exposed(BlockPos pos) {
        return exposed(pos, null);
    }

    private boolean exposed(BlockPos pos, BlockPos partner) {
        return !AntiCheatGeometry.enclosed(pos, partner, neighbor -> mc.world.isChunkLoaded(neighbor)
                && mc.world.getBlockState(neighbor).isOpaqueFullCube());
    }

    private PlayerEntity nearest(BlockPos pos) {
        PlayerEntity result = null;
        double closest = 36;
        for (PlayerEntity player : mc.world.getPlayers()) {
            double distance = distanceSquared(player, pos);
            if (!ignored(player) && distance <= closest) { result = player; closest = distance; }
        }
        return result;
    }

    private boolean localBedBreaker(BlockPos pos) {
        BedBreaker module = getModule(BedBreaker.class);
        return module != null && module.isEnabled() && distanceSquared(mc.player, pos) <= 36;
    }

    private static double distanceSquared(PlayerEntity player, BlockPos pos) {
        return player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    private void flag(PlayerEntity player, AntiCheatCheck check, String detail) {
        if (!enabled(check) || ignored(player)) return;
        long time = now();
        if (!alerts.allow(player.getUuid(), check, time, flagInterval.getValue().longValue() * 1000)) return;
        // Legacy checks have no separate detector cooldown: only accepted flags add the default weight.
        scoreFlag(player, check.displayName, time);
        emitFlag(player, check.displayName, detail, time);
    }

    private void flagRaven(RavenChecks.Detection detection) {
        if (!(detection.player().identity instanceof PlayerEntity player) || ignored(player, false)) return;
        long time = now();
        // The Raven callback already passed VL and detector cooldown. Like the script, count it
        // before throttling chat, so Flag interval / hidden chat never discard Raven score gains.
        scoreFlag(player, detection.check(), time);
        if (!alerts.allow(player.getUuid(), "Raven:" + detection.check(), time, flagInterval.getValue().longValue() * 1000)) return;
        emitFlag(player, detection.check(), "Raven VL: " + detection.vl(), time);
    }

    private boolean alarmEnabled(int level) {
        return thresholdAlarms.getValue() && switch (level) {
            case 50 -> alarm50.getValue(); case 80 -> alarm80.getValue(); case 100 -> alarm100.getValue();
            default -> false;
        };
    }

    private void scoreFlag(PlayerEntity player, String check, long time) {
        var update = suspicion.flag(player.getUuid(), check, this::alarmEnabled);
        for (int level : update.alarms()) {
            if (!hideChatAlerts.getValue()) Util.logToChat(Text.literal("[CheatUI] " + player.getGameProfile().name()
                    + " is now at " + update.score() + "% suspicion (" + level + "% alarm).")
                    .withColor(AntiCheatSuspicion.color(update.score()) & 0xFFFFFF));
        }
        if (!update.alarms().isEmpty()) {
            SoundEvent sound = switch (alarmSound.getValue()) {
                case "Bass" -> SoundEvents.BLOCK_NOTE_BLOCK_BASS.value();
                case "Orb" -> SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP;
                case "Harp" -> SoundEvents.BLOCK_NOTE_BLOCK_HARP.value();
                case "Break" -> SoundEvents.ENTITY_ITEM_BREAK.value();
                default -> SoundEvents.BLOCK_NOTE_BLOCK_PLING.value();
            };
            ping(sound, time);
        }
        if (suspicionReport.getValue() && update.score() >= reportAt.getValue().intValue()) queueReport(player);
    }

    private void queueReport(PlayerEntity player) {
        String name = player.getGameProfile().name();
        if (autoReport.getValue() && AntiCheatEnvironment.hypixel(mc) && name.matches("[A-Za-z0-9_]{1,16}")
                && !reported.contains(player.getUuid()) && pendingReports.size() < 64)
            pendingReports.putIfAbsent(player.getUuid(), "report " + name + " cheating");
    }

    private void ping(SoundEvent sound, long time) {
        if (shouldPing.getValue() && (lastPing == 0 || time - lastPing > 1500)) {
            mc.player.playSound(sound, 0.5F, 1.0F);
            lastPing = time;
        }
    }

    private void emitFlag(PlayerEntity player, String checkName, String detail, long time) {
        String name = player.getGameProfile().name();
        String message = name + " suspected " + checkName + " ("
                + String.format(Locale.ROOT, "%.1f", mc.player.distanceTo(player)) + "m, "
                + suspicion.score(player.getUuid()) + "% suspicion)";
        boolean hypixel = AntiCheatEnvironment.hypixel(mc);
        boolean validName = name.matches("[A-Za-z0-9_]{1,16}");
        if (!hideChatAlerts.getValue()) {
            var text = Text.literal(message).formatted(Formatting.GRAY).styled(style ->
                    style.withHoverEvent(new HoverEvent.ShowText(Text.literal(detail))));
            if (hypixel && validName) text.append(Text.literal(" [WDR]").formatted(Formatting.RED).styled(style ->
                    style.withClickEvent(new ClickEvent.RunCommand("/report " + name + " cheating"))
                            .withHoverEvent(new HoverEvent.ShowText(Text.literal("/report " + name + " cheating")))));
            Util.logToChat(text);
        }
        if (hypixel && validName) {
            if (!suspicionReport.getValue()) queueReport(player);
            if (sendPartyChat.getValue() && pendingParty.size() < 64) pendingParty.put(player.getUuid(), "pc " + message);
        }
        ping(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, time);
    }

    private void flushCommand(long time) {
        if (!autoReport.getValue()) pendingReports.clear();
        if (suspicionReport.getValue())
            pendingReports.keySet().removeIf(player -> suspicion.score(player) < reportAt.getValue().intValue());
        if (!sendPartyChat.getValue()) pendingParty.clear();
        if (!AntiCheatEnvironment.hypixel(mc)) { pendingReports.clear(); pendingParty.clear(); return; }
        if (mc.currentScreen != null || time < nextCommand) return;
        Map<UUID, String> queue = pendingReports.isEmpty() ? pendingParty : pendingReports;
        Iterator<Map.Entry<UUID, String>> iterator = queue.entrySet().iterator();
        if (!iterator.hasNext()) return;
        var command = iterator.next();
        mc.player.networkHandler.sendChatCommand(command.getValue());
        if (queue == pendingReports) reported.add(command.getKey());
        iterator.remove();
        nextCommand = time + 1000;
    }

    private static long now() { return System.nanoTime() / 1_000_000; }
}
