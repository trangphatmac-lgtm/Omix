package cn.omix.util.world.anticheat.raven;

import java.util.*;
import java.util.function.Consumer;

/**
 * Detection formulas adapted from the user-supplied HypixelACV2 Raven-bS script.
 * Legacy names identify heuristic variants, not official implementations of those anticheats.
 * Runs on bounded snapshots supplied by RavenDetector; no scripting or external I/O.
 */
@SuppressWarnings("unchecked")
public final class RavenChecks {
    final Map<RavenPlayer, Map<String, Object>> anticheatPlayers = new LinkedHashMap<>();
    final List<Object[]> blockBreaks = new ArrayList<>(), blockPlaces = new ArrayList<>();
    final RavenWorld world;
    private final Consumer<Detection> sink;
    private RavenPlayer localPlayer;
    private Set<String> enabled = Set.of();
    long now;
    int localHitTick;
    public record Detection(RavenPlayer player, String check, int vl) {}
    public record Settings(Set<String> enabled, double vlScale, long minimumCooldown) {
        public Settings { enabled = Set.copyOf(enabled); }
    }

    public RavenChecks(RavenWorld world, Consumer<Detection> sink) {
        this.world = world;
        this.sink = sink;
    }

    public void clear() {
        anticheatPlayers.clear(); blockBreaks.clear(); blockPlaces.clear();
        localPlayer = null; localHitTick = 0; enabled = Set.of();
    }

    public void tick(List<RavenPlayer> players, RavenPlayer local, long time, Settings settings) {
        now = time;
        localPlayer = local;
        // Changing check selection cannot resurrect old VL/evidence on re-enable.
        if (!enabled.equals(settings.enabled())) {
            anticheatPlayers.clear();
            enabled = settings.enabled();
        }
        anticheatPlayers.keySet().retainAll(players);
        blockBreaks.removeIf(e -> now - (Long)e[1] > 3000);
        blockPlaces.removeIf(e -> now - (Long)e[1] > 3000);
        // Snapshot everyone before evaluating anyone: combat attribution is order independent.
        for (RavenPlayer player : players) {
            var data = anticheatPlayers.computeIfAbsent(player, ignored -> new HashMap<>());
            if (player.discontinuity) data.clear();
            updatePlayerData(player, data);
            data.put("sampleCount", (int)data.getOrDefault("sampleCount", 0) + 1);
        }
        if (local != null && local.hurtTime == local.maxHurtTime && local.hurtTime > 0) localHitTick = local.ticks;
        for (var entry : anticheatPlayers.entrySet()) {
            RavenPlayer player = entry.getKey();
            var data = entry.getValue();
            if ((int)data.get("sampleCount") < 20 || player.discontinuity) continue;
            boolean aimContext = isMeleeWeapon(player.heldItem) && isLookingAtPlayer(data);
            for (RavenCheck check : RavenCheck.ALL) {
                if (!enabled.contains(check.name()) || (!player.suspect && !check.victimBased())) continue;
                if (isSuspectAimCheck(check.name()) && !aimContext) continue;
                int threshold = Math.max(1, (int)Math.ceil(check.threshold() * settings.vlScale()));
                int cooldown = (int)Math.min(Integer.MAX_VALUE, Math.max(check.cooldown(), settings.minimumCooldown()));
                run(check.name(), data, cooldown, threshold);
            }
            // Bound all rolling collections, including malformed/stale script histories.
            for (Object value : data.values()) {
                if (value instanceof List<?> list && list.size() > 256) list.subList(0, list.size() - 256).clear();
            }
            data.replaceAll((key, value) -> key.endsWith("_VL") && value instanceof Integer number
                    ? Math.min(10000, Math.max(0, number)) : value);
        }
    }

    public void block(RavenVec3 position, boolean broken, boolean bed, long time) {
        var events = broken ? blockBreaks : blockPlaces;
        events.add(new Object[]{position, time, bed});
        if (events.size() > 200) events.remove(0);
    }

    public void velocity(RavenPlayer player, double horizontal) {
        var data = anticheatPlayers.get(player);
        if (data != null && horizontal > 0.5) {
            data.put("VelocityD_Expected", horizontal);
            data.put("VelocityD_Tick", player.ticks);
            data.put("VelocityD_StartPos", player.position);
        }
    }

    public int violation(RavenPlayer player, String check) {
        var data = anticheatPlayers.get(player);
        return data == null ? 0 : (int)data.getOrDefault(RavenCheck.byName(check).key() + "_VL", 0);
    }

    private void printFlag(Map<String, Object> data, String check, int vl) {
        RavenPlayer player = (RavenPlayer)data.get("entity");
        // Victim-based checks may identify an ignored teammate/local player as the attacker.
        if (player != null && player.suspect && enabled.contains(check)) sink.accept(new Detection(player, check, vl));
    }

    private boolean isBot(Map<String, Object> data) {
        return data.get("entity") instanceof RavenPlayer player && player.bot;
    }

    private boolean isSameTeamAsLocal(Map<String, Object> data) {
        return data.get("entity") instanceof RavenPlayer player && player.teammate;
    }

    private void run(String name, Map<String, Object> data, int cooldown, int threshold) {
        switch (name) {
            case "NCP NoSlow" -> NoSlowA(data, cooldown, threshold, false);
            case "Vulcan AutoBlock" -> AutoBlockA(data, cooldown, threshold, false);
            case "Grim Sprint" -> SprintA(data, cooldown, threshold, false);
            case "NCP Velocity" -> VelocityA(data, cooldown, threshold, false);
            case "Grim Rotation" -> RotationA(data, cooldown, threshold, false);
            case "NCP Scaffold" -> ScaffoldA(data, cooldown, threshold, false);
            case "Vulcan Scaffold" -> ScaffoldC(data, cooldown, threshold, false);
            case "Grim Speed" -> SpeedA(data, cooldown, threshold, false);
            case "NCP Fly" -> FlyA(data, cooldown, threshold, false);
            case "Vulcan Glide" -> GlideA(data, cooldown, threshold, false);
            case "Grim Elude" -> EludeA(data, cooldown, threshold, false);
            case "Grim Vertical" -> FallSpeedA(data, cooldown, threshold, false);
            case "Grim NoFall" -> NoFallA(data, cooldown, threshold, false);
            case "Grim Reach" -> ReachA(data, cooldown, threshold, false);
            case "NCP Teleport" -> TeleportA(data, cooldown, threshold, false);
            case "Grim VClip" -> VClipA(data, cooldown, threshold, false);
            case "Verus Velocity" -> VelocityB(data, cooldown, threshold, false);
            case "Vulcan Rotation" -> RotationB(data, cooldown, threshold, false);
            case "NCP Spider" -> SpiderA(data, cooldown, threshold, false);
            case "Matrix AirJump" -> AirJumpA(data, cooldown, threshold, false);
            case "Vulcan Step" -> StepA(data, cooldown, threshold, false);
            case "Intave Jump" -> JumpA(data, cooldown, threshold, false);
            case "Grim JumpPrediction" -> JumpPredictionA(data, cooldown, threshold, false);
            case "AAC BunnyHop" -> JumpB(data, cooldown, threshold, false);
            case "Grim Jesus" -> JesusA(data, cooldown, threshold, false);
            case "Verus NoSlow" -> NoSlowB(data, cooldown, threshold, false);
            case "NCP KillAura" -> KillAuraA(data, cooldown, threshold, false);
            case "Vulcan KillAura" -> KillAuraB(data, cooldown, threshold, false);
            case "Hit Miss Ratio" -> HitMissRatioA(data, cooldown, threshold, false);
            case "Matrix Combine" -> CombatCombineA(data, cooldown, threshold, false);
            case "Grim Hitbox" -> HitBoxA(data, cooldown, threshold, false);
            case "Grim InvisibleHit" -> InvisibleHitA(data, cooldown, threshold, false);
            case "Karhu MultiAura" -> MultiAuraA(data, cooldown, threshold, false);
            case "Verus Reach" -> ReachC(data, cooldown, threshold, false);
            case "Vulcan Speed" -> SpeedB(data, cooldown, threshold, false);
            case "Matrix Acceleration" -> SpeedC(data, cooldown, threshold, false);
            case "NCP Sprint" -> SprintB(data, cooldown, threshold, false);
            case "Grim Swim" -> SwimA(data, cooldown, threshold, false);
            case "NCP Ladder" -> LadderA(data, cooldown, threshold, false);
            case "NCP Tower" -> TowerA(data, cooldown, threshold, false);
            case "AAC Freecam" -> FreecamA(data, cooldown, threshold, false);
            case "Pika Velocity" -> VelocityC(data, cooldown, threshold, false);
            case "Grim Velocity" -> VelocityD(data, cooldown, threshold, false);
            case "NCP Nuker" -> NukerA(data, cooldown, threshold, false);
            case "Grim GroundSpoof" -> GroundSpoofA(data, cooldown, threshold, false);
            case "Intave AutoBlock" -> AutoBlockB(data, cooldown, threshold, false);
            case "NCP AutoClicker" -> AutoClickerA(data, cooldown, threshold, false);
            case "Karhu AutoClicker" -> AutoClickerB(data, cooldown, threshold, false);
            case "Hawk AutoBlock" -> AutoBlockC(data, cooldown, threshold, false);
            case "NCP NoSwing" -> NoSwingA(data, cooldown, threshold, false);
            case "Grim Aim" -> AimA(data, cooldown, threshold, false);
            case "Vulcan Aim" -> AimB(data, cooldown, threshold, false);
            case "Karhu SmoothAim" -> AimC(data, cooldown, threshold, false);
            case "AAC Reach" -> ReachB(data, cooldown, threshold, false);
            case "Matrix Angle" -> AngleA(data, cooldown, threshold, false);
            case "Intave Heuristic" -> IntaveHeuristic(data, cooldown, threshold, false);
            case "Grim SimAim" -> GrimSimAim(data, cooldown, threshold, false);
            case "Intave Rhythm" -> IntaveRhythm(data, cooldown, threshold, false);
            case "Grim SimHitbox" -> GrimSimHitbox(data, cooldown, threshold, false);
            case "Grim Backtrack" -> BacktrackA(data, cooldown, threshold, false);
            case "NCP FakeLag" -> FakeLagA(data, cooldown, threshold, false);
            case "Grim AimGCD" -> AimGcdA(data, cooldown, threshold, false);
            case "Vulcan RotationSpeed" -> RotationSpeedA(data, cooldown, threshold, false);
            case "Vulcan AimFlick" -> AimFlickA(data, cooldown, threshold, false);
            case "Karhu PitchLock" -> PitchLockA(data, cooldown, threshold, false);
            case "Matrix SilentAim" -> SilentAimA(data, cooldown, threshold, false);
            case "NCP WallHit" -> WallHitA(data, cooldown, threshold, false);
            case "NCP HitboxV" -> HitboxVA(data, cooldown, threshold, false);
            case "Karhu FastHit" -> FastHitA(data, cooldown, threshold, false);
            case "Verus VelocityDir" -> VelocityDirA(data, cooldown, threshold, false);
            case "Vulcan Criticals" -> CriticalsA(data, cooldown, threshold, false);
            case "Matrix AutoClicker" -> AutoClickerStatA(data, cooldown, threshold, false);
            case "Grim MultiAuraB" -> MultiAuraB(data, cooldown, threshold, false);
            case "NCP BedNuker" -> BedNukerA(data, cooldown, threshold, false);
            case "Grim BedSnap" -> BedSnapA(data, cooldown, threshold, false);
            case "Karhu BedNuker" -> BedNukerB(data, cooldown, threshold, false);
            case "Grim AimAccel" -> AimD(data, cooldown, threshold, false);
            case "NCP AimSnap" -> AimE(data, cooldown, threshold, false);
            case "Karhu AimTracking" -> AimF(data, cooldown, threshold, false);
            case "Vulcan FastPlace" -> FastPlaceA(data, cooldown, threshold, false);
            case "Grim NegativeTimer" -> TimerA(data, cooldown, threshold, false);
            case "Grim Timer" -> TimerB(data, cooldown, threshold, false);
            case "Grim BreakThrough" -> BreakThroughA(data, cooldown, threshold, false);
            case "Grim LookAway" -> LookAwayA(data, cooldown, threshold, false);
            case "NCP AutoTool" -> AutoToolA(data, cooldown, threshold, false);
            case "Grim FastMine" -> FastMineA(data, cooldown, threshold, false);
            case "Grim LegitScaff" -> LegitScaffA(data, cooldown, threshold, false);
            case "Intave GCD" -> GcdB(data, cooldown, threshold, false);
            case "Vulcan DivisorY" -> DivisorYA(data, cooldown, threshold, false);
            case "Intave ClickStats" -> ClickStatsA(data, cooldown, threshold, false);
            case "Intave PreAttack" -> PreAttackA(data, cooldown, threshold, false);
            case "Intave StdDev" -> StdDevA(data, cooldown, threshold, false);
            case "Intave Reset" -> RotationResetA(data, cooldown, threshold, false);
            case "NCP MoveConsistency" -> MoveConsistencyA(data, cooldown, threshold, false);
            case "NCP Hover" -> HoverA(data, cooldown, threshold, false);
            case "NCP WrongTurn" -> WrongTurnA(data, cooldown, threshold, false);
            case "Grim Strafe" -> StrafeA(data, cooldown, threshold, false);
            case "NCP FastEat" -> FastEatA(data, cooldown, threshold, false);
            case "Grim AirSpeed" -> AirSpeedA(data, cooldown, threshold, false);
            case "NCP Regen" -> RegenA(data, cooldown, threshold, false);
            case "Karhu FastInventory" -> FastInventoryA(data, cooldown, threshold, false);
            default -> throw new IllegalArgumentException(name);
        }
    }

    boolean isPlacingNearby(Map<String, Object> p) {
        RavenVec3 position = (RavenVec3) p.get("position");
        if (position == null) return false;
        for (Object[] bp : blockPlaces) {
            RavenVec3 blockPos = (RavenVec3) bp[0];
            long time = (Long) bp[1];
            if (now - time > 300 || now - time < 0) continue;
            if (Math.abs(blockPos.x - position.x) <= 4 && Math.abs(blockPos.z - position.z) <= 4 && Math.abs(blockPos.y - position.y) <= 3) return true;
        }
        return false;
    }

    void updatePlayerData(RavenPlayer p, Map <String, Object > playerData) {
        int currentTick = p.getTicksExisted();
        boolean lastCrouching = (Boolean) playerData.getOrDefault( "crouching", false);
        boolean lastSprinting = (Boolean) playerData.getOrDefault( "sprinting", false);
        boolean lastUsing = (Boolean) playerData.getOrDefault( "using", false);
        boolean lastOnEdge = (Boolean) playerData.getOrDefault( "onEdge", false);
        int lastSwing = (Integer) playerData.getOrDefault( "swingProgress", 0);
        boolean lastOnGround = (Boolean) playerData.getOrDefault( "onGround", false);
        RavenItem lastHeldItem = (RavenItem) playerData.getOrDefault( "heldItem", null);
        double lastDeltaY = (Double) playerData.getOrDefault( "lastDeltaY", 0d);
        boolean isCrouching = p.isSneaking();
        boolean isSprinting = p.isSprinting();
        boolean isUsing = p.isUsingItem();
        int swingProgress = p.getSwingProgress();
        boolean isCollided = p.isCollided();
        boolean isDead = p.isDead();
        boolean isBurning = p.isBurning();
        boolean onEdge = p.isOnEdge();
        RavenVec3 currentPosition = p.getPosition();
        RavenVec3 lastPosition = p.getLastPosition();
        double deltaX = currentPosition.x - lastPosition.x;
        double deltaY = currentPosition.y - lastPosition.y;
        double deltaZ = currentPosition.z - lastPosition.z;
        float rotationYaw = p.getYaw();
        float moveYaw = getMoveYaw(deltaX, deltaZ, rotationYaw);
        float pitch = p.getPitch();
        float prevYaw = p.getPrevYaw();
        float prevPitch = p.getPrevPitch();
        boolean onGround = p.onGround();
        String name = p.getName();
        String uuid = p.getUUID();
        String displayName = p.getDisplayName();
        int hurtTime = p.getHurtTime();
        int maxHurtTime = p.getMaxHurtTime();
        RavenPlayer riding = p.getRidingEntity();
        RavenItem heldItem = p.getHeldItem();
        String blockOn = world.getBlockAt((int) Math.floor(currentPosition.x), (int) Math.floor(currentPosition.y) - 1, (int) Math.floor(currentPosition.z)).name;
        boolean isInWater = p.isInWater();
        boolean isInLava = p.isInLava();
        boolean isOnLadder = p.isOnLadder();
        boolean isCreative = p.isCreative();
        float health = p.getHealth();
        float absorption = p.getAbsorption();
        List<Object[]> potionEffects = p.getPotionEffects();
        double distToGround = p.distanceToGround();
        playerData.put("lastCrouching", lastCrouching);
        playerData.put("lastSprinting", lastSprinting);
        playerData.put("lastUsing", lastUsing);
        playerData.put("lastOnEdge", lastOnEdge);
        playerData.put("lastSwinging", lastSwing);
        playerData.put("lastBlockOn", playerData.getOrDefault("blockOn","air"));
        playerData.put("lastOnGround", lastOnGround);
        playerData.put("lastHeldItem", lastHeldItem);
        playerData.put("lastDeltaY", deltaY);
        playerData.put("uuid", uuid);
        playerData.put("name", name);
        playerData.put("displayName", displayName);
        playerData.put("hurtTime", hurtTime);
        playerData.put("maxHurtTime", maxHurtTime);
        playerData.put("inWater", isInWater);
        playerData.put("inLava", isInLava);
        playerData.put("onLadder", isOnLadder);
        playerData.put("creative", isCreative);
        playerData.put("health", health);
        playerData.put("absorption", absorption);
        playerData.put("potionEffects", potionEffects);
        playerData.put("entity", p);
        playerData.put("distToGround", distToGround);
        int lastHurtTime = (int) playerData.getOrDefault("lastHurtTime", 0);
        playerData.put("lastHurtTime", hurtTime);
        if (hurtTime == maxHurtTime && hurtTime > 0 && lastHurtTime < maxHurtTime) {
            playerData.put("lastHitTick", currentTick);
        }
        playerData.put("ticksExisted", currentTick);
        playerData.put("position", currentPosition);
        playerData.put("lastPosition", lastPosition);
        playerData.put("yaw", rotationYaw);
        playerData.put("pitch", pitch);
        playerData.put("previousYaw", prevYaw);
        playerData.put("previousPitch", prevPitch);
        List<Float> yawHistory = (List<Float>) playerData.get("yawHistory");
        if (yawHistory == null) {
            yawHistory = new ArrayList<>(20);
            playerData.put("yawHistory", yawHistory);
        } else if (yawHistory.size() >= 20) {
            yawHistory.remove(yawHistory.size() - 1);
        }
        yawHistory.add(0, rotationYaw);
        playerData.put("swingProgress", swingProgress);
        playerData.put("riding", riding);
        playerData.put("dead", isDead);
        playerData.put("invisible", p.isInvisible());
        playerData.put("onGround", onGround);
        playerData.put("heldItem", heldItem);
        playerData.put("collided", isCollided);
        playerData.put("burning", isBurning);
        playerData.put("moveYaw", moveYaw);
        playerData.put("onEdge", onEdge);
        playerData.put("blockOn", blockOn);
        playerData.put("crouching", isCrouching);
        playerData.put("sprinting", isSprinting);
        playerData.put("using", isUsing);
        playerData.put("blocking", p.blocking);
        List<RavenVec3> previousPositions = (List<RavenVec3>) playerData.get("previousPositions");
        if (previousPositions == null) {
            previousPositions = new ArrayList<>(20);
            playerData.put("previousPositions", previousPositions);
        } else if (previousPositions.size() >= 20) {
            previousPositions.remove(previousPositions.size() - 1);
        }
        previousPositions.add(0, currentPosition);
        double totalMove = Math.abs(deltaX) + Math.abs(deltaZ);
        int freezeTicks = (int) playerData.getOrDefault("FreezeA_Ticks", 0);
        int freezeActivity = (int) playerData.getOrDefault("FreezeA_Activity", 0);
        boolean freezeWasMoving = (Boolean) playerData.getOrDefault("FreezeA_WasMoving", false);
        double yawChange = Math.abs(rotationYaw - prevYaw);
        if (yawChange > 180) yawChange = 360 - yawChange;
        if (riding != null || isDead) {
            freezeTicks = 0;
            freezeActivity = 0;
            freezeWasMoving = false;
        } else if (totalMove < 1e-6 && Math.abs(deltaY) < 1e-6) {
            if (freezeTicks == 0 && previousPositions.size() > 1) {
                freezeWasMoving = previousPositions.get(0).distanceTo(previousPositions.get(1)) > 0.02;
            }
            freezeTicks++;
            if (yawChange > 0.5) freezeActivity++;
        } else {
            if (freezeTicks >= 5) {
                double jumpSize = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
                if (jumpSize > 0.5) {
                    playerData.put("FreezeA_JumpTick", currentTick);
                    playerData.put("FreezeA_JumpSize", jumpSize);
                    playerData.put("FreezeA_FreezeLen", freezeTicks);
                    playerData.put("FreezeA_Activity", freezeActivity);
                    playerData.put("FreezeA_WasMoving", freezeWasMoving);
                }
            }
            freezeTicks = 0;
            freezeActivity = 0;
            freezeWasMoving = false;
        }
        playerData.put("FreezeA_Ticks", freezeTicks);
        playerData.put("FreezeA_Activity", freezeActivity);
        playerData.put("FreezeA_WasMoving", freezeWasMoving);
        if (isCrouching && !lastCrouching) playerData.put("lastCrouchedTick", currentTick);
        if (!isCrouching && lastCrouching) playerData.put("lastStopCrouchingTick", currentTick);
        if (isSprinting && !lastSprinting) playerData.put("lastSprintingTick", currentTick);
        if (isUsing && !lastUsing) {
            playerData.put("lastUsingTick", currentTick);
            List<Integer> useTimes = (List<Integer>) playerData.get("useTimes");
            if (useTimes == null) {
                useTimes = new ArrayList<>();
                playerData.put("useTimes", useTimes);
            }
            useTimes.add(currentTick);
        }
        List<Integer> useTimes = (List<Integer>) playerData.get("useTimes");
        if (useTimes != null) {
            while (!useTimes.isEmpty() && currentTick - useTimes.get(0) > 10) {
                useTimes.remove(0);
            }
        }
        if (!isUsing && lastUsing) {
            playerData.put("lastStopUsingTick", currentTick);
            playerData.put("lastStopUsingItem", lastHeldItem);
        }
        if (lastSwing == 0 && swingProgress == 1) {
            playerData.put("lastSwingTick", currentTick);
            // Block+swing overlap: vanilla cannot swing while using an item (sword
            // block), so a swing landing while the use animation is active is an
            // autoblocker signature. Recorded for Intave AutoBlock.
            if (p.blocking) {
                List<Integer> blockSwingTimes = (List<Integer>) playerData.get("blockSwingTimes");
                if (blockSwingTimes == null) {
                    blockSwingTimes = new ArrayList<>();
                    playerData.put("blockSwingTimes", blockSwingTimes);
                }
                blockSwingTimes.add(currentTick);
                while (!blockSwingTimes.isEmpty() && currentTick - blockSwingTimes.get(0) > 40) blockSwingTimes.remove(0);
            }
            playerData.put("lastSwingItem", heldItem);
            List<Integer> swingTimes = (List<Integer>) playerData.get("swingTimes");
            if (swingTimes == null) {
                swingTimes = new ArrayList<>();
                playerData.put("swingTimes", swingTimes);
            }
            swingTimes.add(currentTick);
            // Intave click-pattern statistics need millisecond precision (tick
            // gaps are quantized and hide the jitter that separates human from bot).
            List<Long> swingTimesMs = (List<Long>) playerData.get("swingTimesMs");
            if (swingTimesMs == null) {
                swingTimesMs = new ArrayList<>();
                playerData.put("swingTimesMs", swingTimesMs);
            }
            swingTimesMs.add(now);
        }
        List<Integer> swingTimes = (List<Integer>) playerData.get("swingTimes");
        if (swingTimes != null) {
            while (!swingTimes.isEmpty() && currentTick - swingTimes.get(0) > 160) {
                swingTimes.remove(0);
            }
        }
        List<Long> swingTimesMs = (List<Long>) playerData.get("swingTimesMs");
        if (swingTimesMs != null) {
            while (!swingTimesMs.isEmpty() && now - swingTimesMs.get(0) > 8000) {
                swingTimesMs.remove(0);
            }
        }
        List<Object[]> miningCache = (List<Object[]>) playerData.get("miningCache");
        if (miningCache == null) {
            miningCache = new ArrayList<>();
            playerData.put("miningCache", miningCache);
        }
        if (lastSwing == 0 && swingProgress == 1 && riding == null && !isDead) {
            RavenVec3 eye = new RavenVec3(currentPosition.x, currentPosition.y + 1.62, currentPosition.z);
            RavenVec3 target = raycastBlock(eye, rotationYaw, pitch, 4.5);
            if (target != null) {
                String key = ((int) Math.floor(target.x)) + "," + ((int) Math.floor(target.y)) + "," + ((int) Math.floor(target.z));
                Object[] entry = null;
                for (Object[] e : miningCache) {
                    if (((String) e[0]).equals(key)) { entry = e; break; }
                }
                if (entry == null) {
                    String bName = world.getBlockAt((int) Math.floor(target.x), (int) Math.floor(target.y), (int) Math.floor(target.z)).name;
                    miningCache.add(new Object[]{key, bName, now, 1});
                } else if (now - ((Long) entry[2]) <= 3000) {
                    entry[3] = ((Integer) entry[3]) + 1;
                }
            }
        }
        if (!miningCache.isEmpty()) {
            while (now - ((Long) miningCache.get(0)[2]) > 3000) {
                miningCache.remove(0);
                if (miningCache.isEmpty()) break;
            }
        }
        if (!onEdge && lastOnEdge) playerData.put("lastEdgeTick", currentTick);
        if ((heldItem == null && lastHeldItem != null) || (heldItem != null && lastHeldItem == null) || (heldItem != null && lastHeldItem != null && (heldItem.meta != lastHeldItem.meta || !heldItem.name.equals(lastHeldItem.name)))) {
            playerData.put("lastItemChangeTick", currentTick);
            playerData.put("lastItemChangeTime", now);
            playerData.put("lastItemChangedTo", heldItem);
            List<Integer> itemChangeTimes = (List<Integer>) playerData.get("itemChangeTimes");
            if (itemChangeTimes == null) {
                itemChangeTimes = new ArrayList<>();
                playerData.put("itemChangeTimes", itemChangeTimes);
            }
            itemChangeTimes.add(currentTick);
            while (!itemChangeTimes.isEmpty() && currentTick - itemChangeTimes.get(0) > 20) itemChangeTimes.remove(0);
        }
        double totalMoveXYZ = Math.abs(deltaX) + Math.abs(deltaZ) + Math.abs(deltaY);
        if (totalMoveXYZ < 1e-6 && riding == null && !isDead) {
            playerData.put("staticTicks", (int) playerData.getOrDefault("staticTicks", 0) + 1);
        } else {
            playerData.put("staticTicks", 0);
        }
        if (isCrouching && !lastCrouching) {
            playerData.put("lastSneakOnTime", now);
            playerData.put("lastSneakOnTick", currentTick);
            List<Long> sneakToggles = (List<Long>) playerData.get("sneakToggleTimes");
            if (sneakToggles == null) {
                sneakToggles = new ArrayList<>();
                playerData.put("sneakToggleTimes", sneakToggles);
            }
            sneakToggles.add(now);
            while (sneakToggles.size() > 8) sneakToggles.remove(0);
        }
        if (!isCrouching && lastCrouching) {
            playerData.put("lastSneakOffTime", now);
            long lastOn = (long) playerData.getOrDefault("lastSneakOnTime", 0L);
            if (lastOn > 0) playerData.put("lastSneakOnDuration", now - lastOn);
        }
        // Intave-style no-slow accumulator: record horizontal distance only for ticks
        // where the player is actually using an item on the ground. Reset on any
        // other tick, so the measured speed never includes pre-use sprinting or
        // airborne jump momentum.
        if (isUsing && onGround && riding == null && !isInWater && !isInLava) {
            playerData.put("usingTicks", (int) playerData.getOrDefault("usingTicks", 0) + 1);
            playerData.put("usingDist", (double) playerData.getOrDefault("usingDist", 0d) + Math.sqrt(deltaX * deltaX + deltaZ * deltaZ));
        } else {
            playerData.put("usingTicks", 0);
            playerData.put("usingDist", 0d);
        }
        RavenVec3 lastSamplePos = (RavenVec3) playerData.get("lastSamplePosition");
        if (lastSamplePos == null) {
            playerData.put("lastSamplePosition", currentPosition);
        } else if (Math.abs(currentPosition.x - lastSamplePos.x) > 1e-6 || Math.abs(currentPosition.z - lastSamplePos.z) > 1e-6 || Math.abs(currentPosition.y - lastSamplePos.y) > 1e-6) {
            List<Long> updateTimes = (List<Long>) playerData.get("posUpdateTimes");
            if (updateTimes == null) {
                updateTimes = new ArrayList<>();
                playerData.put("posUpdateTimes", updateTimes);
            }
            updateTimes.add(now);
            while (updateTimes.size() > 60) updateTimes.remove(0);
            playerData.put("lastSamplePosition", currentPosition);
        }
        List<Object[]> placeHistory = (List<Object[]>) playerData.get("placeHistory");
        if (placeHistory == null) {
            placeHistory = new ArrayList<>();
            playerData.put("placeHistory", placeHistory);
        }
        for (Object[] bp : blockPlaces) {
            RavenVec3 blockPos = (RavenVec3) bp[0];
            long pt = (Long) bp[1];
            if (now - pt > 500 || now - pt < 0) continue;
            if (Math.abs(blockPos.x - currentPosition.x) <= 1.3 && Math.abs(blockPos.z - currentPosition.z) <= 1.3 && blockPos.y < currentPosition.y && currentPosition.y - blockPos.y <= 2.2) {
                if (placeHistory.isEmpty() || (Long) placeHistory.get(placeHistory.size() - 1)[2] != pt) {
                    placeHistory.add(new Object[]{blockPos.x, blockPos.z, pt});
                    if (placeHistory.size() > 16) placeHistory.remove(0);
                }
            }
        }
        if (blockOn.equals("air")) playerData.put("lastOnAir", currentTick);
        if (deltaY < -0.1 && lastDeltaY >= -0.1) {
            playerData.put("lastStartFallingTick", currentTick);
            playerData.put("lastStartFallingPosition", currentPosition);
        }
        if (onGround && !lastOnGround) {
            playerData.put("lastStopFallingTick", currentTick);
            playerData.put("lastStopFallingPosition", currentPosition);
        }
        if (!onGround && lastOnGround) playerData.put("lastOnGroundTick", currentTick);
    }

    float getMoveYaw(double deltaX, double deltaZ, float playerYaw) {
        if (Math.abs(deltaX) < 1e-8 && Math.abs(deltaZ) < 1e-8) return 0f;
        float moveAngle = (float) Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90;
        float relativeAngle = moveAngle - playerYaw;
        relativeAngle = (relativeAngle % 360 + 360) % 360;
        if (relativeAngle > 180) relativeAngle -= 360;
        return relativeAngle;
    }

    boolean checkSurroundingBlocks(RavenVec3 position) {
        double[][] offsets = {{0.5, 0}, {-0.5, 0}, {0, 0.5}, {0, -0.5}};
        int bottom = (int) Math.floor(position.y);
        int middle = bottom + 1;
        for (double[] offset : offsets) {
            double offsetX = offset[0];
            double offsetZ = offset[1];
            String blockLeg = world.getBlockAt((int) Math.floor(position.x + offsetX), bottom, (int) Math.floor(position.z + offsetZ)).name;
            String blockTorso = world.getBlockAt((int) Math.floor(position.x + offsetX), middle, (int) Math.floor(position.z + offsetZ)).name;
            if (!blockLeg.equals("air") || !blockTorso.equals("air")) return true;
        }
        return false;
    }

    boolean isInVoid(RavenVec3 position) {
        int startX = (int) Math.floor(position.x);
        int startZ = (int) Math.floor(position.z);
        int startY = (int) Math.floor(position.y) - 1;
        for (int y = startY; y >= world.bottomY(); y--) {
            String blockName = world.getBlockAt(startX, y, startZ).name;
            if (!isPassableBlock(blockName)) {
                return false;
            }
        }
        return true;
    }

    int voidBlocksBelow(RavenVec3 position) {
        // Counts consecutive passable blocks straight down from the feet. 3+ means
        // the player is bridging over a real drop (void); 0-2 means solid ground
        // under them, so it is not a bridge.
        int x = (int) Math.floor(position.x);
        int z = (int) Math.floor(position.z);
        int y = (int) Math.floor(position.y) - 1;
        int count = 0;
        while (y >= world.bottomY()) {
            if (!isPassableBlock(world.getBlockAt(x, y, z).name)) break;
            count++;
            y--;
        }
        return count;
    }

    boolean isPassableBlock(String name) {
        if (name.equals( "air") || name.equals( "water") || name.equals( "flowing_water") || name.equals( "lava") || name.equals( "flowing_lava")) return true;
        if (name.equals( "ladder") || name.equals( "vine") || name.equals( "web") || name.equals( "waterlily")) return true;
        if (name.equals( "tallgrass") || name.equals( "double_plant") || name.equals( "deadbush") || name.equals( "sapling")) return true;
        if (name.equals( "red_flower") || name.equals( "yellow_flower") || name.equals( "red_mushroom") || name.equals( "brown_mushroom")) return true;
        if (name.equals( "torch") || name.equals( "redstone_torch") || name.equals( "unlit_redstone_torch") || name.equals( "fire")) return true;
        if (name.equals( "snow_layer") || name.equals( "carpet") || name.equals( "cake") || name.equals( "flower_pot")) return true;
        if (name.equals( "sign") || name.equals( "wall_sign") || name.equals( "standing_banner") || name.equals( "wall_banner")) return true;
        if (name.equals( "lever") || name.equals( "stone_button") || name.equals( "wooden_button")) return true;
        if (name.equals( "stone_pressure_plate") || name.equals( "wooden_pressure_plate") || name.equals( "light_weighted_pressure_plate") || name.equals( "heavy_weighted_pressure_plate")) return true;
        if (name.equals( "portal") || name.equals( "end_portal") || name.equals( "end_gateway")) return true;
        if (name.equals( "rail") || name.equals( "golden_rail") || name.equals( "activator_rail") || name.equals( "detector_rail")) return true;
        if (name.equals( "reeds") || name.equals( "wheat") || name.equals( "carrots") || name.equals( "potatoes") || name.equals( "beetroots") || name.equals( "nether_wart")) return true;
        if (name.equals( "melon_stem") || name.equals( "pumpkin_stem") || name.equals( "tripwire") || name.equals( "tripwire_hook")) return true;
        return false;
    }

    double[] getAnglesToTarget(RavenVec3 eye, RavenVec3 targetCenter) {
        double dx = targetCenter.x - eye.x;
        double dz = targetCenter.z - eye.z;
        double dy = targetCenter.y - eye.y;
        double h = Math.sqrt(dx * dx + dz * dz);
        double yaw = Math.toDegrees(Math.atan2(dz, dx)) - 90;
        double pitch = -Math.toDegrees(Math.atan2(dy, h));
        return new double[]{yaw, pitch};
    }

    RavenVec3 raycastBlock(RavenVec3 eye, float yaw, float pitch, double maxDist) {
        double radYaw = Math.toRadians(yaw);
        double radPitch = Math.toRadians(pitch);
        double dx = -Math.sin(radYaw) * Math.cos(radPitch);
        double dy = -Math.sin(radPitch);
        double dz = Math.cos(radYaw) * Math.cos(radPitch);
        for (double d = 0.5; d <= maxDist; d += 0.25) {
            int bx = (int) Math.floor(eye.x + dx * d);
            int by = (int) Math.floor(eye.y + dy * d);
            int bz = (int) Math.floor(eye.z + dz * d);
            String bn = world.getBlockAt(bx, by, bz).name;
            if (!isPassableBlock(bn)) return new RavenVec3(bx + 0.5, by + 0.5, bz + 0.5);
        }
        return null;
    }

    String blockKey(RavenVec3 v) {
        return ((int) Math.floor(v.x)) + "," + ((int) Math.floor(v.y)) + "," + ((int) Math.floor(v.z));
    }

    Object[] findMiningEntry(Map<String, Object> anticheatPlayer, RavenVec3 blockPos) {
        List<Object[]> miningCache = (List<Object[]>) anticheatPlayer.get("miningCache");
        if (miningCache == null) return null;
        String key = blockKey(blockPos);
        for (Object[] e : miningCache) {
            if (((String) e[0]).equals(key)) return e;
        }
        return null;
    }

    double hardnessOf(String block) {
        if (block.equals("wool")) return 0.8;
        if (block.equals("planks") || block.equals("log") || block.equals("log2") || block.equals("fence") || block.equals("fence_gate") || block.equals("wooden_door") || block.equals("wooden_slab") || block.equals("double_wooden_slab") || block.equals("wooden_stairs")) return 2.0;
        if (block.equals("bookshelf")) return 1.5;
        if (block.equals("chest") || block.equals("trapped_chest") || block.equals("crafting_table")) return 2.5;
        if (block.equals("ladder")) return 0.4;
        if (block.equals("trapdoor")) return 3.0;
        if (block.equals("sign") || block.equals("wall_sign")) return 1.0;
        if (block.equals("bed")) return 0.2;
        if (block.equals("piston") || block.equals("sticky_piston") || block.equals("piston_extension")) return 0.5;
        if (block.equals("pumpkin") || block.equals("lit_pumpkin")) return 1.0;
        if (block.equals("stone") || block.equals("stone_bricks") || block.equals("mossy_cobblestone") || block.equals("granite") || block.equals("diorite") || block.equals("andesite") || block.equals("cobblestone_wall")) return 1.5;
        if (block.equals("cobblestone")) return 2.0;
        if (block.equals("brick_block") || block.equals("stonebrick")) return 2.0;
        if (block.equals("sandstone") || block.equals("red_sandstone")) return 0.8;
        if (block.equals("end_stone")) return 3.0;
        if (block.equals("obsidian")) return 50.0;
        if (block.equals("netherrack")) return 0.4;
        if (block.equals("glowstone")) return 0.3;
        if (block.equals("coal_ore") || block.equals("iron_ore") || block.equals("gold_ore") || block.equals("diamond_ore") || block.equals("emerald_ore") || block.equals("redstone_ore") || block.equals("lit_redstone_ore") || block.equals("lapis_ore") || block.equals("quartz_ore")) return 3.0;
        if (block.equals("quartz_block") || block.equals("sea_lantern")) return 0.8;
        if (block.equals("hardened_clay") || block.equals("stained_hardened_clay")) return 1.25;
        if (block.equals("dirt") || block.equals("sand") || block.equals("farmland") || block.equals("soul_sand") || block.equals("ice") || block.equals("packed_ice")) return 0.5;
        if (block.equals("gravel") || block.equals("clay") || block.equals("grass") || block.equals("mycelium") || block.equals("sponge")) return 0.6;
        if (block.equals("snow") || block.equals("snow_layer")) return 0.1;
        if (block.equals("glass") || block.equals("stained_glass") || block.equals("glass_pane") || block.equals("stained_glass_pane")) return 0.3;
        if (block.equals("leaves") || block.equals("leaves2")) return 0.2;
        if (block.equals("cactus") || block.equals("web")) return 4.0;
        return -1.0;
    }

    boolean isProperTool(String block, String item) {
        if (item == null) return false;
        boolean pick = item.endsWith("_pickaxe");
        boolean axe = item.endsWith("_axe");
        boolean shovel = item.endsWith("_shovel");
        boolean shears = item.equals("shears");
        if (pick && (block.equals("stone") || block.equals("cobblestone") || block.equals("stone_bricks") || block.equals("stonebrick") || block.equals("mossy_cobblestone") || block.equals("sandstone") || block.equals("red_sandstone") || block.equals("end_stone") || block.equals("obsidian") || block.equals("brick_block") || block.equals("netherrack") || block.equals("coal_ore") || block.equals("iron_ore") || block.equals("gold_ore") || block.equals("diamond_ore") || block.equals("emerald_ore") || block.equals("redstone_ore") || block.equals("lit_redstone_ore") || block.equals("lapis_ore") || block.equals("quartz_ore") || block.equals("quartz_block") || block.equals("granite") || block.equals("diorite") || block.equals("andesite") || block.equals("hardened_clay") || block.equals("stained_hardened_clay") || block.equals("sea_lantern") || block.equals("ice") || block.equals("packed_ice") || block.equals("cobblestone_wall"))) return true;
        if (axe && (block.equals("planks") || block.equals("log") || block.equals("log2") || block.equals("bookshelf") || block.equals("chest") || block.equals("trapped_chest") || block.equals("crafting_table") || block.equals("fence") || block.equals("fence_gate") || block.equals("wooden_door") || block.equals("wooden_slab") || block.equals("double_wooden_slab") || block.equals("wooden_stairs") || block.equals("ladder") || block.equals("trapdoor") || block.equals("pumpkin") || block.equals("lit_pumpkin"))) return true;
        if (shovel && (block.equals("dirt") || block.equals("sand") || block.equals("gravel") || block.equals("clay") || block.equals("grass") || block.equals("farmland") || block.equals("mycelium") || block.equals("snow") || block.equals("snow_layer") || block.equals("soul_sand"))) return true;
        if (shears && (block.equals("wool") || block.equals("leaves") || block.equals("leaves2") || block.equals("web"))) return true;
        return false;
    }

    int baseToolSpeed(String item) {
        if (item == null) return 1;
        int material = 1;
        if (item.startsWith("wooden_")) material = 2;
        else if (item.startsWith("stone_")) material = 4;
        else if (item.startsWith("iron_")) material = 6;
        else if (item.startsWith("diamond_")) material = 8;
        else if (item.startsWith("netherite_")) material = 9;
        else if (item.startsWith("golden_")) material = 12;
        if (item.equals("shears")) return 5;
        if (item.endsWith("_pickaxe") || item.endsWith("_axe") || item.endsWith("_shovel") || item.endsWith("_hoe")) return material;
        if (item.endsWith("_sword")) return 1;
        return 1;
    }

    int efficiencyLevel(RavenItem item) {
        if (item == null) return 0;
        List<Object[]> enchants = item.getEnchantments();
        if (enchants == null) return 0;
        for (Object[] e : enchants) {
            if (e != null && e.length >= 2 && "efficiency".equals(e[0])) return (Integer) e[1];
        }
        return 0;
    }

    double expectedMineMs(String block, String item, int eff, boolean haste) {
        double hardness = hardnessOf(block);
        if (hardness < 0) return -1.0;
        double speed = 1.0;
        double seconds;
        if (isProperTool(block, item)) {
            speed = baseToolSpeed(item) + eff * eff + 1;
            seconds = hardness * 1.5 / speed;
        } else {
            seconds = hardness * 5.0;
        }
        if (haste) seconds /= 1.4;
        double ticks = Math.ceil(seconds * 20);
        if (ticks < 1) ticks = 1;
        return ticks * 50.0;
    }

    void NoSlowA(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "NoSlowA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "NoSlowA_LastAlert", 0L);
        boolean isSprinting = Boolean.TRUE.equals(anticheatPlayer.get( "sprinting"));
        boolean isUsingItem = Boolean.TRUE.equals(anticheatPlayer.get( "using"));
        boolean isRiding = anticheatPlayer.get( "riding") != null;
        int ticks = (int) anticheatPlayer.get( "ticksExisted");
        int lastStartUsing = (int) anticheatPlayer.getOrDefault( "lastUsingTick", 0);
        int lastItemSwap = (int) anticheatPlayer.getOrDefault( "lastItemChangeTick", 0);
        // Grim NoSlow (offset, 2-tick confirm): while using an item on the ground the
        // 1.8.9 use-slow caps horizontal speed at ~0.07 blocks/tick (0.2 multiplier).
        // Sustained faster movement while using = NoSlow. Speed potions scale the
        // legit cap. The usingTicks/usingDist accumulator only counts use+ground ticks.
        double usingDist = (Double) anticheatPlayer.getOrDefault( "usingDist", 0d);
        int usingTicks = (int) anticheatPlayer.getOrDefault( "usingTicks", 0);
        double slowCap = 0.11;
        if (usingTicks >= 10) {
            List<Object[]> effects = (List<Object[]>) anticheatPlayer.get("potionEffects");
            if (effects != null) {
                for (Object[] effect : effects) {
                    if ((Integer) effect[0] == 1) slowCap *= 1.0 + ((Integer) effect[2] + 1) * 0.2;
                }
            }
            if (usingDist / usingTicks > slowCap) {
                vl++;
                if (vl >= vlThreshold && now - lastAlert > cooldown) {
                    anticheatPlayer.put("NoSlowA_LastAlert", now);
                    printFlag(anticheatPlayer, "NCP NoSlow", vl);
                }
                anticheatPlayer.put("NoSlowA_VL", vl);
                return;
            }
        }
        if (isUsingItem && isSprinting && !isRiding && lastStartUsing - lastItemSwap > 1) {
            boolean isSameItem = true;
            int ticksNotUsing = (int) anticheatPlayer.getOrDefault("lastUsingTick", 0) - (int) anticheatPlayer.getOrDefault("lastStopUsingTick", 0);
            if (ticksNotUsing <= 7) {
                RavenItem heldItem = (RavenItem) anticheatPlayer.get("heldItem");
                RavenItem lastStopUsingItem = (RavenItem) anticheatPlayer.get("lastStopUsingItem");
                String heldItemKey = heldItem != null ? heldItem.name + ":" + heldItem.meta : null;
                String lastStopUsingItemKey = lastStopUsingItem != null ? lastStopUsingItem.name + ":" + lastStopUsingItem.meta : null;
                isSameItem = heldItemKey != null && heldItemKey.equals(lastStopUsingItemKey);
            }
            if (ticksNotUsing > 7 || !isSameItem) {
                vl++;
                if (vl >= vlThreshold && now - lastAlert > cooldown) {
                    anticheatPlayer.put("NoSlowA_LastAlert", now);
                    printFlag(anticheatPlayer, "NCP NoSlow", vl);
                }
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("NoSlowA_VL", vl);
    }

    void AutoBlockA(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "AutoBlockA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "AutoBlockA_LastAlert", 0L);
        RavenItem heldItem = (RavenItem) anticheatPlayer.get( "heldItem");
        List <Integer > blockSwings = (List <Integer >) anticheatPlayer.get( "blockSwingTimes");
        // Vulcan AutoBlock: a swing that STARTS while the sword is blocking. The
        // old swingProgress!=0 test false-flagged legit swing-then-block because
        // the swing animation decays over several ticks. A swing starting while
        // using an item is impossible in vanilla, so only real overlaps count.
        if (Boolean.TRUE.equals(anticheatPlayer.get("blocking")) && blockSwings != null && blockSwings.size() >= 2) {
            vl++;
            if (vl  >= vlThreshold  && now - lastAlert  > cooldown) {
                anticheatPlayer.put( "AutoBlockA_LastAlert", now);
                printFlag(anticheatPlayer,  "Vulcan AutoBlock", vl);
            }
        } else {
            vl = Math.max(vl - 5, 0);
        }
        anticheatPlayer.put( "AutoBlockA_VL", vl);
    }

    void SprintA(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "SprintA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "SprintA_LastAlert", 0L);
        boolean isSprinting = Boolean.TRUE.equals(anticheatPlayer.get( "sprinting"));
        boolean isGroundCollision = Boolean.TRUE.equals(anticheatPlayer.get( "onGround"));
        boolean isRiding = anticheatPlayer.get( "riding") != null;
        int hurtTime = (int) anticheatPlayer.get( "hurtTime");
        int lastHit = (int) anticheatPlayer.getOrDefault( "lastHitTick", 0);
        int ticksExisted = (int) anticheatPlayer.get( "ticksExisted");
        float moveYaw = (float) anticheatPlayer.get( "moveYaw");
        RavenVec3 current = (RavenVec3) anticheatPlayer.get( "position");
        RavenVec3 previous = (RavenVec3) anticheatPlayer.get( "lastPosition");
        double speed = Math.max(Math.abs(current.x - previous.x), Math.abs(current.z - previous.z));
        if (!isRiding && isSprinting && isGroundCollision && hurtTime <= 0 && (lastHit <= 0 || ticksExisted - lastHit > 10) && Math.abs(moveYaw) > 90 && speed >= 0.2) {
            vl++;
            if (vl >= vlThreshold && now - lastAlert > cooldown) {
                anticheatPlayer.put("SprintA_LastAlert", now);
                printFlag(anticheatPlayer, "Grim Sprint", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("SprintA_VL", vl);
    }

    void VelocityA(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "VelocityA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "VelocityA_LastAlert", 0L);
        int hurtTime = (int) anticheatPlayer.get( "hurtTime");
        int maxHurtTime = (int) anticheatPlayer.get( "maxHurtTime");
        int ticksExisted = (int) anticheatPlayer.get( "ticksExisted");
        int startFall = (int) anticheatPlayer.getOrDefault( "lastStartFallingTick", 0);
        int stopFall = (int) anticheatPlayer.getOrDefault( "lastStopFallingTick", 0);
        int ticksSinceFall = ticksExisted - stopFall;
        int fallTicks = Math.max(0, stopFall - startFall);
        boolean burning = Boolean.TRUE.equals(anticheatPlayer.get( "burning"));
        RavenVec3 position = (RavenVec3) anticheatPlayer.get( "position");
        RavenVec3 lastPosition = (RavenVec3) anticheatPlayer.get( "lastPosition");
        boolean recentFall = fallTicks  >= 6  && ticksSinceFall  <= 6;
        double deltaXZ = Math.sqrt(Math.pow(position.x - lastPosition.x, 2) + Math.pow(position.z - lastPosition.z, 2));
        if (!burning && hurtTime > 0 && hurtTime < maxHurtTime && !recentFall && Math.abs(deltaXZ) < 1e-8) {
            boolean isCollided = checkSurroundingBlocks(position);
            if (!isCollided) {
                vl++;
                if (vl >= vlThreshold && now - lastAlert > cooldown) {
                    anticheatPlayer.put("VelocityA_LastAlert", now);
                    printFlag(anticheatPlayer, "NCP Velocity", vl);
                }
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("VelocityA_VL", vl);
    }

    void RotationA(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "RotationA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "RotationA_LastAlert", 0L);
        float pitch = (float) anticheatPlayer.get( "pitch");
        if (Math.abs(pitch)  > 90) {
            List <String > scoreboard = world.getScoreboard();
            if (scoreboard != null && !scoreboard.isEmpty()) {
                String header = RavenText.strip(scoreboard.get(0));
                if ( "ATLAS".equals(header) ||  "REPLAY".equals(header)) return;
            }
            vl++;
            if (vl  >= vlThreshold  && now - lastAlert  > cooldown) {
                anticheatPlayer.put( "RotationA_LastAlert", now);
                printFlag(anticheatPlayer,  "Grim Rotation", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put( "RotationA_VL", vl);
    }

    void ScaffoldA(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "ScaffoldA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "ScaffoldA_LastAlert", 0L);
        int lastStopCrouch = (int) anticheatPlayer.getOrDefault( "lastStopCrouchingTick", 0);
        int ticksExisted = (int) anticheatPlayer.get( "ticksExisted");
        int lastSwing = (int) anticheatPlayer.getOrDefault( "lastSwingTick", 0);
        int lastStartCrouch = (int) anticheatPlayer.getOrDefault( "lastCrouchedTick", 0);
        boolean onGround = Boolean.TRUE.equals(anticheatPlayer.get( "onGround"));
        float pitch = (float) anticheatPlayer.get( "pitch");
        RavenItem lastSwingItem = (RavenItem) anticheatPlayer.get( "lastSwingItem");
        boolean holdingBlocks = lastSwingItem == null ? false : lastSwingItem.type.startsWith( "Block");
        boolean lookingDown = pitch  >= 70f;
        if (lookingDown && holdingBlocks && lastSwing == ticksExisted && lastStopCrouch >= ticksExisted - 1 && lastStopCrouch - lastStartCrouch <= 2) {
            boolean grounded = onGround;
            List<String> scoreboard = world.getScoreboard();
            if (!grounded && scoreboard != null && !scoreboard.isEmpty()) {
                String header = RavenText.strip(scoreboard.get(0));
                if ("ATLAS".equals(header) ||"REPLAY".equals(header)) {
                    @SuppressWarnings("unchecked")
                    List<RavenVec3> ps = (List<RavenVec3>) anticheatPlayer.get("previousPositions");
                    if (ps != null && ps.size() > 1) {
                        int sz = ps.size(), n = Math.min(sz - 1, 10);
                        double sum = 0;
                        for (int i = sz - n; i < sz; i++) sum += Math.abs(ps.get(i).y - ps.get(i - 1).y);
                        grounded = (sum / n) <= 0.2;
                    }
                }
            }
            if (grounded) {
                vl++;
                if (vl >= vlThreshold && now - lastAlert > cooldown) {
                    anticheatPlayer.put("ScaffoldA_LastAlert", now);
                    printFlag(anticheatPlayer, "NCP Scaffold", vl);
                }
            }
        } else if (!lookingDown || !holdingBlocks || ticksExisted - lastStopCrouch > 20 || ticksExisted - lastSwing > 20 || (onGround && lastSwing == ticksExisted && lastStopCrouch < ticksExisted - 1)) {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("ScaffoldA_VL", vl);
    }

    void ScaffoldC(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "ScaffoldC_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "ScaffoldC_LastAlert", 0L);
        int lastStartCrouch = (int) anticheatPlayer.getOrDefault( "lastCrouchedTick", 0);
        int lastStopCrouch = (int) anticheatPlayer.getOrDefault( "lastStopCrouchingTick", 0);
        int ticksExisted = (int) anticheatPlayer.get( "ticksExisted");
        int lastSwing = (int) anticheatPlayer.getOrDefault( "lastSwingTick", 0);
        float moveYaw = (float) anticheatPlayer.get( "moveYaw");
        float pitch = (float) anticheatPlayer.get( "pitch");
        RavenItem lastSwingItem = (RavenItem) anticheatPlayer.get( "lastSwingItem");
        List <RavenVec3 > previousPositions = (List <RavenVec3 >) anticheatPlayer.get( "previousPositions");
        boolean holdingBlocks = lastSwingItem != null  && lastSwingItem.type.startsWith( "Block");
        boolean lookingDown = pitch  >= 70;
        if (lookingDown && holdingBlocks && ticksExisted - lastSwing <= 10 && lastStopCrouch >= lastStartCrouch && ticksExisted - lastStopCrouch > 30 && Math.abs(moveYaw) >= 90 && previousPositions.size() >= 20) {
            double speed = 0;
            for (int i = 0; i < previousPositions.size() - 1; i++) {
                RavenVec3 current = previousPositions.get(i);
                RavenVec3 previous = previousPositions.get(i + 1);
                speed += Math.max(Math.abs(current.x - previous.x), Math.abs(current.z - previous.z));
            }
            double avgSpeed = speed / (previousPositions.size() - 1);
            RavenVec3 position = (RavenVec3) anticheatPlayer.get("position");
            RavenVec3 lastPosition = (RavenVec3) anticheatPlayer.get("lastPosition");
            double direction = Math.toRadians(Math.toDegrees(Math.atan2(position.z - lastPosition.z, position.x - lastPosition.x)) - 90);
            int baseY = (int) Math.floor(position.y);
            for (int i = 0; i < 3; i++) {
                String blockBelow = world.getBlockAt((int) Math.floor(lastPosition.x), baseY - i, (int) Math.floor(lastPosition.z)).name;
                if (!blockBelow.equals("air")) {
                    baseY -= i;
                    break;
                }
            }
            boolean onGround = Boolean.TRUE.equals(anticheatPlayer.get("onGround"));
            double totalDistance = previousPositions.get(previousPositions.size() - 1).distanceTo(previousPositions.get(0));
            String blockAhead = world.getBlockAt((int) Math.floor(position.x + (2 * Math.cos(direction))), baseY, (int) Math.floor(position.z + (2 * Math.sin(direction)))).name;
            boolean overAir = blockAhead.equals("air");
            String standingBlock = world.getBlockAt((int) Math.floor(lastPosition.x), baseY, (int) Math.floor(lastPosition.z)).name;
            boolean matchingBlock = standingBlock.equals(lastSwingItem.name);
            boolean unsupported = true;
            if (onGround) {
                String block1Below = world.getBlockAt((int) Math.floor(lastPosition.x), baseY - 1, (int) Math.floor(lastPosition.z)).name;
                String block2Below = world.getBlockAt((int) Math.floor(lastPosition.x), baseY - 2, (int) Math.floor(lastPosition.z)).name;
                unsupported = block1Below.equals("air") && block2Below.equals("air");
            }
            if (avgSpeed >= 0.14 && totalDistance > 3.4 && overAir && matchingBlock && unsupported) {
                vl++;
                if (vl >= vlThreshold && now - lastAlert > cooldown) {
                    anticheatPlayer.put("ScaffoldC_LastAlert", now);
                    printFlag(anticheatPlayer, "Vulcan Scaffold", vl);
                }
            } else {
                vl = Math.max(vl - 1, 0);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("ScaffoldC_VL", vl);
    }

    boolean isAirborne(Map<String, Object> anticheatPlayer, RavenVec3 position) {
        if (Boolean.TRUE.equals(anticheatPlayer.get("onGround"))) return false;
        String feet = world.getBlockAt((int) Math.floor(position.x), (int) Math.floor(position.y), (int) Math.floor(position.z)).name;
        String below = world.getBlockAt((int) Math.floor(position.x), (int) Math.floor(position.y) - 1, (int) Math.floor(position.z)).name;
        return feet.equals("air") && below.equals("air");
    }

    void SpeedA(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "SpeedA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "SpeedA_LastAlert", 0L);
        boolean onGround = Boolean.TRUE.equals(anticheatPlayer.get( "onGround"));
        boolean inWater = Boolean.TRUE.equals(anticheatPlayer.get( "inWater"));
        boolean inLava = Boolean.TRUE.equals(anticheatPlayer.get( "inLava"));
        boolean riding = anticheatPlayer.get( "riding") != null;
        boolean creative = Boolean.TRUE.equals(anticheatPlayer.get( "creative"));
        boolean sprinting = Boolean.TRUE.equals(anticheatPlayer.get( "sprinting"));
        int hurtTime = (int) anticheatPlayer.get( "hurtTime");
        int lastHit = (int) anticheatPlayer.getOrDefault( "lastHitTick", 0);
        int ticksExisted = (int) anticheatPlayer.get( "ticksExisted");
        List <RavenVec3 > positions = (List <RavenVec3 >) anticheatPlayer.get( "previousPositions");
        if (!onGround || inWater || inLava || riding || creative || hurtTime > 0 || (lastHit > 0 && ticksExisted - lastHit <= 20) || positions == null || positions.size() < 20) {
            anticheatPlayer.put("SpeedA_VL", Math.max(vl - 1, 0));
            return;
        }
        double distance = 0;
        for (int i = 0; i < positions.size() - 1; i++) {
            RavenVec3 current = positions.get(i);
            RavenVec3 previous = positions.get(i + 1);
            distance += Math.sqrt(Math.pow(current.x - previous.x, 2) + Math.pow(current.z - previous.z, 2));
        }
        double cap = sprinting ? 5.612 : 4.317;
        List<Object[]> effects = (List<Object[]>) anticheatPlayer.get("potionEffects");
        boolean slowed = false;
        if (effects != null) {
            for (Object[] effect : effects) {
                int id = (Integer) effect[0];
                if (id == 1) cap *= 1.0 + ((Integer) effect[2] + 1) * 0.2;
                else if (id == 2) slowed = true;
            }
        }
        if (!slowed && distance > Math.max(cap * 1.25, 5.5)) {
            vl++;
            if (vl >= vlThreshold && now - lastAlert > cooldown) {
                anticheatPlayer.put("SpeedA_LastAlert", now);
                printFlag(anticheatPlayer, "Grim Speed", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("SpeedA_VL", vl);
    }

    void FlyA(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "FlyA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "FlyA_LastAlert", 0L);
        boolean inWater = Boolean.TRUE.equals(anticheatPlayer.get( "inWater"));
        boolean inLava = Boolean.TRUE.equals(anticheatPlayer.get( "inLava"));
        boolean onLadder = Boolean.TRUE.equals(anticheatPlayer.get( "onLadder"));
        boolean riding = anticheatPlayer.get( "riding") != null;
        boolean creative = Boolean.TRUE.equals(anticheatPlayer.get( "creative"));
        RavenVec3 position = (RavenVec3) anticheatPlayer.get( "position");
        RavenVec3 lastPosition = (RavenVec3) anticheatPlayer.get( "lastPosition");
        double deltaY = position.y - lastPosition.y;
        int airborneTicks = (int) anticheatPlayer.getOrDefault( "FlyA_AirborneTicks", 0);
        int ascentTicks = (int) anticheatPlayer.getOrDefault( "FlyA_AscentTicks", 0);
        if (isAirborne(anticheatPlayer, position) && !inWater && !inLava && !onLadder && !riding && !creative) {
            airborneTicks++;
            if (deltaY > 0.11) ascentTicks++;
            else ascentTicks = 0;
            boolean flag = false;
            if (ascentTicks >= 14) flag = true;
            else if (airborneTicks >= 30) {
                List<RavenVec3> ps = (List<RavenVec3>) anticheatPlayer.get("previousPositions");
                if (ps != null && ps.size() >= 10 && Math.abs(ps.get(0).y - ps.get(9).y) < 0.05) flag = true;
            }
            if (flag) {
                vl++;
                if (vl >= vlThreshold && now - lastAlert > cooldown) {
                    anticheatPlayer.put("FlyA_LastAlert", now);
                    printFlag(anticheatPlayer, "NCP Fly", vl);
                }
            }
        } else {
            airborneTicks = 0;
            ascentTicks = 0;
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("FlyA_AirborneTicks", airborneTicks);
        anticheatPlayer.put("FlyA_AscentTicks", ascentTicks);
        anticheatPlayer.put("FlyA_VL", vl);
    }

    void GlideA(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "GlideA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "GlideA_LastAlert", 0L);
        boolean inWater = Boolean.TRUE.equals(anticheatPlayer.get( "inWater"));
        boolean inLava = Boolean.TRUE.equals(anticheatPlayer.get( "inLava"));
        boolean onLadder = Boolean.TRUE.equals(anticheatPlayer.get( "onLadder"));
        boolean riding = anticheatPlayer.get( "riding") != null;
        boolean creative = Boolean.TRUE.equals(anticheatPlayer.get( "creative"));
        RavenVec3 position = (RavenVec3) anticheatPlayer.get( "position");
        int airborneTicks = (int) anticheatPlayer.getOrDefault( "GlideA_AirborneTicks", 0);
        int hurtTime = (int) anticheatPlayer.get( "hurtTime");
        if (isAirborne(anticheatPlayer, position) && !inWater && !inLava && !onLadder && !riding && !creative) {
            airborneTicks++;
            List<RavenVec3> ps = (List<RavenVec3>) anticheatPlayer.get("previousPositions");
            if (airborneTicks >= 8 && ps != null && ps.size() >= 2) {
                // NCP vertical physics: while falling, dy follows the gravity
                // recurrence dy' = dy * 0.91 - 0.08 every tick. Falling slower than
                // the predicted curve = glide; 1.8.9 has no slow-falling potion to
                // exempt, and knockback/jump phases are gated out above.
                double dy = ps.get(0).y - ps.get(1).y;
                double lastDy = (double) anticheatPlayer.getOrDefault( "GlideA_LastDy", 0d);
                if (dy >= 0 || lastDy >= 0 || hurtTime > 0) {
                    anticheatPlayer.put( "GlideA_LastDy", dy);
                    anticheatPlayer.put( "GlideA_SlowTicks", 0);
                } else {
                    double predicted = lastDy * 0.91 - 0.08;
                    String feet = world.getBlockAt((int) Math.floor(position.x), (int) Math.floor(position.y), (int) Math.floor(position.z)).name;
                    String below = world.getBlockAt((int) Math.floor(position.x), (int) Math.floor(position.y) - 1, (int) Math.floor(position.z)).name;
                    boolean stuck = feet.equals("web") || below.equals("web") || feet.equals("vine") || below.equals("vine");
                    int slowTicks = (int) anticheatPlayer.getOrDefault( "GlideA_SlowTicks", 0);
                    if (stuck) {
                        slowTicks = 0;
                    } else if (dy - predicted > 0.08) {
                        slowTicks++;
                    } else {
                        slowTicks = Math.max(slowTicks - 1, 0);
                    }
                    anticheatPlayer.put( "GlideA_LastDy", dy);
                    anticheatPlayer.put( "GlideA_SlowTicks", slowTicks);
                    if (slowTicks >= 20) {
                        anticheatPlayer.put( "GlideA_SlowTicks", 0);
                        vl++;
                        if (vl >= vlThreshold && now - lastAlert > cooldown) {
                            anticheatPlayer.put("GlideA_LastAlert", now);
                            printFlag(anticheatPlayer, "Vulcan Glide", vl);
                        }
                    }
                }
            }
        } else {
            airborneTicks = 0;
            vl = Math.max(vl - 1, 0);
            anticheatPlayer.put( "GlideA_LastDy", 0d);
            anticheatPlayer.put( "GlideA_SlowTicks", 0);
        }
        anticheatPlayer.put("GlideA_AirborneTicks", airborneTicks);
        anticheatPlayer.put("GlideA_VL", vl);
    }

    void NoFallA(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "NoFallA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "NoFallA_LastAlert", 0L);
        int ticksExisted = (int) anticheatPlayer.get( "ticksExisted");
        boolean creative = Boolean.TRUE.equals(anticheatPlayer.get( "creative"));
        boolean inWater = Boolean.TRUE.equals(anticheatPlayer.get( "inWater"));
        boolean inLava = Boolean.TRUE.equals(anticheatPlayer.get( "inLava"));
        boolean onLadder = Boolean.TRUE.equals(anticheatPlayer.get( "onLadder"));
        boolean riding = anticheatPlayer.get( "riding") != null;
        int hurtTime = (int) anticheatPlayer.get( "hurtTime");
        float health = (Float) anticheatPlayer.get( "health");
        float absorption = (Float) anticheatPlayer.getOrDefault( "absorption", 0f);
        RavenVec3 position = (RavenVec3) anticheatPlayer.get( "position");
        boolean airborne = isAirborne(anticheatPlayer, position);
        int fallStartTick = (int) anticheatPlayer.getOrDefault( "NoFallA_FallStartTick", 0);
        if (airborne && !creative && !riding) {
            if (fallStartTick == 0) {
                anticheatPlayer.put("NoFallA_FallStartTick", ticksExisted);
                anticheatPlayer.put("NoFallA_FallStartY", position.y);
                anticheatPlayer.put("NoFallA_HealthAtFallStart", health);
                anticheatPlayer.put("NoFallA_AbsorptionAtFallStart", absorption);
                anticheatPlayer.put("NoFallA_HurtDuringFall", false);
            } else if (hurtTime > 0) {
                anticheatPlayer.put("NoFallA_HurtDuringFall", true);
            }
        } else if (fallStartTick > 0) {
            double startY = (Double) anticheatPlayer.get("NoFallA_FallStartY");
            double distance = startY - position.y;
            float healthAtFallStart = (Float) anticheatPlayer.get("NoFallA_HealthAtFallStart");
            float absorptionAtFallStart = (Float) anticheatPlayer.get("NoFallA_AbsorptionAtFallStart");
            boolean hurtDuringFall = Boolean.TRUE.equals(anticheatPlayer.get("NoFallA_HurtDuringFall"));
            anticheatPlayer.put("NoFallA_FallStartTick", 0);
            if (distance >= 5.0 && !creative && !hurtDuringFall && !inWater && !inLava && !onLadder && !riding && absorptionAtFallStart <= 0f) {
                String feet = world.getBlockAt((int) Math.floor(position.x), (int) Math.floor(position.y), (int) Math.floor(position.z)).name;
                String below = world.getBlockAt((int) Math.floor(position.x), (int) Math.floor(position.y) - 1, (int) Math.floor(position.z)).name;
                boolean safeLanding = feet.equals("web") || below.equals("web") || feet.equals("vine") || below.equals("vine") || below.equals("slime");
                if (!safeLanding) {
                    anticheatPlayer.put("NoFallA_LandedTick", ticksExisted);
                }
            }
        }
        int landedTick = (int) anticheatPlayer.getOrDefault("NoFallA_LandedTick", 0);
        int checkedTick = (int) anticheatPlayer.getOrDefault("NoFallA_CheckedTick", 0);
        if (landedTick > 0 && checkedTick < landedTick && ticksExisted - landedTick >= 2) {
            anticheatPlayer.put("NoFallA_CheckedTick", landedTick);
            float healthAtFallStart = (Float) anticheatPlayer.getOrDefault("NoFallA_HealthAtFallStart", 0f);
            if (healthAtFallStart > 0 && health >= healthAtFallStart) {
                vl++;
                if (vl >= vlThreshold && now - lastAlert > cooldown) {
                    anticheatPlayer.put("NoFallA_LastAlert", now);
                    printFlag(anticheatPlayer, "Grim NoFall", vl);
                }
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("NoFallA_VL", vl);
    }

    void ReachA(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "ReachA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "ReachA_LastAlert", 0L);
        int lastHit = (int) anticheatPlayer.getOrDefault( "lastHitTick", 0);
        int ticksExisted = (int) anticheatPlayer.get( "ticksExisted");
        if (lastHit != ticksExisted) {
            anticheatPlayer.put( "ReachA_VL", Math.max(vl - 1, 0));
            return;
        }
        boolean burning = Boolean.TRUE.equals(anticheatPlayer.get( "burning"));
        boolean inWater = Boolean.TRUE.equals(anticheatPlayer.get( "inWater"));
        boolean inLava = Boolean.TRUE.equals(anticheatPlayer.get( "inLava"));
        boolean riding = anticheatPlayer.get( "riding") != null;
        if (burning || inWater || inLava || riding) {
            anticheatPlayer.put( "ReachA_VL", Math.max(vl - 1, 0));
            return;
        }
        RavenPlayer victim = (RavenPlayer) anticheatPlayer.get( "entity");
        RavenVec3 victimPos = (RavenVec3) anticheatPlayer.get( "position");
        Map <String, Object > attacker = null;
        double attackerDistance = 6.5;
        for (Map.Entry <RavenPlayer, Map <String, Object > > entry : anticheatPlayers.entrySet()) {
            if (entry.getKey() == victim) continue;
            Map <String, Object > other = entry.getValue();
            if (other == null || other == anticheatPlayer) continue;
            int lastSwing = (int) other.getOrDefault( "lastSwingTick", 0);
            if (ticksExisted - lastSwing  > 2) continue;
            List <Integer > swings = (List <Integer >) other.get( "swingTimes");
            if (swings == null || swings.size()  < 2) continue;
            // Negativity Reach IGNORED_TYPE: bow / fishing rod "hits" are projectile
            // hits - the victim is not in melee range of the attacker.
            RavenItem otherHeld = (RavenItem) other.get("heldItem");
            if (otherHeld != null && (otherHeld.type.contains("Bow") || otherHeld.type.contains("FishingRod"))) continue;
            // Aim context gate: only attribute attacks from players holding a melee
            // weapon and looking at a real player (not a bot).
            if (!isMeleeWeapon(otherHeld) || !isLookingAtPlayer(other)) continue;
            int recentSwings = 0;
            for (int tick : swings) {
                if (ticksExisted - tick  <= 5) recentSwings++;
            }
            if (recentSwings  < 2) continue;
            RavenVec3 otherPos = (RavenVec3) other.get( "position");
            if (otherPos == null) continue;
            double distance = victimPos.distanceTo(otherPos);
            if (distance  < attackerDistance) {
                attackerDistance = distance;
                attacker = other;
            }
        }
        if (attacker != null) {
            RavenVec3 attackerPos = (RavenVec3) attacker.get( "position");
            if (attackerPos != null) {
                // Grim Reach: raycast from the attacker's eye (standing + sneaking heights)
                // along the current and previous look direction against the victim's box
                // expanded by the pre-1.9 hitbox margin. No intercept = impossible hitbox
                // (flag as Hitbox), distance beyond maxReach = reach flag.
                float ayaw = (float) attacker.getOrDefault( "yaw", 0f);
                float apitch = (float) attacker.getOrDefault( "pitch", 0f);
                float preyaw = (float) attacker.getOrDefault( "previousYaw", 0f);
                double half = 0.3 + 0.1005;
                double minX = victimPos.x - half, maxX = victimPos.x + half;
                double minZ = victimPos.z - half, maxZ = victimPos.z + half;
                double minY = victimPos.y, maxY = victimPos.y + 1.8 + 0.1005;
                double maxReach = 3.0;
                double rayLen = maxReach + 3.0;
                double minDistance = Double.MAX_VALUE;
                boolean intercepted = false;
                double[] eyeHeights = new double[]{1.62, 1.54};
                double[][] looks = new double[][]{{ayaw, apitch}, {preyaw, apitch}};
                for (double eyeH : eyeHeights) {
                    RavenVec3 eye = attackerPos.offset(0, eyeH, 0);
                    for (double[] look : looks) {
                        double radYaw = Math.toRadians(look[0]);
                        double radPitch = Math.toRadians(look[1]);
                        double dx = -Math.sin(radYaw) * Math.cos(radPitch);
                        double dy = -Math.sin(radPitch);
                        double dz = Math.cos(radYaw) * Math.cos(radPitch);
                        double t = rayBoxDistance(eye, dx, dy, dz, rayLen, minX, minY, minZ, maxX, maxY, maxZ);
                        if (t >= 0) {
                            intercepted = true;
                            minDistance = Math.min(minDistance, t);
                        }
                    }
                }
                if (!intercepted) {
                    int hitboxVl = (int) attacker.getOrDefault( "ReachA_HitboxVL", 0);
                    long hitboxLastAlert = (long) attacker.getOrDefault( "ReachA_HitboxLastAlert", 0L);
                    hitboxVl++;
                    if (hitboxVl  >= vlThreshold  && now - hitboxLastAlert  > cooldown) {
                        attacker.put( "ReachA_HitboxLastAlert", now);
                        attacker.put("ReachA_HitboxFlagTime", now);
                        printFlag(attacker,  "Grim Hitbox", hitboxVl);
                    }
                    attacker.put( "ReachA_HitboxVL", hitboxVl);
                    vl = Math.max(vl - 1, 0);
                } else if (minDistance  > maxReach) {
                    // Matrix traceback: compare the hit against the victim's position up to 8
                    // ticks back. A hit explained by latency (the victim was within reach at
                    // some point during the traceback window) is not flagged.
                    List<RavenVec3> victimHistory = (List<RavenVec3>) anticheatPlayer.get("previousPositions");
                    double traceDist = Double.MAX_VALUE;
                    if (victimHistory != null) {
                        int limit = Math.min(victimHistory.size(), 8);
                        for (int i = 0; i < limit; i++) {
                            RavenVec3 h = victimHistory.get(i);
                            if (h != null) traceDist = Math.min(traceDist, attackerPos.distanceTo(h));
                        }
                    }
                    if (traceDist <= maxReach) {
                        vl = Math.max(vl - 1, 0);
                    } else {
                        // Matrix dynamic VL: (reach - max_reach) * 10, clamped 3..8. Deeper
                        // reach flags harder; matrix caps burst VL at 15 per 5 seconds.
                        double excess = Math.max(0, minDistance - maxReach);
                        int gain = (int) Math.round(Math.min(8.0, Math.max(3.0, excess * 10.0)));
                        int burst = (int) anticheatPlayer.getOrDefault( "ReachA_Burst", 0);
                        long burstStart = (long) anticheatPlayer.getOrDefault( "ReachA_BurstStart", 0L);
                        if (now - burstStart > 5000) {
                            burst = 0;
                            burstStart = now;
                        }
                        if (burst + gain > 15) gain = Math.max(0, 15 - burst);
                        burst += gain;
                        anticheatPlayer.put( "ReachA_Burst", burst);
                        anticheatPlayer.put( "ReachA_BurstStart", burstStart);
                        vl += gain;
                        attacker.put("ReachA_FlagTime", now);
                        if (vl  >= vlThreshold  && now - lastAlert  > cooldown) {
                            anticheatPlayer.put( "ReachA_LastAlert", now);
                            printFlag(attacker,  "Grim Reach", vl);
                        }
                    }
                } else {
                    vl = Math.max(vl - 1, 0);
                }
            } else {
                vl = Math.max(vl - 1, 0);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put( "ReachA_VL", vl);
    }

    void TeleportA(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "TeleportA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "TeleportA_LastAlert", 0L);
        boolean riding = anticheatPlayer.get( "riding") != null;
        RavenVec3 position = (RavenVec3) anticheatPlayer.get( "position");
        RavenVec3 lastPosition = (RavenVec3) anticheatPlayer.get( "lastPosition");
        double deltaXZ = Math.sqrt(Math.pow(position.x - lastPosition.x, 2) + Math.pow(position.z - lastPosition.z, 2));
        List <RavenVec3 > ps = (List <RavenVec3 >) anticheatPlayer.get( "previousPositions");
        int hurtTime = (int) anticheatPlayer.get( "hurtTime");
        int ticksExisted = (int) anticheatPlayer.get( "ticksExisted");
        int lastHit = (int) anticheatPlayer.getOrDefault( "lastHitTick", 0);
        if (!riding && hurtTime == 0 && (lastHit <= 0 || ticksExisted - lastHit > 3) && deltaXZ > 6.0 && ps != null && ps.size() >= 5) {
            vl++;
            if (vl >= vlThreshold && now - lastAlert > cooldown) {
                anticheatPlayer.put("TeleportA_LastAlert", now);
                printFlag(anticheatPlayer, "NCP Teleport", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("TeleportA_VL", vl);
    }

    void VelocityB(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "VelocityB_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "VelocityB_LastAlert", 0L);
        int lastHit = (int) anticheatPlayer.getOrDefault( "lastHitTick", 0);
        int ticksExisted = (int) anticheatPlayer.get( "ticksExisted");
        int ticksSinceHit = lastHit  > 0 ? ticksExisted - lastHit : 99;
        boolean onGround = Boolean.TRUE.equals(anticheatPlayer.get( "onGround"));
        boolean inWater = Boolean.TRUE.equals(anticheatPlayer.get( "inWater"));
        boolean inLava = Boolean.TRUE.equals(anticheatPlayer.get( "inLava"));
        boolean riding = anticheatPlayer.get( "riding") != null;
        boolean collided = Boolean.TRUE.equals(anticheatPlayer.get( "collided"));
        RavenVec3 position = (RavenVec3) anticheatPlayer.get( "position");
        RavenVec3 lastPosition = (RavenVec3) anticheatPlayer.get( "lastPosition");
        double deltaXZ = Math.sqrt(Math.pow(position.x - lastPosition.x, 2) + Math.pow(position.z - lastPosition.z, 2));
        if (ticksSinceHit >= 1 && ticksSinceHit <= 3 && !inWater && !inLava && !riding && !collided && onGround && deltaXZ < 0.08 && !checkSurroundingBlocks(position)) {
            vl++;
            if (vl >= vlThreshold && now - lastAlert > cooldown) {
                anticheatPlayer.put("VelocityB_LastAlert", now);
                printFlag(anticheatPlayer, "Verus Velocity", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("VelocityB_VL", vl);
    }

    void RotationB(Map<String, Object> anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault("RotationB_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault("RotationB_LastAlert", 0L);
        boolean riding = anticheatPlayer.get("riding") != null;
        float yaw = (float) anticheatPlayer.get("yaw");
        float prevYaw = (float) anticheatPlayer.get("previousYaw");
        double yawDelta = Math.abs(yaw - prevYaw);
        if (yawDelta > 180) yawDelta = 360 - yawDelta;
        int ticksExisted = (int) anticheatPlayer.get("ticksExisted");
        // Negativity NoPitchLimit: the client pitch is clamped to [-90, 90]; values
        // beyond that are only possible with a cheat that writes raw rotation values.
        float pitch = (float) anticheatPlayer.getOrDefault("pitch", 0f);
        if (Math.abs(pitch) > 90) {
            vl++;
            if (vl >= vlThreshold && now - lastAlert > cooldown) {
                anticheatPlayer.put("RotationB_LastAlert", now);
                printFlag(anticheatPlayer, "Vulcan Rotation", vl);
            }
            anticheatPlayer.put("RotationB_VL", vl);
            return;
        }
        int spinTicks = (int) anticheatPlayer.getOrDefault("RotationB_SpinTicks", 0);
        int snapCount = (int) anticheatPlayer.getOrDefault("RotationB_SnapCount", 0);
        int lastSnapTick = (int) anticheatPlayer.getOrDefault("RotationB_LastSnapTick", 0);
        if (!riding && ticksExisted - lastSnapTick > 10) snapCount = 0;
        if (yawDelta >= 100) {
            spinTicks++;
            if (yawDelta >= 170) {
                snapCount++;
                anticheatPlayer.put("RotationB_LastSnapTick", ticksExisted);
            }
        } else {
            spinTicks = 0;
        }
        if (spinTicks >= 5 || snapCount >= 2) {
            snapCount = 0;
            vl++;
            if (vl >= vlThreshold && now - lastAlert > cooldown) {
                anticheatPlayer.put("RotationB_LastAlert", now);
                printFlag(anticheatPlayer, "Vulcan Rotation", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("RotationB_SpinTicks", spinTicks);
        anticheatPlayer.put("RotationB_SnapCount", snapCount);
        anticheatPlayer.put("RotationB_VL", vl);
    }

    void SpiderA(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "SpiderA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "SpiderA_LastAlert", 0L);
        boolean inWater = Boolean.TRUE.equals(anticheatPlayer.get( "inWater"));
        boolean inLava = Boolean.TRUE.equals(anticheatPlayer.get( "inLava"));
        boolean onLadder = Boolean.TRUE.equals(anticheatPlayer.get( "onLadder"));
        boolean riding = anticheatPlayer.get( "riding") != null;
        boolean creative = Boolean.TRUE.equals(anticheatPlayer.get( "creative"));
        int lastHit = (int) anticheatPlayer.getOrDefault( "lastHitTick", 0);
        int ticksExisted = (int) anticheatPlayer.get( "ticksExisted");
        RavenVec3 position = (RavenVec3) anticheatPlayer.get( "position");
        RavenVec3 lastPosition = (RavenVec3) anticheatPlayer.get( "lastPosition");
        double deltaY = position.y - lastPosition.y;
        boolean wall = checkSurroundingBlocks(position);
        boolean jumpBoost = false;
        List <Object[] > effects = (List <Object[] >) anticheatPlayer.get( "potionEffects");
        if (effects != null) {
            for (Object[] effect : effects) {
                if ((Integer) effect[0] == 8) {
                    jumpBoost = true;
                    break;
                }
            }
        }
        int climbTicks = (int) anticheatPlayer.getOrDefault( "SpiderA_ClimbTicks", 0);
        if (deltaY > 0.05 && wall && !inWater && !inLava && !onLadder && !riding && !creative && !jumpBoost && !(lastHit > 0 && ticksExisted - lastHit <= 10)) {
            climbTicks++;
            if (climbTicks >= 9) {
                vl++;
                if (vl >= vlThreshold && now - lastAlert > cooldown) {
                    anticheatPlayer.put("SpiderA_LastAlert", now);
                    printFlag(anticheatPlayer, "NCP Spider", vl);
                }
            }
        } else {
            climbTicks = 0;
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("SpiderA_ClimbTicks", climbTicks);
        anticheatPlayer.put("SpiderA_VL", vl);
    }

    void AirJumpA(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "AirJumpA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "AirJumpA_LastAlert", 0L);
        boolean inWater = Boolean.TRUE.equals(anticheatPlayer.get( "inWater"));
        boolean inLava = Boolean.TRUE.equals(anticheatPlayer.get( "inLava"));
        boolean onLadder = Boolean.TRUE.equals(anticheatPlayer.get( "onLadder"));
        boolean riding = anticheatPlayer.get( "riding") != null;
        boolean creative = Boolean.TRUE.equals(anticheatPlayer.get( "creative"));
        int lastHit = (int) anticheatPlayer.getOrDefault( "lastHitTick", 0);
        int ticksExisted = (int) anticheatPlayer.get( "ticksExisted");
        RavenVec3 position = (RavenVec3) anticheatPlayer.get( "position");
        RavenVec3 lastPosition = (RavenVec3) anticheatPlayer.get( "lastPosition");
        double deltaY = position.y - lastPosition.y;
        int count = (int) anticheatPlayer.getOrDefault( "AirJumpA_Count", 0);
        if (isAirborne(anticheatPlayer, position) && !inWater && !inLava && !onLadder && !riding && !creative && !(lastHit > 0 && ticksExisted - lastHit <= 10) && deltaY > 0.25) {
            count++;
        } else {
            count = Math.max(count - 1, 0);
        }
        if (count >= 8) {
            vl++;
            if (vl >= vlThreshold && now - lastAlert > cooldown) {
                anticheatPlayer.put("AirJumpA_LastAlert", now);
                printFlag(anticheatPlayer, "Matrix AirJump", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("AirJumpA_Count", count);
        anticheatPlayer.put("AirJumpA_VL", vl);
    }

    void StepA(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "StepA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "StepA_LastAlert", 0L);
        boolean inWater = Boolean.TRUE.equals(anticheatPlayer.get( "inWater"));
        boolean inLava = Boolean.TRUE.equals(anticheatPlayer.get( "inLava"));
        boolean onLadder = Boolean.TRUE.equals(anticheatPlayer.get( "onLadder"));
        boolean riding = anticheatPlayer.get( "riding") != null;
        boolean creative = Boolean.TRUE.equals(anticheatPlayer.get( "creative"));
        int lastHit = (int) anticheatPlayer.getOrDefault( "lastHitTick", 0);
        int ticksExisted = (int) anticheatPlayer.get( "ticksExisted");
        RavenVec3 position = (RavenVec3) anticheatPlayer.get( "position");
        RavenVec3 lastPosition = (RavenVec3) anticheatPlayer.get( "lastPosition");
        double deltaY = position.y - lastPosition.y;
        String below = world.getBlockAt((int) Math.floor(position.x), (int) Math.floor(position.y) - 1, (int) Math.floor(position.z)).name;
        String feet = world.getBlockAt((int) Math.floor(position.x), (int) Math.floor(position.y), (int) Math.floor(position.z)).name;
        if (deltaY > 0.7 && !inWater && !inLava && !onLadder && !riding && !creative && !(lastHit > 0 && ticksExisted - lastHit <= 12) && !below.equals("slime") && !feet.equals("web") && !below.equals("web") && !feet.equals("vine") && !below.equals("vine")) {
            vl++;
            if (vl >= vlThreshold && now - lastAlert > cooldown) {
                anticheatPlayer.put("StepA_LastAlert", now);
                printFlag(anticheatPlayer, "Vulcan Step", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("StepA_VL", vl);
    }

    void JesusA(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "JesusA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "JesusA_LastAlert", 0L);
        boolean onGround = Boolean.TRUE.equals(anticheatPlayer.get( "onGround"));
        boolean inWater = Boolean.TRUE.equals(anticheatPlayer.get( "inWater"));
        boolean inLava = Boolean.TRUE.equals(anticheatPlayer.get( "inLava"));
        boolean riding = anticheatPlayer.get( "riding") != null;
        RavenVec3 position = (RavenVec3) anticheatPlayer.get( "position");
        String feet = world.getBlockAt((int) Math.floor(position.x), (int) Math.floor(position.y), (int) Math.floor(position.z)).name;
        String below = world.getBlockAt((int) Math.floor( position.x), (int) Math.floor(position.y) - 1, (int) Math.floor(position.z)).name;
        boolean onLiquid = feet.startsWith( "water") || feet.equals( "lava") || feet.equals( "flowing_lava");
        boolean aboveLiquid = !onLiquid  && (below.startsWith( "water") || below.equals( "lava") || below.equals( "flowing_lava"));
        List <RavenVec3 > positions = (List <RavenVec3 >) anticheatPlayer.get( "previousPositions");
        if (onGround && !inWater && !inLava && !riding && (onLiquid || aboveLiquid) && positions != null && positions.size() >= 20) {
            double distance = 0;
            for (int i = 0; i < positions.size() - 1; i++) {
                RavenVec3 current = positions.get(i);
                RavenVec3 previous = positions.get(i + 1);
                distance += Math.sqrt(Math.pow(current.x - previous.x, 2) + Math.pow(current.z - previous.z, 2));
            }
            if (distance > 1.0) {
                vl++;
                if (vl >= vlThreshold && now - lastAlert > cooldown) {
                    anticheatPlayer.put("JesusA_LastAlert", now);
                    printFlag(anticheatPlayer, "Grim Jesus", vl);
                }
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("JesusA_VL", vl);
    }

    void NoSlowB(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "NoSlowB_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "NoSlowB_LastAlert", 0L);
        boolean using = Boolean.TRUE.equals(anticheatPlayer.get( "using"));
        boolean onGround = Boolean.TRUE.equals(anticheatPlayer.get( "onGround"));
        boolean inWater = Boolean.TRUE.equals(anticheatPlayer.get( "inWater"));
        boolean inLava = Boolean.TRUE.equals(anticheatPlayer.get( "inLava"));
        boolean riding = anticheatPlayer.get( "riding") != null;
        boolean creative = Boolean.TRUE.equals(anticheatPlayer.get("creative"));
        if (!using || !onGround || inWater || inLava || riding || creative) {
            anticheatPlayer.put("NoSlowB_VL", Math.max(vl - 1, 0));
            return;
        }
        // Intave-style: only the movement measured WHILE using an item on the ground
        // counts (updatePlayerData accumulates usingDist/usingTicks per tick and
        // resets on any non-using/airborne tick). Vanilla 1.8 caps ground speed while
        // using at ~0.043 blocks/tick (0.2 input factor); even with friction/momentum
        // spikes it stays well below 0.10. Flag only after 5+ sustained fast ticks,
        // so knockback and one-off spikes cannot trigger it.
        int usingTicks = (int) anticheatPlayer.getOrDefault("usingTicks", 0);
        double usingDist = (double) anticheatPlayer.getOrDefault("usingDist", 0d);
        if (usingTicks >= 5 && usingDist / usingTicks > 0.10) {
            vl++;
            if (vl >= vlThreshold && now - lastAlert > cooldown) {
                anticheatPlayer.put("NoSlowB_LastAlert", now);
                printFlag(anticheatPlayer, "Verus NoSlow", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("NoSlowB_VL", vl);
    }

    void KillAuraA(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "KillAuraA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "KillAuraA_LastAlert", 0L);
        int ticksExisted = (int) anticheatPlayer.get( "ticksExisted");
        boolean riding = anticheatPlayer.get( "riding") != null;
        RavenVec3 position = (RavenVec3) anticheatPlayer.get( "position");
        if (position == null || riding || isReplay()) {
            anticheatPlayer.put( "KillAuraA_VL", Math.max(vl - 1, 0));
            return;
        }
        // Real killaura: score the hit timing and rotation precision of the hits
        // this suspect actually lands. Swing counting flags any fast clicker; hit
        // timing and aim precision cannot be produced by clicking. A hit belongs to
        // this suspect when a victim's lastHitTick is the current tick and
        // findRecentAttacker resolves back to the suspect (it already enforces the
        // melee-weapon + looking-at-a-real-player context gate).
        Map <String, Object > hitVictim = null;
        boolean antiBot = true;
        for (Map.Entry<RavenPlayer, Map<String, Object>> entry : anticheatPlayers.entrySet()) {
            Map<String, Object> v = entry.getValue();
            if (v == null || v == anticheatPlayer) continue;
            if (antiBot && isBot(v)) continue;
            if ((int) v.getOrDefault("lastHitTick", 0) != ticksExisted) continue;
            if (findRecentAttacker(v, ticksExisted) == anticheatPlayer) {
                hitVictim = v;
                break;
            }
        }
        if (hitVictim == null) {
            anticheatPlayer.put( "KillAuraA_VL", Math.max(vl - 1, 0));
            return;
        }
        // 1) Hit timing: the 1.8 client enforces a 0.5s (10 tick) attack cooldown,
        // so two attributed hits < 8 ticks apart are impossible for a legit client.
        // Require three consecutive too-fast gaps so latency jitter cannot trip it.
        List<Integer> hitTicks = (List<Integer>) anticheatPlayer.get( "KillAuraA_HitTicks");
        if (hitTicks == null) {
            hitTicks = new ArrayList<>();
            anticheatPlayer.put( "KillAuraA_HitTicks", hitTicks);
        }
        hitTicks.add(ticksExisted);
        while (!hitTicks.isEmpty() && ticksExisted - hitTicks.get(0) > 30) hitTicks.remove(0);
        int fastHits = (int) anticheatPlayer.getOrDefault( "KillAuraA_FastHits", 0);
        if (hitTicks.size() >= 2) {
            int gap = ticksExisted - hitTicks.get(hitTicks.size() - 2);
            if (gap < 8) fastHits++;
            else fastHits = 0;
        }
        // 2) Rotation precision: killaura rotates exactly onto the victim's hitbox
        // on every hit. Five consecutive hits aimed within 1.5 yaw / 2.5 pitch
        // degrees of the perfect angle is beyond human jitter.
        RavenVec3 victimPos = (RavenVec3) hitVictim.get("position");
        RavenVec3 eye = position.offset(0, 1.62, 0);
        RavenVec3 center = victimPos.offset(0, 0.9, 0);
        double[] required = getAnglesToTarget(eye, center);
        float yaw = (float) anticheatPlayer.get("yaw");
        float pitch = (float) anticheatPlayer.get("pitch");
        double errYaw = Math.abs(yaw - required[0]);
        if (errYaw > 180) errYaw = 360 - errYaw;
        double errPitch = Math.abs(pitch - required[1]);
        int perfectStreak = (int) anticheatPlayer.getOrDefault( "KillAuraA_PerfectStreak", 0);
        if (errYaw < 1.5 && errPitch < 2.5) perfectStreak++;
        else perfectStreak = 0;
        anticheatPlayer.put( "KillAuraA_HitTicks", hitTicks);
        anticheatPlayer.put( "KillAuraA_FastHits", fastHits);
        anticheatPlayer.put( "KillAuraA_PerfectStreak", perfectStreak);
        double violation = 0;
        if (fastHits >= 3) violation += 3.0;
        if (perfectStreak >= 5) violation += 3.0;
        if (fastHits >= 3 && perfectStreak >= 4) violation += 4.0;
        if (violation > 0) {
            vl += (int) Math.ceil(violation);
            if (vl >= vlThreshold && now - lastAlert > cooldown) {
                anticheatPlayer.put( "KillAuraA_LastAlert", now);
                printFlag(anticheatPlayer, "NCP KillAura", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put( "KillAuraA_VL", vl);
    }

    void HitMissRatioA(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        // Hit/Miss Ratio (HMR): counts every swing the suspect makes while a real
        // enemy is within 4 blocks and the 1.8 attack cooldown could have landed
        // damage ("attempts"), and every hit the suspect actually lands on such an
        // enemy. Killaura hits the first possible swing of every damage window
        // (hit rate ~100%); a legit player misses plenty of those swings. Flags
        // only on a sustained near-perfect ratio with high hits-per-second, and the
        // counters reset once attempts reach 100 so the state cannot compound out
        // of hand.
        int vl = (int) anticheatPlayer.getOrDefault( "HMR_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "HMR_LastAlert", 0L);
        int ticksExisted = (int) anticheatPlayer.get( "ticksExisted");
        RavenVec3 position = (RavenVec3) anticheatPlayer.get( "position");
        boolean riding = anticheatPlayer.get( "riding") != null;
        if (position == null || riding || isReplay()) {
            anticheatPlayer.put( "HMR_VL", Math.max(vl - 1, 0));
            return;
        }
        boolean antiBot = true;
        int hits = (int) anticheatPlayer.getOrDefault( "HMR_Hits", 0);
        int attempts = (int) anticheatPlayer.getOrDefault( "HMR_Attempts", 0);
        int lastHitTick = (int) anticheatPlayer.getOrDefault( "HMR_LastHitTick", 0);
        List<Integer> hitTicks = (List<Integer>) anticheatPlayer.get( "HMR_HitTicks");
        if (hitTicks == null) {
            hitTicks = new ArrayList<>();
            anticheatPlayer.put( "HMR_HitTicks", hitTicks);
        }
        while (!hitTicks.isEmpty() && ticksExisted - hitTicks.get(0) > 40) hitTicks.remove(0);
        RavenItem heldItem = (RavenItem) anticheatPlayer.get( "heldItem");
        List<Integer> swings = (List<Integer>) anticheatPlayer.get( "swingTimes");
        boolean swungThisTick = swings != null && !swings.isEmpty() && swings.get(swings.size() - 1) == ticksExisted;
        // Real enemy within 4 blocks? (not a bot, not on the local team)
        boolean enemyInRange = false;
        for (Map.Entry<RavenPlayer, Map<String, Object>> entry : anticheatPlayers.entrySet()) {
            Map<String, Object> other = entry.getValue();
            if (other == null || other == anticheatPlayer) continue;
            if (antiBot && isBot(other)) continue;
            if (isSameTeamAsLocal(other)) continue;
            RavenVec3 otherPos = (RavenVec3) other.get("position");
            if (otherPos == null) continue;
            if (position.distanceTo(otherPos) <= 4.0) {
                enemyInRange = true;
                break;
            }
        }
        if (!enemyInRange) {
            anticheatPlayer.put( "HMR_VL", Math.max(vl - 1, 0));
            return;
        }
        // Hit landed this tick on an enemy within 4 blocks?
        boolean hitThisTick = false;
        for (Map.Entry<RavenPlayer, Map<String, Object>> entry : anticheatPlayers.entrySet()) {
            Map<String, Object> v = entry.getValue();
            if (v == null || v == anticheatPlayer) continue;
            if (antiBot && isBot(v)) continue;
            if (isSameTeamAsLocal(v)) continue;
            RavenVec3 vPos = (RavenVec3) v.get("position");
            if (vPos == null || position.distanceTo(vPos) > 4.0) continue;
            if ((int) v.getOrDefault("lastHitTick", 0) == ticksExisted && findRecentAttacker(v, ticksExisted) == anticheatPlayer) {
                hitThisTick = true;
                break;
            }
        }
        // A swing counts as an attempt only when the 0.5s (10 tick) damage cooldown
        // has passed: only then could that swing have landed damage, so every non-hit
        // attempt is a genuine miss. Killaura swings once per damage window and hits
        // it; legit players swing several times per window and miss most of those.
        if (swungThisTick && isMeleeWeapon(heldItem) && (lastHitTick == 0 || ticksExisted - lastHitTick >= 8)) {
            attempts++;
        }
        if (hitThisTick) {
            hits++;
            lastHitTick = ticksExisted;
            hitTicks.add(ticksExisted);
        }
        // Reset cap: once attempts reach 100 (a perfect run would be 100/100) the
        // counters reset to 0/0 so the ratio can never compound out of hand.
        if (attempts >= 100) {
            attempts = 0;
            hits = 0;
            lastHitTick = 0;
        }
        int hpsWindow = hitTicks.size();
        double hitRate = attempts > 0 ? Math.min(1.0, (double) hits / attempts) : 0.0;
        if (attempts >= 20 && hpsWindow >= 4 && hitRate >= 0.95) {
            vl += 2;
            if (vl >= vlThreshold && now - lastAlert > cooldown) {
                anticheatPlayer.put( "HMR_LastAlert", now);
                printFlag(anticheatPlayer, "Hit Miss Ratio", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put( "HMR_VL", vl);
        anticheatPlayer.put( "HMR_Hits", hits);
        anticheatPlayer.put( "HMR_Attempts", attempts);
        anticheatPlayer.put( "HMR_LastHitTick", lastHitTick);
        anticheatPlayer.put( "HMR_HitTicks", hitTicks);
    }

    void CombatCombineA(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        // Matrix combine module (ka.abt.magic + hb.reach x2 within 5000ms -> +5 VL):
        // aimbot-style evidence together with repeated reach flags within 5 seconds
        // is far stronger than either alone. Reads the attacker-side flag timestamps
        // written by the reach checks and the last-alert times of the aim checks.
        int vl = (int) anticheatPlayer.getOrDefault( "CombatCombineA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "CombatCombineA_LastAlert", 0L);
        long now = this.now;
        String[] reachKeys = {"ReachA_FlagTime", "ReachB_FlagTime", "ReachC_FlagTime", "ReachA_HitboxFlagTime", "HitBoxA_FlagTime"};
        int reachFlags = 0;
        for (String key : reachKeys) {
            long t = (Long) anticheatPlayer.getOrDefault(key, 0L);
            if (t > 0 && now - t <= 5000) reachFlags++;
        }
        String[] aimKeys = {"AimA_LastAlert", "AimB_LastAlert", "AimC_LastAlert", "IntaveRhythm_LastAlert", "AngleA_LastAlert",
            "GrimSimAim_LastAlert", "IntaveHeuristic_LastAlert", "AimGcdA_LastAlert", "GcdB_LastAlert", "DivisorYA_LastAlert",
            "AimFlickA_LastAlert", "PitchLockA_LastAlert", "RotationSpeedA_LastAlert", "AimD_LastAlert", "AimE_LastAlert", "AimF_LastAlert"};
        boolean aimbotEvidence = false;
        for (String key : aimKeys) {
            long t = (Long) anticheatPlayer.getOrDefault(key, 0L);
            if (t > 0 && now - t <= 5000) {
                aimbotEvidence = true;
                break;
            }
        }
        if (aimbotEvidence && reachFlags >= 2) {
            vl += 5;
            if (vl >= vlThreshold && now - lastAlert > cooldown) {
                anticheatPlayer.put( "CombatCombineA_LastAlert", now);
                printFlag(anticheatPlayer, "Matrix Combine", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put( "CombatCombineA_VL", vl);
    }

    void WrongTurnA(Map <String, Object > p, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) p.getOrDefault( "WrongTurnA_VL", 0);
        long lastAlert = (long) p.getOrDefault( "WrongTurnA_LastAlert", 0L);
        // NCP WrongTurn: vanilla clamps pitch to [-90, 90]; a pitch outside that
        // range is protocol-impossible for a real player and only rotation/freecam
        // hacks produce it. Immediate single-shot flag, zero false positives.
        float pitch = (float) p.getOrDefault( "pitch", 0f);
        if (pitch > 90.0f || pitch < -90.0f) {
            vl++;
            if (vl >= vlThreshold && now - lastAlert > cooldown) {
                p.put( "WrongTurnA_LastAlert", now);
                printFlag(p, "NCP WrongTurn", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        p.put( "WrongTurnA_VL", vl);
    }

    void ClickStatsA(Map <String, Object > p, int cooldown, int vlThreshold, boolean alerts) {
        // Intave click-pattern statistics on millisecond-precision swing gaps. Human
        // clicking jitters; autoclickers produce uniform, low-entropy, repetitive
        // intervals. Signals: kurtosis, entropy, deviation, bursts, equal cadence.
        int vl = (int) p.getOrDefault( "ClickStatsA_VL", 0);
        long lastAlert = (long) p.getOrDefault( "ClickStatsA_LastAlert", 0L);
        List<Long> swingsMs = (List<Long>) p.get( "swingTimesMs");
        RavenItem heldItem = (RavenItem) p.get( "heldItem");
        if (swingsMs == null || swingsMs.size() < 3 || !isMeleeWeapon(heldItem) || p.get("riding") != null) {
            p.put( "ClickStatsA_VL", Math.max(vl - 1, 0));
            return;
        }
        long now = this.now;
        if (now - swingsMs.get(swingsMs.size() - 1) > 4000) {
            p.put( "ClickStatsA_VL", Math.max(vl - 1, 0));
            return;
        }
        List<Long> gaps = new ArrayList<>();
        for (int i = 1; i < swingsMs.size(); i++) {
            long g = swingsMs.get(i) - swingsMs.get(i - 1);
            if (g > 0 && g <= 4000) gaps.add(g);
        }
        int violation = 0;
        if (gaps.size() >= 25) {
            // Intave Kurtosis (Kurtosis.java): d2*(s4/(s2/sum)^2)-d3, /1000 < 6 flags.
            // With ms jitter, a uniform autoclicker scores ~3; human jitter 13+.
            double sum = 0;
            for (int i = gaps.size() - 25; i < gaps.size(); i++) sum += gaps.get(i);
            double average = sum / 25.0;
            double s2 = 0, s4 = 0;
            for (int i = gaps.size() - 25; i < gaps.size(); i++) {
                double d = average - gaps.get(i);
                s2 += d * d;
                s4 += d * d * d * d;
            }
            double d2 = 25.0 * 26.0 / (24.0 * 23.0 * 22.0);
            double d3 = 3.0 * 24.0 * 24.0 / (23.0 * 22.0);
            double kurtosis = (s2 > 0 && sum > 0) ? d2 * (s4 / Math.pow(s2 / sum, 2)) - d3 : Double.POSITIVE_INFINITY;
            if (kurtosis / 1000.0 < 6.0) {
                int kurtVl = (int) p.getOrDefault( "ClickStatsA_KurtVl", 0);
                kurtVl++;
                p.put( "ClickStatsA_KurtVl", kurtVl);
                if (kurtVl > 15) violation += 2;
            } else {
                p.put( "ClickStatsA_KurtVl", 0);
            }
        }
        if (gaps.size() >= 100) {
            // Intave Entropy (Entropy.java): Shannon entropy over exact gap values.
            // <=2 distinct intervals among 100 clicks means H <= 1.0 = low entropy.
            Map<Long, Integer> counts = new HashMap<>();
            for (int i = gaps.size() - 100; i < gaps.size(); i++) {
                counts.put(gaps.get(i), counts.getOrDefault(gaps.get(i), 0) + 1);
            }
            double entropy = 0;
            for (int c : counts.values()) {
                double prob = c / 100.0;
                entropy -= prob * (Math.log(prob) / Math.log(2));
            }
            if (entropy <= 1.0 && entropy >= 0.35) violation += 2;
        }
        if (gaps.size() >= 50) {
            // Intave Deviation (Deviation.java): std of the last 50 gaps; <25 = too
            // uniform, <10 = extremely uniform.
            double s = 0;
            for (int i = gaps.size() - 50; i < gaps.size(); i++) s += gaps.get(i);
            double mean = s / 50.0;
            double var = 0;
            for (int i = gaps.size() - 50; i < gaps.size(); i++) {
                double d = gaps.get(i) - mean;
                var += d * d;
            }
            double std = Math.sqrt(var / 50.0);
            if (std < 25) violation += 1;
            if (std < 10) violation += 2;
        }
        List<Integer> swings = (List<Integer>) p.get( "swingTimes");
        if (swings != null && swings.size() >= 20) {
            // Intave Bursts (Bursts.java): streaks of consecutive click ticks score
            // streak+2 each (a double click would subtract); total >= 20 flags.
            int consecutive = 0;
            int burstVl = 0;
            for (int i = 1; i < swings.size(); i++) {
                int gap = swings.get(i) - swings.get(i - 1);
                if (gap == 1) consecutive++;
                else if (gap > 1) {
                    if (consecutive > 5) burstVl += consecutive + 2;
                    consecutive = 0;
                }
            }
            if (consecutive > 5) burstVl += consecutive + 2;
            if (burstVl >= 20) violation += 2;
            // Intave EqualDelay: the same inter-click gap repeating; limit grows with
            // the gap size. Humans never reproduce a cadence this many times.
            int bestStreak = 0;
            int bestStreakGap = 0;
            int lastGap = -1;
            int streak = 0;
            for (int i = 1; i < swings.size(); i++) {
                int gap = swings.get(i) - swings.get(i - 1);
                if (gap <= 0 || gap > 20) {
                    lastGap = -1;
                    streak = 0;
                    continue;
                }
                if (gap == lastGap) {
                    streak++;
                    if (streak > bestStreak) {
                        bestStreak = streak;
                        bestStreakGap = gap;
                    }
                } else {
                    lastGap = gap;
                    streak = 1;
                }
            }
            int limit = 10;
            if (bestStreakGap > 1) limit += 2;
            if (bestStreakGap > 2) limit += 2;
            if (bestStreakGap > 3) limit += 2;
            if (bestStreak > limit) violation += 2;
        }
        if (violation > 0) {
            vl += violation;
            if (vl >= vlThreshold && now - lastAlert > cooldown) {
                p.put( "ClickStatsA_LastAlert", now);
                printFlag(p, "Intave ClickStats", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        p.put( "ClickStatsA_VL", vl);
    }

    void PreAttackA(Map <String, Object > p, int cooldown, int vlThreshold, boolean alerts) {
        // Intave PreAttackHeuristic (1.8): a legit player sometimes swings at an
        // enemy without the swing turning into an attack (misclicks, hesitations).
        // An autoclicker only swings when it attacks: fewer than 4 pre-swings per
        // 100 attacks means the swings are automated.
        int vl = (int) p.getOrDefault( "PreAttackA_VL", 0);
        long lastAlert = (long) p.getOrDefault( "PreAttackA_LastAlert", 0L);
        RavenItem heldItem = (RavenItem) p.get( "heldItem");
        RavenVec3 pos = (RavenVec3) p.get( "position");
        int ticksExisted = (int) p.get( "ticksExisted");
        if (!isMeleeWeapon(heldItem) || pos == null || p.get("riding") != null) {
            p.put( "PreAttackA_VL", Math.max(vl - 1, 0));
            return;
        }
        boolean attackedThisTick = false;
        RavenVec3 targetPos = null;
        boolean antiBot = true;
        for (Map.Entry<RavenPlayer, Map<String, Object>> entry : anticheatPlayers.entrySet()) {
            Map<String, Object> v = entry.getValue();
            if (v == null || v == p) continue;
            if (antiBot && isBot(v)) continue;
            if (isSameTeamAsLocal(v)) continue;
            RavenVec3 vPos = (RavenVec3) v.get("position");
            if (vPos == null) continue;
            if ((int) v.getOrDefault("lastHitTick", 0) == ticksExisted && findRecentAttacker(v, ticksExisted) == p) {
                attackedThisTick = true;
                targetPos = vPos;
                break;
            }
        }
        boolean swungThisTick = (int) p.getOrDefault( "lastSwingTick", 0) == ticksExisted;
        int preAttacks = (int) p.getOrDefault( "PreAttackA_Pre", 0);
        int attacks = (int) p.getOrDefault( "PreAttackA_Attacks", 0);
        boolean flagNow = false;
        if (swungThisTick && !attackedThisTick && targetPos == null) {
            // Cursor-on-entity raycast (0.25 hitbox expansion, reach 4.0) against the
            // nearest moving enemy: a swing that hits the cursor target but never
            // becomes an attack is a pre-attack.
            Map<String, Object> cursorTarget = null;
            double best = 4.0;
            for (Map.Entry<RavenPlayer, Map<String, Object>> entry : anticheatPlayers.entrySet()) {
                Map<String, Object> v = entry.getValue();
                if (v == null || v == p) continue;
                if (antiBot && isBot(v)) continue;
                if (isSameTeamAsLocal(v)) continue;
                RavenVec3 vPos = (RavenVec3) v.get("position");
                if (vPos == null) continue;
                double d = pos.distanceTo(vPos);
                if (d > best) continue;
                RavenVec3 vLast = (RavenVec3) v.get("lastPosition");
                if (vLast == null || vPos.distanceTo(vLast) < 0.05) continue;
                best = d;
                cursorTarget = v;
            }
            if (cursorTarget != null) {
                RavenVec3 vPos = (RavenVec3) cursorTarget.get("position");
                double half = 0.3 + 0.25;
                double minX = vPos.x - half, maxX = vPos.x + half;
                double minZ = vPos.z - half, maxZ = vPos.z + half;
                double minY = vPos.y, maxY = vPos.y + 1.8 + 0.25;
                float ayaw = (float) p.getOrDefault( "yaw", 0f);
                float apitch = (float) p.getOrDefault( "pitch", 0f);
                float preyaw = (float) p.getOrDefault( "previousYaw", 0f);
                RavenVec3 eye = pos.offset(0, 1.62, 0);
                boolean hit = false;
                for (float lookYaw : new float[]{ayaw, preyaw}) {
                    double radYaw = Math.toRadians(lookYaw);
                    double radPitch = Math.toRadians(apitch);
                    double dx = -Math.sin(radYaw) * Math.cos(radPitch);
                    double dy = -Math.sin(radPitch);
                    double dz = Math.cos(radYaw) * Math.cos(radPitch);
                    if (rayBoxDistance(eye, dx, dy, dz, 4.0, minX, minY, minZ, maxX, maxY, maxZ) >= 0) {
                        hit = true;
                        break;
                    }
                }
                if (hit) preAttacks++;
            }
        }
        if (attackedThisTick) attacks++;
        if (attacks >= 100) {
            if (preAttacks < 4) flagNow = true;
            attacks = 0;
            preAttacks = 0;
        }
        if (flagNow) {
            vl += 2;
            if (vl >= vlThreshold && now - lastAlert > cooldown) {
                p.put( "PreAttackA_LastAlert", now);
                printFlag(p, "Intave PreAttack", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        p.put( "PreAttackA_Pre", preAttacks);
        p.put( "PreAttackA_Attacks", attacks);
        p.put( "PreAttackA_VL", vl);
    }

    void StdDevA(Map <String, Object > p, int cooldown, int vlThreshold, boolean alerts) {
        // Intave RotationStandardDeviationHeuristic: while actively rotating toward a
        // moving target, the spread of the look-error is measured. Aim-assist locks
        // produce near-constant error (std < 1 degree); humans jitter.
        int vl = (int) p.getOrDefault( "StdDevA_VL", 0);
        long lastAlert = (long) p.getOrDefault( "StdDevA_LastAlert", 0L);
        if (isReplay()) {
            p.put( "StdDevA_VL", Math.max(vl - 1, 0));
            return;
        }
        if (!inCombatState(p)) {
            p.put( "StdDevA_Yaw", new ArrayList<Double>());
            p.put( "StdDevA_Pitch", new ArrayList<Double>());
            p.put( "StdDevA_VL", Math.max(vl - 1, 0));
            return;
        }
        RavenVec3 pos = (RavenVec3) p.get( "position");
        if (pos == null) {
            p.put( "StdDevA_VL", Math.max(vl - 1, 0));
            return;
        }
        // Nearest moving enemy = the tracked target (proxy for lastAttackedEntity).
        RavenVec3 targetPos = null;
        double best = 8.0;
        boolean antiBot = true;
        for (Map.Entry<RavenPlayer, Map<String, Object>> entry : anticheatPlayers.entrySet()) {
            Map<String, Object> v = entry.getValue();
            if (v == null || v == p) continue;
            if (antiBot && isBot(v)) continue;
            if (isSameTeamAsLocal(v)) continue;
            RavenVec3 vPos = (RavenVec3) v.get("position");
            if (vPos == null) continue;
            double d = pos.distanceTo(vPos);
            if (d > best) continue;
            RavenVec3 vLast = (RavenVec3) v.get("lastPosition");
            if (vLast == null || vPos.distanceTo(vLast) < 0.05) continue;
            best = d;
            targetPos = vPos;
        }
        if (targetPos == null) {
            p.put( "StdDevA_VL", Math.max(vl - 1, 0));
            return;
        }
        float yaw = (float) p.get( "yaw");
        float prevYaw = (float) p.get( "previousYaw");
        float pitch = (float) p.get( "pitch");
        float prevPitch = (float) p.get( "previousPitch");
        double yawSpeed = Math.abs(yaw - prevYaw);
        if (yawSpeed > 180) yawSpeed = 360 - yawSpeed;
        int violation = 0;
        double distToPerfectYaw = Math.abs(getFacingAngle(yaw, pos, targetPos));
        if (yawSpeed > 2.6) {
            List<Double> yawSamples = (List<Double>) p.get( "StdDevA_Yaw");
            if (yawSamples == null) {
                yawSamples = new ArrayList<>();
                p.put( "StdDevA_Yaw", yawSamples);
            }
            yawSamples.add(distToPerfectYaw);
            if (yawSamples.size() >= 7) {
                double sum = 0;
                for (double s : yawSamples) sum += s;
                double mean = sum / yawSamples.size();
                double var = 0;
                for (double s : yawSamples) var += (s - mean) * (s - mean);
                double std = Math.sqrt(var / yawSamples.size());
                double bal = (Double) p.getOrDefault( "StdDevA_YawBal", 0d);
                if (std < 1.0) {
                    bal++;
                    if (bal >= 3) {
                        violation += 2;
                        bal = Math.max(bal - 1, 0);
                    }
                } else {
                    bal = bal > 0 ? 0.2 : 0;
                }
                p.put( "StdDevA_YawBal", bal);
                yawSamples.clear();
            }
        }
        double pitchSpeed = Math.abs(pitch - prevPitch);
        if (pitchSpeed > 0.5 && yawSpeed > 3.0) {
            RavenVec3 eye = pos.offset(0, 1.62, 0);
            double distToPerfectPitch = Math.abs(pitch - getPitchTo(eye, targetPos.offset(0, 0.9, 0)));
            List<Double> pitchSamples = (List<Double>) p.get( "StdDevA_Pitch");
            if (pitchSamples == null) {
                pitchSamples = new ArrayList<>();
                p.put( "StdDevA_Pitch", pitchSamples);
            }
            pitchSamples.add(distToPerfectPitch);
            if (pitchSamples.size() >= 10) {
                double sum = 0;
                for (double s : pitchSamples) sum += s;
                double mean = sum / pitchSamples.size();
                double var = 0;
                for (double s : pitchSamples) var += (s - mean) * (s - mean);
                double std = Math.sqrt(var / pitchSamples.size());
                double bal = (Double) p.getOrDefault( "StdDevA_PitchBal", 0d);
                if (std < 3.0) {
                    bal++;
                    if (bal >= 5) {
                        violation += 2;
                        bal = 2;
                    }
                } else {
                    bal = bal > 0 ? 0.2 : 0;
                }
                p.put( "StdDevA_PitchBal", bal);
                pitchSamples.clear();
            }
        }
        // Intave RotationAccuracyYawHeuristic (hitbox corners): while tracking a
        // moving target, the angular ERROR relative to the perfect yaw stays nearly
        // constant only if the aim is computer-corrected - a human's error
        // fluctuates every tick. A direction change resets the balance.
        int cornerBalance = (int) p.getOrDefault("StdDevA_Corner", 0);
        double prevErr = (double) p.getOrDefault("StdDevA_PrevErr", 0d);
        int lastDir = (int) p.getOrDefault("StdDevA_LastDir", 0);
        double signedErr = getFacingAngle(yaw, pos, targetPos);
        int dir = signedErr > 0 ? 1 : 0;
        if (dir != lastDir) {
            cornerBalance = 0;
        } else if (yawSpeed > 3.0) {
            double deviation = Math.abs(prevErr - distToPerfectYaw);
            double increase = Math.max(-0.2, Math.min((1.0 - deviation) * 4.0, 4.0));
            cornerBalance = (int) Math.max(0, Math.min(cornerBalance + increase, 100));
            if (cornerBalance > 30) {
                violation += 2;
                cornerBalance = 20;
            }
        }
        p.put("StdDevA_Corner", cornerBalance);
        p.put("StdDevA_PrevErr", distToPerfectYaw);
        p.put("StdDevA_LastDir", dir);
        if (violation > 0) {
            vl += violation;
            if (vl >= vlThreshold && now - lastAlert > cooldown) {
                p.put( "StdDevA_LastAlert", now);
                printFlag(p, "Intave StdDev", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        p.put( "StdDevA_VL", vl);
    }

    void RotationResetA(Map <String, Object > p, int cooldown, int vlThreshold, boolean alerts) {
        // Intave RotationModuloResetHeuristic: after 5 seconds of tracking the same
        // target without teleports, a >100 degree yaw jump while the target is in
        // line of sight is a silent "rotation reset" (aimbot re-orientation).
        int vl = (int) p.getOrDefault( "RotationResetA_VL", 0);
        long lastAlert = (long) p.getOrDefault( "RotationResetA_LastAlert", 0L);
        if (isReplay()) {
            p.put( "RotationResetA_VL", Math.max(vl - 1, 0));
            return;
        }
        RavenVec3 pos = (RavenVec3) p.get( "position");
        int ticksExisted = (int) p.get( "ticksExisted");
        if (pos == null) {
            p.put( "RotationResetA_VL", Math.max(vl - 1, 0));
            return;
        }
        String lastVictimName = (String) p.getOrDefault( "RotationResetA_Victim", null);
        int lastAttackTick = (int) p.getOrDefault( "RotationResetA_AttackTick", 0);
        int lastSwitchTick = (int) p.getOrDefault( "RotationResetA_SwitchTick", ticksExisted);
        Map<String, Object> attacked = null;
        for (Map.Entry<RavenPlayer, Map<String, Object>> entry : anticheatPlayers.entrySet()) {
            Map<String, Object> v = entry.getValue();
            if (v == null || v == p) continue;
            if ((int) v.getOrDefault("lastHitTick", 0) == ticksExisted && findRecentAttacker(v, ticksExisted) == p) {
                attacked = v;
                break;
            }
        }if (attacked != null) {
            String name = (String) attacked.get("name");
            if (lastVictimName == null || !lastVictimName.equals(name)) {
                if (lastVictimName != null) lastSwitchTick = ticksExisted;
                lastVictimName = name;
                p.put( "RotationResetA_Victim", lastVictimName);
            }
            p.put( "RotationResetA_AttackTick", ticksExisted);
        }
        if (lastVictimName == null || lastAttackTick <= 0) {
            p.put( "RotationResetA_VL", Math.max(vl - 1, 0));
            return;
        }
        boolean violation = false;
        if (ticksExisted - lastSwitchTick > 100) {
            RavenVec3 victimPos = null;
            for (Map.Entry<RavenPlayer, Map<String, Object>> entry : anticheatPlayers.entrySet()) {
                Map<String, Object> v = entry.getValue();
                if (v == null || v == p) continue;
                String n = (String) v.getOrDefault("name", "");
                if (lastVictimName.equals(n)) {
                    victimPos = (RavenVec3) v.get("position");
                    break;
                }
            }
            if (victimPos != null) {
                float yaw = (float) p.get( "yaw");
                float prevYaw = (float) p.get( "previousYaw");
                double yawJump = Math.abs(yaw - prevYaw);
                if (yawJump > 180) yawJump = 360 - yawJump;
                double half = 0.3 + 0.1;
                double minX = victimPos.x - half, maxX = victimPos.x + half;
                double minZ = victimPos.z - half, maxZ = victimPos.z + half;
                double minY = victimPos.y, maxY = victimPos.y + 1.8 + 0.1;
                RavenVec3 eye = pos.offset(0, 1.62, 0);
                double radYaw = Math.toRadians(yaw);
                double radPitch = Math.toRadians((float) p.getOrDefault( "pitch", 0f));
                double dx = -Math.sin(radYaw) * Math.cos(radPitch);
                double dy = -Math.sin(radPitch);
                double dz = Math.cos(radYaw) * Math.cos(radPitch);
                boolean los = rayBoxDistance(eye, dx, dy, dz, 10.0, minX, minY, minZ, maxX, maxY, maxZ) >= 0;
                boolean pending = Boolean.TRUE.equals(p.get( "RotationResetA_Pending"));
                if (pending) {
                    if (los) violation = true;
                    p.put( "RotationResetA_Pending", false);
                } else if (yawJump > 100.0 && ticksExisted - lastAttackTick <= 10 && los) {
                    p.put( "RotationResetA_Pending", true);
                }
            }
        }
        if (violation) {
            vl += 2;
            if (vl >= vlThreshold && now - lastAlert > cooldown) {
                p.put( "RotationResetA_LastAlert", now);
                printFlag(p, "Intave Reset", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        p.put( "RotationResetA_VL", vl);
    }

    void MoveConsistencyA(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        // NCP MoveConsistency: every move must be within 1.25 blocks of the previous
        // position. Teleports, blink/position rewrites and freecam jumps exceed it.
        // Buffer requires 3+ occurrences so a legit pearl/end-rod teleport passes.
        int vl = (int) anticheatPlayer.getOrDefault( "MoveConsistencyA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "MoveConsistencyA_LastAlert", 0L);
        RavenVec3 position = (RavenVec3) anticheatPlayer.get( "position");
        RavenVec3 lastPosition = (RavenVec3) anticheatPlayer.get( "lastPosition");
        if (position == null || lastPosition == null || anticheatPlayer.get("riding") != null) {
            anticheatPlayer.put( "MoveConsistencyA_VL", Math.max(vl - 1, 0));
            return;
        }
        double delta = position.distanceTo(lastPosition);
        double buffer = (Double) anticheatPlayer.getOrDefault( "MoveConsistencyA_Buffer", 0d);
        if (delta > 1.25) {
            buffer += 0.5;
        } else {
            buffer = Math.max(buffer - 0.2, 0);
        }
        if (buffer >= 1.5) {
            buffer = 0;
            vl++;
            if (vl >= vlThreshold && now - lastAlert > cooldown) {
                anticheatPlayer.put( "MoveConsistencyA_LastAlert", now);
                printFlag(anticheatPlayer, "NCP MoveConsistency", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put( "MoveConsistencyA_Buffer", buffer);
        anticheatPlayer.put( "MoveConsistencyA_VL", vl);
    }

    void HoverA(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        // NCP hover: staying airborne 85+ ticks (4.25s) while neither falling far nor
        // landing is impossible legitimately. Falling is excluded by the vertical
        // bound: a real fall covers >2 blocks in that window.
        int vl = (int) anticheatPlayer.getOrDefault( "HoverA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "HoverA_LastAlert", 0L);
        boolean onGround = Boolean.TRUE.equals(anticheatPlayer.get( "onGround"));
        boolean onLadder = Boolean.TRUE.equals(anticheatPlayer.get( "onLadder"));
        boolean inWater = Boolean.TRUE.equals(anticheatPlayer.get( "inWater"));
        boolean inLava = Boolean.TRUE.equals(anticheatPlayer.get( "inLava"));
        boolean riding = anticheatPlayer.get( "riding") != null;
        boolean creative = Boolean.TRUE.equals(anticheatPlayer.get( "creative"));
        RavenVec3 position = (RavenVec3) anticheatPlayer.get( "position");
        int hoverTicks = (int) anticheatPlayer.getOrDefault( "HoverA_Ticks", 0);
        Double hoverStartY = (Double) anticheatPlayer.get( "HoverA_StartY");
        RavenVec3 lastPosition = (RavenVec3) anticheatPlayer.get( "lastPosition");
        if (position == null) {
            anticheatPlayer.put( "HoverA_VL", Math.max(vl - 1, 0));
            return;
        }
        boolean airborne = !onGround && !onLadder && !inWater && !inLava && !riding && !creative;
        double speed = lastPosition != null ? Math.sqrt(Math.pow(position.x - lastPosition.x, 2) + Math.pow(position.z - lastPosition.z, 2)) : 0;
        if (airborne && speed > 0.03) {
            if (hoverStartY == null) {
                hoverStartY = position.y;
                anticheatPlayer.put( "HoverA_StartY", hoverStartY);
            }
            hoverTicks++;
            if (Math.abs(position.y - hoverStartY) >= 2.0) {
                hoverTicks = 0;
                anticheatPlayer.put( "HoverA_StartY", position.y);
            }
        } else {
            hoverTicks = 0;
            anticheatPlayer.put( "HoverA_StartY", null);
        }
        if (hoverTicks >= 85) {
            hoverTicks = 0;
            anticheatPlayer.put( "HoverA_StartY", null);
            vl += 2;
            if (vl >= vlThreshold && now - lastAlert > cooldown) {
                anticheatPlayer.put( "HoverA_LastAlert", now);
                printFlag(anticheatPlayer, "NCP Hover", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put( "HoverA_Ticks", hoverTicks);
        anticheatPlayer.put( "HoverA_VL", vl);
    }

    void SpeedB(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "SpeedB_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "SpeedB_LastAlert", 0L);
        boolean inWater = Boolean.TRUE.equals(anticheatPlayer.get( "inWater"));
        boolean inLava = Boolean.TRUE.equals(anticheatPlayer.get( "inLava"));
        boolean onLadder = Boolean.TRUE.equals(anticheatPlayer.get( "onLadder"));
        boolean riding = anticheatPlayer.get( "riding") != null;
        boolean creative = Boolean.TRUE.equals(anticheatPlayer.get( "creative"));
        int lastHit = (int) anticheatPlayer.getOrDefault( "lastHitTick", 0);
        int ticksExisted = (int) anticheatPlayer.get( "ticksExisted");
        RavenVec3 position = (RavenVec3) anticheatPlayer.get( "position");
        List <RavenVec3 > positions = (List <RavenVec3 >) anticheatPlayer.get( "previousPositions");
        if (isAirborne(anticheatPlayer, position) && !inWater && !inLava && !onLadder && !riding && !creative && !(lastHit > 0 && ticksExisted - lastHit <= 10) && positions != null && positions.size() >= 20) {
            double distance = 0;
            for (int i = 0; i < positions.size() - 1; i++) {
                RavenVec3 current = positions.get(i);
                RavenVec3 previous = positions.get(i + 1);
                distance += Math.sqrt(Math.pow(current.x - previous.x, 2) + Math.pow(current.z - previous.z, 2));
            }
            double cap = 6.9;
            List<Object[]> effects = (List<Object[]>) anticheatPlayer.get("potionEffects");
            boolean slowed = false;
            if (effects != null) {
                for (Object[] effect : effects) {
                    if ((Integer) effect[0] == 1) cap *= 1.0 + ((Integer) effect[2] + 1) * 0.2;
                    else if ((Integer) effect[0] == 2) slowed = true;
                }
            }
            if (!slowed && distance > Math.max(cap * 1.12, 7.5)) {
                vl++;
                if (vl >= vlThreshold && now - lastAlert > cooldown) {
                    anticheatPlayer.put("SpeedB_LastAlert", now);
                    printFlag(anticheatPlayer, "Vulcan Speed", vl);
                }
            } else {
                vl = Math.max(vl - 1, 0);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("SpeedB_VL", vl);
    }

    void SprintB(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "SprintB_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "SprintB_LastAlert", 0L);
        boolean isSprinting = Boolean.TRUE.equals(anticheatPlayer.get( "sprinting"));
        boolean crouching = Boolean.TRUE.equals(anticheatPlayer.get( "crouching"));
        boolean onGround = Boolean.TRUE.equals(anticheatPlayer.get( "onGround"));
        boolean riding = anticheatPlayer.get( "riding") != null;
        RavenVec3 position = (RavenVec3) anticheatPlayer.get( "position");
        RavenVec3 lastPosition = (RavenVec3) anticheatPlayer.get( "lastPosition");
        double speed = Math.max(Math.abs(position.x - lastPosition.x), Math.abs(position.z - lastPosition.z));
        // Negativity Sneak "sneak-sprint": buffer 0.1 per matching tick, flag only
        // after 5+ occurrences. A single edge-case tick of sneak-sprint (e.g. shift
        // tapping on a block edge) is legit; a sustained pattern is not.
        double buffer = (Double) anticheatPlayer.getOrDefault("SprintB_Buffer", 0d);
        if (isSprinting && crouching && onGround && !riding && speed >= 0.1) {
            buffer += 0.1;
            if (buffer > 0.5) {
                buffer = 0;
                vl++;
                if (vl >= vlThreshold && now - lastAlert > cooldown) {
                    anticheatPlayer.put("SprintB_LastAlert", now);
                    printFlag(anticheatPlayer, "NCP Sprint", vl);
                }
            }
        } else {
            buffer = Math.max(buffer - 0.2, 0);
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("SprintB_Buffer", buffer);
        anticheatPlayer.put("SprintB_VL", vl);
    }

    void SwimA(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "SwimA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "SwimA_LastAlert", 0L);
        boolean inWater = Boolean.TRUE.equals(anticheatPlayer.get( "inWater"));
        boolean inLava = Boolean.TRUE.equals(anticheatPlayer.get( "inLava"));
        boolean riding = anticheatPlayer.get( "riding") != null;
        List <RavenVec3 > positions = (List <RavenVec3 >) anticheatPlayer.get( "previousPositions");
        if ((inWater || inLava) && !riding && positions != null && positions.size() >= 20) {
            double distance = 0;
            for (int i = 0; i < positions.size() - 1; i++) {
                RavenVec3 current = positions.get(i);
                RavenVec3 previous = positions.get(i + 1);
                distance += Math.sqrt(Math.pow(current.x - previous.x, 2) + Math.pow(current.z - previous.z, 2));
            }
            double cap = 3.2;
            List<Object[]> swimEffects = (List<Object[]>) anticheatPlayer.get("potionEffects");
            if (swimEffects != null) {
                for (Object[] effect : swimEffects) {
                    if ((Integer) effect[0] == 1) cap *= 1.0 + ((Integer) effect[2] + 1) * 0.2;
                }
            }
            if (distance > cap) {
                vl++;
                if (vl >= vlThreshold && now - lastAlert > cooldown) {
                    anticheatPlayer.put("SwimA_LastAlert", now);
                    printFlag(anticheatPlayer, "Grim Swim", vl);
                }
            } else {
                vl = Math.max(vl - 1, 0);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("SwimA_VL", vl);
    }

    void LadderA(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "LadderA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "LadderA_LastAlert", 0L);
        boolean onLadder = Boolean.TRUE.equals(anticheatPlayer.get( "onLadder"));
        boolean riding = anticheatPlayer.get( "riding") != null;
        boolean creative = Boolean.TRUE.equals(anticheatPlayer.get( "creative"));
        boolean inWater = Boolean.TRUE.equals(anticheatPlayer.get( "inWater"));
        boolean inLava = Boolean.TRUE.equals(anticheatPlayer.get( "inLava"));
        RavenVec3 position = (RavenVec3) anticheatPlayer.get( "position");
        RavenVec3 lastPosition = (RavenVec3) anticheatPlayer.get( "lastPosition");
        double deltaY = position.y - lastPosition.y;
        int climbTicks = (int) anticheatPlayer.getOrDefault( "LadderA_ClimbTicks", 0);
        // Negativity FastLadder gates: only consider real ladder climbing. Requires a
        // ladder stack (3+ ladder blocks below), a ladder above, and no jump boost /
        // speed III+ / liquid, which all legitimately accelerate vertical motion.
        boolean ladderStack = false;
        boolean ladderAbove = false;
        if (position != null) {
            int bx = (int) Math.floor(position.x);
            int by = (int) Math.floor(position.y);
            int bz = (int) Math.floor(position.z);
            int depth = 0;
            for (int i = 1; i <= 4; i++) {
                String blockBelow = world.getBlockAt(bx, by - i, bz).name;
                if (blockBelow != null && blockBelow.contains("ladder")) depth++;
            }
            ladderStack = depth >= 3;
            String above = world.getBlockAt(bx, by + 1, bz).name;
            ladderAbove = above != null && above.contains("ladder");
        }
        boolean jumpBoost = false;
        boolean speedBoost = false;
        List <Object[] > effects = (List <Object[] >) anticheatPlayer.get( "potionEffects");
        if (effects != null) {
            for (Object[] effect : effects) {
                int id = (Integer) effect[0];
                if (id == 8) jumpBoost = true;
                else if (id == 1 && (Integer) effect[2] >= 2) speedBoost = true;
            }
        }
        if (onLadder && !riding && !creative && !inWater && !inLava && !jumpBoost && !speedBoost
        && ladderStack && ladderAbove && deltaY > 0.23 && deltaY < 3.8) {
            climbTicks++;
            if (climbTicks >= 4) {
                vl++;
                if (vl >= vlThreshold && now - lastAlert > cooldown) {
                    anticheatPlayer.put("LadderA_LastAlert", now);
                    printFlag(anticheatPlayer, "NCP Ladder", vl);
                }
            }
        } else {
            climbTicks = 0;
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("LadderA_ClimbTicks", climbTicks);
        anticheatPlayer.put("LadderA_VL", vl);
    }

    void TowerA(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "TowerA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "TowerA_LastAlert", 0L);
        float pitch = (float) anticheatPlayer.get( "pitch");
        RavenItem lastSwingItem = (RavenItem) anticheatPlayer.get( "lastSwingItem");
        int lastSwing = (int) anticheatPlayer.getOrDefault( "lastSwingTick", 0);
        int ticksExisted = (int) anticheatPlayer.get( "ticksExisted");
        RavenVec3 position = (RavenVec3) anticheatPlayer.get( "position");
        RavenVec3 lastPosition = (RavenVec3) anticheatPlayer.get( "lastPosition");
        double deltaY = position.y - lastPosition.y;
        boolean holdingBlocks = lastSwingItem != null  && lastSwingItem.type.startsWith( "Block");
        boolean lookingDown = pitch  >= 70;
        int towerTicks = (int) anticheatPlayer.getOrDefault( "TowerA_TowerTicks", 0);
        if (lookingDown && holdingBlocks && ticksExisted - lastSwing <= 2 && deltaY > 0.2) {
            towerTicks++;
            if (towerTicks >= 6) {
                vl++;
                if (vl >= vlThreshold && now - lastAlert > cooldown) {
                    anticheatPlayer.put("TowerA_LastAlert", now);
                    printFlag(anticheatPlayer, "NCP Tower", vl);
                }
            }
        } else {
            towerTicks = 0;
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("TowerA_TowerTicks", towerTicks);
        anticheatPlayer.put("TowerA_VL", vl);
    }

    void FreecamA(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "FreecamA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "FreecamA_LastAlert", 0L);
        boolean riding = anticheatPlayer.get( "riding") != null;
        RavenVec3 position = (RavenVec3) anticheatPlayer.get( "position");
        RavenVec3 lastPosition = (RavenVec3) anticheatPlayer.get( "lastPosition");
        double moved = Math.abs(position.x - lastPosition.x) + Math.abs(position.y - lastPosition.y) + Math.abs(position.z - lastPosition.z);
        float yaw = (float) anticheatPlayer.get( "yaw");
        float prevYaw = (float) anticheatPlayer.get( "previousYaw");
        double yawDelta = Math.abs(yaw - prevYaw);
        if (yawDelta  > 180) yawDelta = 360 - yawDelta;
        int stillTicks = (int) anticheatPlayer.getOrDefault("FreecamA_StillTicks", 0);
        int rotateTicks = (int) anticheatPlayer.getOrDefault("FreecamA_RotateTicks", 0);
        if (!riding && moved < 1e-6) {
            stillTicks++;
            if (stillTicks >= 20 && yawDelta > 5) rotateTicks++;
            if (rotateTicks >= 15) {
                rotateTicks = 0;
                vl++;
                if (vl >= vlThreshold && now - lastAlert > cooldown) {
                    anticheatPlayer.put("FreecamA_LastAlert", now);
                    printFlag(anticheatPlayer, "AAC Freecam", vl);
                }
            }
        } else {
            stillTicks = 0;
            rotateTicks = 0;
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("FreecamA_StillTicks", stillTicks);
        anticheatPlayer.put("FreecamA_RotateTicks", rotateTicks);
        anticheatPlayer.put("FreecamA_VL", vl);
    }

    void VelocityC(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "VelocityC_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "VelocityC_LastAlert", 0L);
        int lastHit = (int) anticheatPlayer.getOrDefault( "lastHitTick", 0);
        int ticksExisted = (int) anticheatPlayer.get( "ticksExisted");
        boolean riding = anticheatPlayer.get( "riding") != null;
        boolean onLadder = Boolean.TRUE.equals(anticheatPlayer.get( "onLadder"));
        RavenVec3 position = (RavenVec3) anticheatPlayer.get( "position");
        RavenVec3 lastPosition = (RavenVec3) anticheatPlayer.get( "lastPosition");
        double deltaY = position.y - lastPosition.y;
        String feet = world.getBlockAt((int) Math.floor(position.x), (int) Math.floor(position.y), (int) Math.floor(position.z)).name;
        String below = world.getBlockAt((int) Math.floor(position.x), (int) Math.floor(position.y) - 1, (int) Math.floor(position.z)).name;
        boolean inWeb = feet.equals( "web") || below.equals( "web");
        int checkedTick = (int) anticheatPlayer.getOrDefault( "VelocityC_CheckedTick", 0);
        if (lastHit == ticksExisted) {
            anticheatPlayer.put("VelocityC_AirborneAtHit", isAirborne(anticheatPlayer, position));
            anticheatPlayer.put("VelocityC_DeltaYAtHit", position.y - lastPosition.y);
            anticheatPlayer.put("VelocityC_Passed", false);
            anticheatPlayer.put("VelocityC_CheckedTick", 0);
            vl = Math.max(vl - 1, 0);
            anticheatPlayer.put("VelocityC_VL", vl);
            return;
        }
        int sinceHit = lastHit > 0 ? ticksExisted - lastHit : 99;
        boolean airborneAtHit = Boolean.TRUE.equals(anticheatPlayer.get("VelocityC_AirborneAtHit"));
        if (sinceHit < 1 || sinceHit > 4 || !airborneAtHit || riding || onLadder || inWeb) {
            vl = Math.max(vl - 1, 0);
            anticheatPlayer.put("VelocityC_VL", vl);
            return;
        }
        double deltaYAtHit = (Double) anticheatPlayer.getOrDefault("VelocityC_DeltaYAtHit", 0d);
        double increase = deltaY - deltaYAtHit;
        if (increase > 0.1) anticheatPlayer.put("VelocityC_Passed", true);
        if (sinceHit >= 4 && checkedTick != lastHit) {
            anticheatPlayer.put("VelocityC_CheckedTick", lastHit);
            boolean passed = Boolean.TRUE.equals(anticheatPlayer.get("VelocityC_Passed"));
            if (!passed) {
                vl++;
                if (vl >= vlThreshold && now - lastAlert > cooldown) {
                    anticheatPlayer.put("VelocityC_LastAlert", now);
                    printFlag(anticheatPlayer, "Pika Velocity", vl);
                }
            } else {
                vl = Math.max(vl - 1, 0);
            }
        }
        anticheatPlayer.put("VelocityC_VL", vl);
    }

    void NukerA(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "NukerA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "NukerA_LastAlert", 0L);
        int ticksExisted = (int) anticheatPlayer.get( "ticksExisted");
        int lastSwing = (int) anticheatPlayer.getOrDefault( "lastSwingTick", 0);
        int lastHit = (int) anticheatPlayer.getOrDefault( "lastHitTick", 0);
        RavenVec3 position = (RavenVec3) anticheatPlayer.get( "position");
        RavenItem heldItem = (RavenItem) anticheatPlayer.get( "heldItem");
        boolean holdingBlock = heldItem != null  && heldItem.type.startsWith( "Block");
        if (lastHit > 0 && ticksExisted - lastHit <= 10) {
            vl = Math.max(vl - 1, 0);
            anticheatPlayer.put("NukerA_VL", vl);
            return;
        }
        int count = 0;
        int sustainedCount = 0;
        for (Object[] br : blockBreaks) {
            RavenVec3 blockPos = (RavenVec3) br[0];
            long time = (Long) br[1];
            long age = now - time;
            if (age > 1000 || age < 0) continue;
            if (Math.abs(blockPos.x - position.x) <= 5.0 && Math.abs(blockPos.z - position.z) <= 5.0 && Math.abs(blockPos.y - position.y) <= 3.0) {
                if (age <= 250) count++;
                sustainedCount++;
            }
        }
        // Negativity Nuker "packet": a sustained break rate above ~10 blocks/second
        // is impossible without a dig-speed enchant (efficiency). Nuker mods send
        // dig packets for every block in radius regardless of the held tool.
        boolean sustained = sustainedCount > 10 && efficiencyLevel(heldItem) == 0;
        if ((count >= 5 && !holdingBlock && ticksExisted - lastSwing <= 5) || sustained) {
            vl++;
            if (vl >= vlThreshold && now - lastAlert > cooldown) {
                anticheatPlayer.put("NukerA_LastAlert", now);
                printFlag(anticheatPlayer, "NCP Nuker", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("NukerA_VL", vl);
    }

    void BedNukerA(Map<String, Object> anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault("BedNukerA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault("BedNukerA_LastAlert", 0L);
        int ticksExisted = (int) anticheatPlayer.get("ticksExisted");
        int lastSwing = (int) anticheatPlayer.getOrDefault("lastSwingTick", 0);
        RavenVec3 position = (RavenVec3) anticheatPlayer.get("position");
        if (position == null) { anticheatPlayer.put("BedNukerA_VL", Math.max(vl - 1, 0)); return; }
        if (isPlacingNearby(anticheatPlayer)) { anticheatPlayer.put("BedNukerA_VL", Math.max(vl - 1, 0)); return; }
        if (isSameTeamAsLocal(anticheatPlayer)) { anticheatPlayer.put("BedNukerA_VL", Math.max(vl - 1, 0)); return; }
        // A bed is two horizontally adjacent blocks at the same Y. Bed nukers break
        // both halves within the same tick and swing exactly on the break ticks.
        RavenVec3 bedPos = null;
        long bedTime = 0L;
        for (Object[] br : blockBreaks) {
            if (!Boolean.TRUE.equals(br[2])) continue;
            RavenVec3 blockPos = (RavenVec3) br[0];
            long time = (Long) br[1];
            if (now - time > 150 || now - time < 0) continue;
            if (Math.abs(blockPos.x - position.x) > 6 || Math.abs(blockPos.z - position.z) > 6 || Math.abs(blockPos.y - position.y) > 3) continue;
            if (bedPos == null) { bedPos = blockPos; bedTime = time; continue; }
            // Two breaks within the same tick, same Y, horizontally adjacent -> bed halves
            boolean sameY = Math.abs(blockPos.y - bedPos.y) < 0.1;
            boolean adjacent = Math.abs(blockPos.x - bedPos.x) + Math.abs(blockPos.z - bedPos.z) == 1;
            boolean near = Math.abs(time - bedTime) <= 60;
            boolean synced = ticksExisted - lastSwing <= 2;
            // A nuker usually mines the defense around the bed first. Beds with a thin
            // defense (wool directly on the bed) leave no surrounding break, so repeat
            // same-tick double breaks accumulate as a fallback signal.
            boolean surrounded = hasSurroundingBreak(bedPos, bedTime) || hasSurroundingBreak(blockPos, time);
            int nearDoubles = (int) anticheatPlayer.getOrDefault("BedNukerA_NearDoubles", 0);
            if (surrounded) nearDoubles = 0;
            if (sameY && adjacent && near && synced && isMinerOfBlock(bedPos, anticheatPlayer) && isMinerOfBlock(blockPos, anticheatPlayer)) {
                if (surrounded || nearDoubles >= 2) {
                    vl++;
                    if (vl >= vlThreshold && now - lastAlert > cooldown) {
                        anticheatPlayer.put("BedNukerA_LastAlert", now);
                        printFlag(anticheatPlayer, "NCP BedNuker", vl);
                    }
                } else {
                    nearDoubles++;
                }
                anticheatPlayer.put("BedNukerA_NearDoubles", nearDoubles);
                anticheatPlayer.put("BedNukerA_VL", vl);
                return;
            }
        }
        anticheatPlayer.put("BedNukerA_NearDoubles", Math.max((int) anticheatPlayer.getOrDefault("BedNukerA_NearDoubles", 0) - 1, 0));
        anticheatPlayer.put("BedNukerA_VL", Math.max(vl - 1, 0));
    }

    void BedSnapA(Map<String, Object> anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault("BedSnapA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault("BedSnapA_LastAlert", 0L);
        int ticksExisted = (int) anticheatPlayer.get("ticksExisted");
        int lastSwing = (int) anticheatPlayer.getOrDefault("lastSwingTick", 0);
        float yaw = (float) anticheatPlayer.get("yaw");
        RavenVec3 position = (RavenVec3) anticheatPlayer.get("position");
        if (position == null) { anticheatPlayer.put("BedSnapA_VL", Math.max(vl - 1, 0)); return; }
        if (isPlacingNearby(anticheatPlayer)) { anticheatPlayer.put("BedSnapA_VL", Math.max(vl - 1, 0)); return; }
        if (isSameTeamAsLocal(anticheatPlayer)) { anticheatPlayer.put("BedSnapA_VL", Math.max(vl - 1, 0)); return; }
        // Bed nukers snap their yaw to within <0.5 degrees of every broken block.
        // Humans have several degrees of error per swing.
        int snapped = 0;
        int considered = 0;
        for (Object[] br : blockBreaks) {
            RavenVec3 blockPos = (RavenVec3) br[0];
            long time = (Long) br[1];
            if (now - time > 150 || now - time < 0) continue;
            if (Math.abs(blockPos.x - position.x) > 6 || Math.abs(blockPos.z - position.z) > 6 || Math.abs(blockPos.y - position.y) > 3) continue;
            if (!isMinerOfBlock(blockPos, anticheatPlayer)) continue;
            considered++;
            double angle = getFacingAngle(yaw, position, blockPos);
            if (Math.abs(angle) < 0.5) snapped++;
        }
        if (considered >= 3 && snapped >= 3 && ticksExisted - lastSwing <= 1) {
            vl++;
            if (vl >= vlThreshold && now - lastAlert > cooldown) {
                anticheatPlayer.put("BedSnapA_LastAlert", now);
                printFlag(anticheatPlayer, "Grim BedSnap", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("BedSnapA_VL", vl);
    }

    void BedNukerB(Map<String, Object> anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault("BedNukerB_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault("BedNukerB_LastAlert", 0L);
        int ticksExisted = (int) anticheatPlayer.get("ticksExisted");
        int lastSwing = (int) anticheatPlayer.getOrDefault("lastSwingTick", 0);
        RavenVec3 position = (RavenVec3) anticheatPlayer.get("position");
        List<Integer> swings = (List<Integer>) anticheatPlayer.get("swingTimes");
        if (position == null || swings == null || swings.size() < 12 || ticksExisted - lastSwing > 1) {
            anticheatPlayer.put("BedNukerB_VL", Math.max(vl - 1, 0));
            return;
        }
        if (isPlacingNearby(anticheatPlayer)) { anticheatPlayer.put("BedNukerB_VL", Math.max(vl - 1, 0)); return; }
        if (isSameTeamAsLocal(anticheatPlayer)) { anticheatPlayer.put("BedNukerB_VL", Math.max(vl - 1, 0)); return; }
        // Robotic mining: all swing intervals identical, and a block break happened
        // exactly on the most recent swing. Bed nukers swing at a constant interval.
        int n = swings.size();
        int expected = swings.get(n - 1) - swings.get(n - 2);
        boolean constant = expected > 0;
        for (int i = 1; i < n - 1; i++) {
            if (swings.get(i) - swings.get(i - 1) != expected) {
                constant = false;
                break;
            }
        }
        boolean brokeNear = false;
        for (Object[] br : blockBreaks) {
            RavenVec3 blockPos = (RavenVec3) br[0];
            long time = (Long) br[1];
            if (now - time <= 100 && Math.abs(blockPos.x - position.x) <= 3 && Math.abs(blockPos.z - position.z) <= 3 && Math.abs(blockPos.y - position.y) <= 2 && isMinerOfBlock(blockPos, anticheatPlayer)) {
                brokeNear = true;
                break;
            }
        }
        if (constant && brokeNear) {
            vl++;
            if (vl >= vlThreshold && now - lastAlert > cooldown) {
                anticheatPlayer.put("BedNukerB_LastAlert", now);
                printFlag(anticheatPlayer, "Karhu BedNuker", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("BedNukerB_VL", vl);
    }

    void FastPlaceA(Map<String, Object> anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault("FastPlaceA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault("FastPlaceA_LastAlert", 0L);
        int ticksExisted = (int) anticheatPlayer.get("ticksExisted");
        RavenVec3 position = (RavenVec3) anticheatPlayer.get("position");
        if (position == null) { anticheatPlayer.put("FastPlaceA_VL", Math.max(vl - 1, 0)); return; }
        // Vulcan FastPlace gate: placing blocks requires a block in hand. A fast
        // placement burst while holding a sword/rod/tool cannot place anything, so
        // flagging it is a false positive.
        RavenItem heldItem = (RavenItem) anticheatPlayer.get("heldItem");
        if (heldItem == null || !heldItem.isBlock) {
            anticheatPlayer.put("FastPlaceA_VL", Math.max(vl - 1, 0));
            return;
        }
        // Grim MultiPlace: vanilla enforces a 4-tick placement delay, so two blocks
        // placed within the same tick at different positions is impossible.
        boolean multiPlace = false;
        RavenVec3 firstSameTickPos = null;
        boolean firstSameTickSeen = false;
        // NCP FastPlace: full window (2s, limit 8) + short-term (1s, limit 6).
        int within1s = 0;
        int within2s = 0;
        // Negativity FastPlace "time": placements arrive at an impossibly consistent
        // pace. Track the gaps (in ticks) between consecutive placements; 4+ gaps
        // averaging < 3 ticks with the latest gap < 5 ticks is a constant-rate placer.
        List<Integer> placeGaps = (List<Integer>) anticheatPlayer.get("FastPlaceA_PlaceGaps");
        if (placeGaps == null) {
            placeGaps = new ArrayList<>();
            anticheatPlayer.put("FastPlaceA_PlaceGaps", placeGaps);
        }
        long lastPlaceTime = (long) anticheatPlayer.getOrDefault("FastPlaceA_LastPlaceTime", 0L);
        int lastPlaceTick = (int) anticheatPlayer.getOrDefault("FastPlaceA_LastPlaceTick", 0);
        boolean timeSignature = false;
        for (Object[] bp : blockPlaces) {
            RavenVec3 blockPos = (RavenVec3) bp[0];
            long time = (Long) bp[1];
            long age = now - time;
            if (age > 2000 || age < 0) continue;
            if (Math.abs(blockPos.x - position.x) > 4 || Math.abs(blockPos.z - position.z) > 4 || Math.abs(blockPos.y - position.y) > 3) continue;
            within2s++;
            if (age <= 1000) within1s++;
            if (age <= 50) {
                if (time != lastPlaceTime) {
                    if (lastPlaceTick > 0) {
                        int gap = ticksExisted - lastPlaceTick;
                        if (gap > 20) placeGaps.clear();
                        else if (gap >= 0) placeGaps.add(gap);
                        if (placeGaps.size() > 3) {
                            int sum = 0;
                            for (int g : placeGaps) sum += g;
                            double avgGap = sum / (double) placeGaps.size();
                            if (avgGap < 3.0 && gap < 5) timeSignature = true;
                        }
                    }
                    anticheatPlayer.put("FastPlaceA_LastPlaceTime", time);
                    anticheatPlayer.put("FastPlaceA_LastPlaceTick", ticksExisted);
                }
                if (firstSameTickSeen) {
                    if (blockPos.x != firstSameTickPos.x || blockPos.y != firstSameTickPos.y || blockPos.z != firstSameTickPos.z) {
                        multiPlace = true;
                    }
                } else {
                    firstSameTickPos = blockPos;
                    firstSameTickSeen = true;
                }
            }
        }
        int violation = 0;
        if (multiPlace) violation = 2;
        else if (timeSignature) violation = 2;
        else {
            int shortTerm = within1s - 6;
            int full = within2s - 8;
            if (shortTerm > violation) violation = shortTerm;
            if (full > violation) violation = full;
        }
        if (violation > 0) {
            vl += violation;
            if (vl >= vlThreshold && now - lastAlert > cooldown) {
                anticheatPlayer.put("FastPlaceA_LastAlert", now);
                printFlag(anticheatPlayer, "Vulcan FastPlace", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("FastPlaceA_VL", vl);
    }

    void TimerA(Map<String, Object> anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault("TimerA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault("TimerA_LastAlert", 0L);
        boolean riding = anticheatPlayer.get("riding") != null;
        boolean onGround = Boolean.TRUE.equals(anticheatPlayer.get("onGround"));
        boolean inWater = Boolean.TRUE.equals(anticheatPlayer.get("inWater"));
        boolean inLava = Boolean.TRUE.equals(anticheatPlayer.get("inLava"));
        boolean creative = Boolean.TRUE.equals(anticheatPlayer.get("creative"));
        List<RavenVec3> positions = (List<RavenVec3>) anticheatPlayer.get("previousPositions");
        if (positions == null || positions.size() < 15 || riding || !onGround || inWater || inLava || creative) {
            anticheatPlayer.put("TimerA_VL", Math.max(vl - 1, 0));
            return;
        }
        // Negative timer: the client ticks slower than the server (e.g. 16 TPS vs 20).
        // A slow client still moves its usual distance between its OWN ticks, so
        // distance-per-sample looks normal - what shrinks is the RATE of position
        // updates. We count position changes over the last 2 seconds: legit clients
        // report ~20/s, a 16 TPS client reports ~16/s. Ping shifts packets in time
        // but does not change the rate, so this is ping-immune.
        double total = 0;
        for (int i = 0; i < positions.size() - 1; i++) {
            total += positions.get(i).distanceTo(positions.get(i + 1));
        }
        double avgSpeed = total / (positions.size() - 1);
        List<Long> updateTimes = (List<Long>) anticheatPlayer.get("posUpdateTimes");
        if (updateTimes == null || updateTimes.size() < 25) {
            anticheatPlayer.put("TimerA_VL", Math.max(vl - 1, 0));
            return;
        }
        long now = this.now;
        int count = 0;
        for (Long t : updateTimes) {
            if (now - t <= 2000) count++;
        }
        double rate = count / 2.0;
        int slowTicks = (int) anticheatPlayer.getOrDefault("TimerA_SlowTicks", 0);
        if (avgSpeed > 0.15 && rate < 18.5) {
            slowTicks++;
            if (slowTicks >= 20) {
                vl++;
                if (vl >= vlThreshold && now - lastAlert > cooldown) {
                    anticheatPlayer.put("TimerA_LastAlert", now);
                    printFlag(anticheatPlayer, "Grim NegativeTimer", vl);
                }
            }
        } else {
            slowTicks = 0;
        }
        anticheatPlayer.put("TimerA_SlowTicks", slowTicks);
        anticheatPlayer.put("TimerA_VL", vl);
    }

    void TimerB(Map<String, Object> anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault("TimerB_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault("TimerB_LastAlert", 0L);
        boolean riding = anticheatPlayer.get("riding") != null;
        boolean onGround = Boolean.TRUE.equals(anticheatPlayer.get("onGround"));
        boolean inWater = Boolean.TRUE.equals(anticheatPlayer.get("inWater"));
        boolean inLava = Boolean.TRUE.equals(anticheatPlayer.get("inLava"));
        boolean creative = Boolean.TRUE.equals(anticheatPlayer.get("creative"));
        // Positive timer: the client ticks faster than the server (e.g. 24 TPS vs 20).
        // Just like the negative case the rate of position updates is ping-immune, so
        // we only count updates in the last 2 seconds. Legit clients report ~20/s.
        List<Long> updateTimes = (List<Long>) anticheatPlayer.get("posUpdateTimes");
        if (updateTimes == null || updateTimes.size() < 25 || riding || !onGround || inWater || inLava || creative) {
            anticheatPlayer.put("TimerB_VL", Math.max(vl - 1, 0));
            return;
        }
        long now = this.now;
        int count = 0;
        int burst = 0;
        for (Long t : updateTimes) {
            if (now - t <= 2000) count++;
            if (now - t <= 500) burst++;
        }
        double rate = count / 2.0;
        int fastTicks = (int) anticheatPlayer.getOrDefault("TimerB_FastTicks", 0);
        // NCP MorePackets bucket: >=15 position packets inside a 500ms window is a
        // timer burst (30+ TPS) even when the 2-second average looks normal.
        if (rate > 24.5 || burst >= 15) {
            fastTicks++;
            if (fastTicks >= 20) {
                vl++;
                if (vl >= vlThreshold && now - lastAlert > cooldown) {
                    anticheatPlayer.put("TimerB_LastAlert", now);
                    printFlag(anticheatPlayer, "Grim Timer", vl);
                }
            }
        } else {
            fastTicks = 0;
        }
        anticheatPlayer.put("TimerB_FastTicks", fastTicks);
        anticheatPlayer.put("TimerB_VL", vl);
    }

    boolean isBedwarsBlock(RavenItem item) {
        // Block materials actually used for bridging/building in Hypixel BedWars:
        // every wool color, glass, end stone, obsidian, wood planks/logs, sandstone,
        // ladders and clay. Anything else (dirt, stone, torches, TNT...) is not a
        // scaffold material.
        if (item == null || !item.isBlock || item.type == null) return false;
        return Set.of("wool", "glass", "stained_glass", "end_stone", "obsidian", "planks", "log",
                "sandstone", "red_sandstone", "ladder", "hardened_clay", "stained_hardened_clay").contains(item.name);
    }

    void LegitScaffA(Map<String, Object> anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault("LegitScaffA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault("LegitScaffA_LastAlert", 0L);
        int ticksExisted = (int) anticheatPlayer.getOrDefault("ticksExisted", 0);
        int lastOnGroundTick = (int) anticheatPlayer.getOrDefault("lastOnGroundTick", 0);
        RavenVec3 position = (RavenVec3) anticheatPlayer.get("position");
        if (position == null) { anticheatPlayer.put("LegitScaffA_VL", Math.max(vl - 1, 0)); return; }
        // Scaffold requires placing blocks, so only run while a BedWars build
        // material is in hand (wool, glass, end stone, obsidian, planks...).
        RavenItem heldItem = (RavenItem) anticheatPlayer.get("heldItem");
        if (!isBedwarsBlock(heldItem)) {
            anticheatPlayer.put("LegitScaffA_VL", Math.max(vl - 1, 0));
            return;
        }
        // Gate 1 - above void: only flag when there are at least 3 blocks of void
        // under the player. Grounded players are not bridging.
        if (voidBlocksBelow(position) < 3) {
            anticheatPlayer.put("LegitScaffA_VL", Math.max(vl - 1, 0));
            return;
        }
        // Gate 2 - bridging speed + is moving: only flag when the player is currently
        // moving at bridging speed (>= 0.05 blocks/tick, ~1 m/s). This filters out
        // players who bridged and then stopped, and slow consistent builders.
        List<RavenVec3> positions = (List<RavenVec3>) anticheatPlayer.get("previousPositions");
        if (positions == null || positions.size() < 6) {
            anticheatPlayer.put("LegitScaffA_VL", Math.max(vl - 1, 0));
            return;
        }
        int pairs = Math.min(positions.size() - 1, 10);
        double totalDist = 0;
        for (int i = 0; i < pairs; i++) {
            totalDist += positions.get(i).distanceTo(positions.get(i + 1));
        }
        double avgSpeed = totalDist / pairs;
        if (avgSpeed < 0.05) {
            anticheatPlayer.put("LegitScaffA_VL", Math.max(vl - 1, 0));
            return;
        }
        List<Object[]> placeHistory = (List<Object[]>) anticheatPlayer.get("placeHistory");
        long lastSneakOn = (long) anticheatPlayer.getOrDefault("lastSneakOnTime", 0L);
        long lastSneakOnDuration = (long) anticheatPlayer.getOrDefault("lastSneakOnDuration", 0L);
        List<Long> sneakToggles = (List<Long>) anticheatPlayer.get("sneakToggleTimes");
        boolean suspicious = false;
        if (placeHistory != null && !placeHistory.isEmpty()) {
            Object[] latest = placeHistory.get(placeHistory.size() - 1);
            long placeTime = (Long) latest[2];
            if (now - placeTime <= 150) {
                // Signature 1: sneak flick - sneak toggled ON within ~2 ticks of the
                // placement AND the previous sneak was an instant blip (<=75ms). Legit
                // scaffolders hold shift; only cheats blip it per block. Speedbridge
                // taps are 100ms+ so they do not match.
                long sinceSneakOn = placeTime - lastSneakOn;
                if (sinceSneakOn >= 0 && sinceSneakOn <= 100 && lastSneakOnDuration > 0 && lastSneakOnDuration <= 75) {
                    suspicious = true;
                }
                // Signature 2: shift spam - three+ sneak toggles within the placement
                // window (toggle spam while placing). Two toggles in 400ms is ordinary
                // shift tapping, three is suspicious.
                if (!suspicious && sneakToggles != null) {
                    int toggles = 0;
                    for (Long t : sneakToggles) {
                        if (placeTime - t <= 200 && t <= placeTime) toggles++;
                    }
                    if (toggles >= 3) suspicious = true;
                }
            }
        }
        // Signature 3: in-line placements with fast average gaps while never sneaking.
        // Vanilla requires sneaking to place under yourself; placing every <500ms in
        // a straight line without shift for 6+ seconds is scaffold behavior. The
        // player must also not have jumped in the last 20 ticks: sprint-jumping and
        // placing blocks under yourself mid-jump is legit building (jumps set
        // lastOnGroundTick constantly), while a flat no-sneak bridger never leaves
        // the ground.
        if (!suspicious && placeHistory != null && placeHistory.size() >= 6 && lastSneakOn > 0 && now - lastSneakOn > 6000
        && ticksExisted - lastOnGroundTick > 20) {
            double sum = 0;
            boolean oneLine = true;
            double firstX = (Double) placeHistory.get(0)[0];
            double firstZ = (Double) placeHistory.get(0)[1];
            for (int i = 1; i < placeHistory.size(); i++) {
                Object[] ph = placeHistory.get(i);
                sum += (Long) ph[2] - (Long) placeHistory.get(i - 1)[2];
                double x = (Double) ph[0];
                double z = (Double) ph[1];
                if (Math.abs(x - firstX) > 0.1 && Math.abs(z - firstZ) > 0.1) oneLine = false;
            }
            double avg = sum / (placeHistory.size() - 1);
            if (oneLine && avg < 500 && avg > 50) suspicious = true;
        }
        // Signature 5 (Negativity Scaffold "place-below"): placing a block directly
        // under your own feet (same X/Z, one below) while looking mostly horizontal
        // (pitch < 50) is a scaffold placement - legit players look down to place
        // under themselves (pitch >= 50) unless they are standing on the block.
        if (!suspicious && position != null) {
            float pitch = (float) anticheatPlayer.getOrDefault("pitch", 0f);
            if (Math.abs(pitch) < 50) {
                double px = Math.floor(position.x);
                double py = Math.floor(position.y);
                double pz = Math.floor(position.z);
                for (Object[] bp : blockPlaces) {
                    RavenVec3 blockPos = (RavenVec3) bp[0];
                    long placeTime = (Long) bp[1];
                    long age = now - placeTime;
                    if (age > 150 || age < 0) continue;
                    if (Math.floor(blockPos.x) == px && Math.floor(blockPos.y) == py - 1 && Math.floor(blockPos.z) == pz) {
                        suspicious = true;
                        break;
                    }
                }
            }
        }
        // Signature 4: duplicate rotation deltas on consecutive placements (Grim
        // DuplicateRotPlace). A human's yaw change between placements is never
        // identical to 1e-4 degrees; rotation modules that turn a fixed amount per
        // placed block reproduce the exact same delta every time. Requires a real
        // rotation (>2 degrees) so idle players do not match.
        if (!suspicious && placeHistory != null && placeHistory.size() >= 2) {
            Object[] latest = placeHistory.get(placeHistory.size() - 1);
            Object[] prev = placeHistory.get(placeHistory.size() - 2);
            long gap = (Long) latest[2] - (Long) prev[2];
            if (gap > 0 && gap <= 100) {
                float yaw = (float) anticheatPlayer.getOrDefault( "yaw", 0f);
                float prevYaw = (float) anticheatPlayer.getOrDefault( "previousYaw", 0f);
                double delta = Math.abs(yaw - prevYaw);
                if (delta > 180) delta = 360 - delta;
                Double lastDelta = (Double) anticheatPlayer.get( "LegitScaffA_LastPlaceDelta");
                Long lastPlaceTime = (Long) anticheatPlayer.get( "LegitScaffA_LastPlaceTime");
                if (lastDelta != null && lastPlaceTime != null && Math.abs(delta - lastDelta) < 1e-4 && delta > 2.0) {
                    suspicious = true;
                }
                anticheatPlayer.put( "LegitScaffA_LastPlaceDelta", delta);
                anticheatPlayer.put( "LegitScaffA_LastPlaceTime", latest[2]);
            }
        }
        // Signature 6 (Intave BlockRotation): pitch beyond 85 degrees (nearly straight
        // down) combined with placements faster than one per 400ms is a scaffold
        // rotation module - legit godbridgers cap around 80 degrees.
        if (!suspicious && placeHistory != null && placeHistory.size() >= 4) {
            float pitch = (float) anticheatPlayer.getOrDefault( "pitch", 0f);
            if (Math.abs(pitch) > 85) {
                double sum = 0;
                for (int i = 1; i < placeHistory.size(); i++) {
                    sum += (Long) placeHistory.get(i)[2] - (Long) placeHistory.get(i - 1)[2];
                }
                double avgInterval = sum / (placeHistory.size() - 1);
                if (avgInterval < 400) suspicious = true;
            }
        }
        // Signature 7 (Intave AngleSnap): yaw sitting within 0.08 degrees of a 45
        // degree multiple while rotating 60-300 degrees inside 5 ticks right around
        // a placement - a rotation module that steps in exact 45 degree increments.
        if (!suspicious) {
            float yaw = (float) anticheatPlayer.getOrDefault( "yaw", 0f);
            double mod45 = Math.abs(yaw % 45.0);
            if (mod45 > 22.5) mod45 = 45 - mod45;
            List<Float> yawHistory = (List<Float>) anticheatPlayer.get( "yawHistory");
            boolean recentPlace = placeHistory != null && !placeHistory.isEmpty() && now - (Long) placeHistory.get(placeHistory.size() - 1)[2] <= 1000;
            if (mod45 < 0.08 && recentPlace && yawHistory != null && yawHistory.size() >= 6) {
                double rotSum = 0;
                for (int i = 0; i < 5; i++) {
                    double d = Math.abs(yawHistory.get(i) - yawHistory.get(i + 1));
                    if (d > 180) d = 360 - d;
                    rotSum += d;
                }
                if (rotSum >= 60 && rotSum <= 300) suspicious = true;
            }
        }
        // Signature 8 (Intave Stability): placement intervals uniform down to a
        // sub-10ms standard deviation across 50 consecutive placements. Humans
        // bridge with 30-60ms+ jitter; a scheduler clicks at a fixed interval.
        if (!suspicious && placeHistory != null && !placeHistory.isEmpty()) {
            long newest = (Long) placeHistory.get(placeHistory.size() - 1)[2];
            long lastPlace = (Long) anticheatPlayer.getOrDefault("LegitScaffA_LastPlaceMs", 0L);
            if (newest > lastPlace) {
                List<Double> gaps = (List<Double>) anticheatPlayer.get("LegitScaffA_PlaceGaps");
                if (gaps == null) {
                    gaps = new ArrayList<>();
                    anticheatPlayer.put("LegitScaffA_PlaceGaps", gaps);
                }
                if (lastPlace > 0) {
                    gaps.add((double) (newest - lastPlace));
                    if (gaps.size() > 50) gaps.remove(0);
                }
                anticheatPlayer.put("LegitScaffA_LastPlaceMs", newest);
                if (gaps.size() >= 50) {
                    double sum = 0;
                    for (double g : gaps) sum += g;
                    double mean = sum / gaps.size();
                    double var = 0;
                    for (double g : gaps) var += (g - mean) * (g - mean);
                    double std = Math.sqrt(var / gaps.size());
                    if (std < 10) suspicious = true;
                    gaps.clear();
                }
            }
        }
        if (suspicious) {
            vl++;
            if (vl >= vlThreshold && now - lastAlert > cooldown) {
                anticheatPlayer.put("LegitScaffA_LastAlert", now);
                printFlag(anticheatPlayer, "Grim LegitScaff", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("LegitScaffA_VL", vl);
    }

    void BreakThroughA(Map<String, Object> anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault("BreakThroughA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault("BreakThroughA_LastAlert", 0L);
        int ticksExisted = (int) anticheatPlayer.get("ticksExisted");
        int lastSwing = (int) anticheatPlayer.getOrDefault("lastSwingTick", 0);
        RavenVec3 position = (RavenVec3) anticheatPlayer.get("position");
        if (position == null) { anticheatPlayer.put("BreakThroughA_VL", Math.max(vl - 1, 0)); return; }
        // Nukers / reach-through-wall: the broken block is behind another solid
        // block, so the eye ray to the broken block's center passes through a solid
        // block that is NOT the broken block itself.
        RavenVec3 eye = position.offset(0, 1.62, 0);
        for (Object[] br : blockBreaks) {
            RavenVec3 blockPos = (RavenVec3) br[0];
            long time = (Long) br[1];
            if (now - time > 150 || now - time < 0) continue;
            if (Math.abs(blockPos.x - position.x) > 6 || Math.abs(blockPos.z - position.z) > 6 || Math.abs(blockPos.y - position.y) > 4) continue;
            if (!isMinerOfBlock(blockPos, anticheatPlayer)) continue;
            RavenVec3 center = blockPos.offset(0.5, 0.5, 0.5);
            double dist = eye.distanceTo(center);
            if (dist < 1.0 || dist > 5.0) continue;
            int steps = Math.max((int) Math.ceil(dist * 2), 1);
            boolean blocked = false;
            for (int i = 1; i < steps; i++) {
                double t = (double) i / steps;
                double x = eye.x + (center.x - eye.x) * t;
                double y = eye.y + (center.y - eye.y) * t;
                double z = eye.z + (center.z - eye.z) * t;
                double d = Math.sqrt(Math.pow(x - center.x, 2) + Math.pow(y - center.y, 2) + Math.pow(z - center.z, 2));
                if (d < 0.8) break;
                String b = world.getBlockAt((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)).name;
                if (!isPassableBlock(b)) { blocked = true; break; }
            }
            if (blocked && ticksExisted - lastSwing <= 2) {
                vl++;
                if (vl >= vlThreshold && now - lastAlert > cooldown) {
                    anticheatPlayer.put("BreakThroughA_LastAlert", now);
                    printFlag(anticheatPlayer, "Grim BreakThrough", vl);
                }
                anticheatPlayer.put("BreakThroughA_VL", vl);
                return;
            }
        }
        anticheatPlayer.put("BreakThroughA_VL", Math.max(vl - 1, 0));
    }

    void LookAwayA(Map<String, Object> anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault("LookAwayA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault("LookAwayA_LastAlert", 0L);
        int ticksExisted = (int) anticheatPlayer.get("ticksExisted");
        int lastSwing = (int) anticheatPlayer.getOrDefault("lastSwingTick", 0);
        RavenVec3 position = (RavenVec3) anticheatPlayer.get("position");
        if (position == null) { anticheatPlayer.put("LookAwayA_VL", Math.max(vl - 1, 0)); return; }
        // Block nukers mine blocks while staring elsewhere (e.g. the sky). They break
        // blocks with zero look-at time: the player never looks at the broken block
        // for the entire window. A legit player who turns away mid-break still has
        // earlier samples pointing at the block, so this filters those out.
        List<Float> yawHistory = (List<Float>) anticheatPlayer.get("yawHistory");
        for (Object[] br : blockBreaks) {
            RavenVec3 blockPos = (RavenVec3) br[0];
            long time = (Long) br[1];
            if (now - time > 150 || now - time < 0) continue;
            if (Math.abs(blockPos.x - position.x) > 5 || Math.abs(blockPos.z - position.z) > 5 || Math.abs(blockPos.y - position.y) > 3) continue;
            if (!isMinerOfBlock(blockPos, anticheatPlayer)) continue;
            if (yawHistory == null || yawHistory.size() < 10) continue;
            int awaySamples = 0;
            for (Float y : yawHistory) {
                double angle = Math.abs(getFacingAngle(y, position, blockPos));
                if (angle > 180) angle = 360 - angle;
                if (angle > 40.0) awaySamples++;
            }
            if (awaySamples >= 16 && ticksExisted - lastSwing <= 2) {
                vl++;
                if (vl >= vlThreshold && now - lastAlert > cooldown) {
                    anticheatPlayer.put("LookAwayA_LastAlert", now);
                    printFlag(anticheatPlayer, "Grim LookAway", vl);
                }
                anticheatPlayer.put("LookAwayA_VL", vl);
                return;
            }
        }
        anticheatPlayer.put("LookAwayA_VL", Math.max(vl - 1, 0));
    }

    void AutoToolA(Map<String, Object> anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault("AutoToolA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault("AutoToolA_LastAlert", 0L);
        RavenVec3 position = (RavenVec3) anticheatPlayer.get("position");
        if (position == null) { anticheatPlayer.put("AutoToolA_VL", Math.max(vl - 1, 0)); return; }
        // AutoTool switches to the perfect tool the instant mining starts. A human
        // usually has the right tool out already or takes longer to switch.
        long lastSwitchTime = (long) anticheatPlayer.getOrDefault("lastItemChangeTime", 0L);
        RavenItem switchedTo = (RavenItem) anticheatPlayer.getOrDefault("lastItemChangedTo", null);
        for (Object[] br : blockBreaks) {
            RavenVec3 blockPos = (RavenVec3) br[0];
            long time = (Long) br[1];
            if (now - time > 300 || now - time < 0) continue;
            if (Math.abs(blockPos.x - position.x) > 6 || Math.abs(blockPos.z - position.z) > 6 || Math.abs(blockPos.y - position.y) > 3) continue;
            if (!isMinerOfBlock(blockPos, anticheatPlayer)) continue;
            Object[] entry = findMiningEntry(anticheatPlayer, blockPos);
            if (entry == null || (Integer) entry[3] < 2) continue;
            String blockName = (String) entry[1];
            long mineStart = (Long) entry[2];
            if (now - mineStart > 2500) continue;
            if (switchedTo == null) continue;
            // Signature 1 (switch ON): instant swap to the perfect tool as mining
            // starts. Humans usually have it out already or switch slower.
            if (isProperTool(blockName, switchedTo.name)) {
                long delta = Math.abs(lastSwitchTime - mineStart);
                if (delta <= 300 && lastSwitchTime <= time) {
                    vl++;
                    if (vl >= vlThreshold && now - lastAlert > cooldown) {
                        anticheatPlayer.put("AutoToolA_LastAlert", now);
                        printFlag(anticheatPlayer, "NCP AutoTool", vl);
                    }
                    anticheatPlayer.put("AutoToolA_VL", vl);
                    return;
                }
            }
            // Signature 2 (switch OFF): the moment the block stops being mined the
            // tool is swapped away within 150ms (3 ticks). Autotool restores its
            // previous slot instantly; humans leave the tool out a moment.
            if (!isProperTool(blockName, switchedTo.name)) {
                long sinceBreak = lastSwitchTime - time;
                if (sinceBreak >= 0 && sinceBreak <= 150) {
                    vl++;
                    if (vl >= vlThreshold && now - lastAlert > cooldown) {
                        anticheatPlayer.put("AutoToolA_LastAlert", now);
                        printFlag(anticheatPlayer, "NCP AutoTool", vl);
                    }
                    anticheatPlayer.put("AutoToolA_VL", vl);
                    return;
                }
            }
        }
        anticheatPlayer.put("AutoToolA_VL", Math.max(vl - 1, 0));
    }

    void FastMineA(Map<String, Object> anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault("FastMineA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault("FastMineA_LastAlert", 0L);
        RavenVec3 position = (RavenVec3) anticheatPlayer.get("position");
        if (position == null) { anticheatPlayer.put("FastMineA_VL", Math.max(vl - 1, 0)); return; }
        boolean riding = anticheatPlayer.get("riding") != null;
        boolean inWater = Boolean.TRUE.equals(anticheatPlayer.get("inWater"));
        boolean inLava = Boolean.TRUE.equals(anticheatPlayer.get("inLava"));
        List<Object[]> effects = (List<Object[]>) anticheatPlayer.get("potionEffects");
        boolean hasHaste = false;
        if (effects != null) {
            for (Object[] effect : effects) {
                if ((Integer) effect[0] == 3) { hasHaste = true; break; }
            }
        }
        RavenItem heldItem = (RavenItem) anticheatPlayer.get("heldItem");
        for (Object[] br : blockBreaks) {
            RavenVec3 blockPos = (RavenVec3) br[0];
            long time = (Long) br[1];
            if (now - time > 300 || now - time < 0) continue;
            if (Math.abs(blockPos.x - position.x) > 6 || Math.abs(blockPos.z - position.z) > 6 || Math.abs(blockPos.y - position.y) > 3) continue;
            if (!isMinerOfBlock(blockPos, anticheatPlayer)) continue;
            Object[] entry = findMiningEntry(anticheatPlayer, blockPos);
            if (entry == null || (Integer) entry[3] < 2) continue;
            String blockName = (String) entry[1];
            long mineStart = (Long) entry[2];
            if (now - mineStart > 2500) continue;
            double expected = expectedMineMs(blockName, heldItem != null ? heldItem.name : "", efficiencyLevel(heldItem), hasHaste);
            if (expected <= 0) continue;
            if (expected < 150) continue;
            double actual = (double) (time - mineStart);
            // Grim FastBreak balances instead of single-shot thresholds: small
            // deviations decay (x0.9), sustained cheating accumulates until the
            // balance passes 1000ms.
            long lastBreakTime = (Long) anticheatPlayer.getOrDefault("FastMineA_LastBreakTime", 0L);
            if (time <= lastBreakTime) continue;
            double delayBalance = (Double) anticheatPlayer.getOrDefault("FastMineA_DelayBalance", 0d);
            double breakBalance = (Double) anticheatPlayer.getOrDefault("FastMineA_BreakBalance", 0d);
            if (lastBreakTime > 0) {
                double breakDelay = (double) (time - lastBreakTime);
                if (breakDelay >= 275.0) delayBalance *= 0.9;
                else delayBalance += 300.0 - breakDelay;
            }
            double diff = expected - actual;
            if (diff < 25.0) breakBalance *= 0.9;
            else breakBalance += diff;
            double clamp = 2000.0;
            delayBalance = Math.min(Math.max(delayBalance, -clamp), clamp);
            breakBalance = Math.min(Math.max(breakBalance, -clamp), clamp);
            anticheatPlayer.put("FastMineA_LastBreakTime", time);
            anticheatPlayer.put("FastMineA_DelayBalance", delayBalance);
            anticheatPlayer.put("FastMineA_BreakBalance", breakBalance);
            if (delayBalance > 1000.0 || breakBalance > 1000.0) {
                vl++;
                if (vl >= vlThreshold && now - lastAlert > cooldown) {
                    anticheatPlayer.put("FastMineA_LastAlert", now);
                    printFlag(anticheatPlayer, "Grim FastMine", vl);
                }
                anticheatPlayer.put("FastMineA_VL", vl);
                return;
            }
        }
        anticheatPlayer.put("FastMineA_VL", Math.max(vl - 1, 0));
    }

    void GroundSpoofA(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "GroundSpoofA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "GroundSpoofA_LastAlert", 0L);
        boolean onGround = Boolean.TRUE.equals(anticheatPlayer.get( "onGround"));
        boolean riding = anticheatPlayer.get( "riding") != null;
        boolean creative = Boolean.TRUE.equals(anticheatPlayer.get( "creative"));
        boolean crouching = Boolean.TRUE.equals(anticheatPlayer.get( "crouching"));
        double distToGround = (Double) anticheatPlayer.getOrDefault( "distToGround", 0d);
        RavenVec3 position = (RavenVec3) anticheatPlayer.get( "position");
        RavenVec3 lastPosition = (RavenVec3) anticheatPlayer.get( "lastPosition");
        double speed = Math.sqrt(Math.pow(position.x - lastPosition.x, 2) + Math.pow(position.z - lastPosition.z, 2));
        if (onGround && !riding && !creative && !crouching && distToGround > 1.5 && speed > 0.04) {
            // Negativity GroundSpoof: standing on the edge of a block counts as
            // supported. Only flag when the player is fully unsupported mid-air.
            if (!isSupportedByBlockEdge(position)) {
                vl++;
                if (vl >= vlThreshold && now - lastAlert > cooldown) {
                    anticheatPlayer.put("GroundSpoofA_LastAlert", now);
                    printFlag(anticheatPlayer, "Grim GroundSpoof", vl);
                }
            } else {
                vl = Math.max(vl - 1, 0);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("GroundSpoofA_VL", vl);
    }

    boolean isSupportedByBlockEdge(RavenVec3 pos) {
        // Negativity GroundSpoof SUPPORTED_FACES: the player box extends 0.31/0.69
        // over the edge of a neighbor block below, so standing on an edge is solid.
        int bx = (int) Math.floor(pos.x);
        int by = (int) Math.floor(pos.y) - 1;
        int bz = (int) Math.floor(pos.z);
        if (world.getBlockAt(bx, by, bz).name != null && !world.getBlockAt(bx, by, bz).name.equals("air")) return true;
        double fracX = pos.x - Math.floor(pos.x);
        double fracZ = pos.z - Math.floor(pos.z);
        if (!world.getBlockAt(bx + 1, by, bz).name.equals("air") && fracX >= 0.69) return true;
        if (!world.getBlockAt(bx - 1, by, bz).name.equals("air") && fracX <= 0.31) return true;
        if (!world.getBlockAt(bx, by, bz + 1).name.equals("air") && fracZ >= 0.69) return true;
        if (!world.getBlockAt(bx, by, bz - 1).name.equals("air") && fracZ <= 0.31) return true;
        if (!world.getBlockAt(bx + 1, by, bz + 1).name.equals("air") && fracX >= 0.69 && fracZ >= 0.69) return true;
        if (!world.getBlockAt(bx - 1, by, bz + 1).name.equals("air") && fracX <= 0.31 && fracZ >= 0.69) return true;
        if (!world.getBlockAt(bx + 1, by, bz - 1).name.equals("air") && fracX >= 0.69 && fracZ <= 0.31) return true;
        if (!world.getBlockAt(bx - 1, by, bz - 1).name.equals("air") && fracX <= 0.31 && fracZ <= 0.31) return true;
        return false;
    }

    void AutoBlockB(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "AutoBlockB_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "AutoBlockB_LastAlert", 0L);
        RavenItem heldItem = (RavenItem) anticheatPlayer.get( "heldItem");
        List <Integer > blockSwings = (List <Integer >) anticheatPlayer.get( "blockSwingTimes");
        boolean holdingSword = Boolean.TRUE.equals(anticheatPlayer.get("blocking"));
        // Intave AutoBlock: a swing that lands while the sword is being used
        // (blocking). Vanilla cancels swings while using an item, so the overlap
        // itself is the cheat - 3+ overlaps inside the window is a held-block
        // autoblocker. Legit block-hitting alternates and never overlaps.
        if (holdingSword && blockSwings != null && blockSwings.size() >= 3) {
            vl += 2;
            if (vl >= vlThreshold && now - lastAlert > cooldown) {
                anticheatPlayer.put("AutoBlockB_LastAlert", now);
                printFlag(anticheatPlayer, "Intave AutoBlock", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("AutoBlockB_VL", vl);
    }

    boolean isReplay() {
        List<String> scoreboard = world.getScoreboard();
        if (scoreboard == null || scoreboard.isEmpty()) return false;
        String header = RavenText.strip(scoreboard.get(0));
        return "ATLAS".equals(header) ||"REPLAY".equals(header);
    }

    boolean holdingWeapon(RavenItem item) {
        return item != null && !item.type.startsWith("Block");
    }

    double getFacingAngle(float yaw, RavenVec3 from, RavenVec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        if (Math.abs(dx) < 1e-8 && Math.abs(dz) < 1e-8) return 0;
        float targetAngle = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
        float relative = targetAngle - yaw;
        relative = (relative % 360 + 360) % 360;
        if (relative > 180) relative -= 360;
        return relative;
    }

    double rayBoxDistance(RavenVec3 origin, double dx, double dy, double dz, double len, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double tmin = 0, tmax = len;
        if (Math.abs(dx) < 1e-10) {
            if (origin.x < minX || origin.x > maxX) return -1;
        } else {
            double t1 = (minX - origin.x) / dx;
            double t2 = (maxX - origin.x) / dx;
            if (t1 > t2) { double t = t1; t1 = t2; t2 = t; }
            tmin = Math.max(tmin, t1);
            tmax = Math.min(tmax, t2);
            if (tmin > tmax) return -1;
        }
        if (Math.abs(dy) < 1e-10) {
            if (origin.y < minY || origin.y > maxY) return -1;
        } else {
            double t1 = (minY - origin.y) / dy;
            double t2 = (maxY - origin.y) / dy;
            if (t1 > t2) { double t = t1; t1 = t2; t2 = t; }
            tmin = Math.max(tmin, t1);
            tmax = Math.min(tmax, t2);
            if (tmin > tmax) return -1;
        }
        if (Math.abs(dz) < 1e-10) {
            if (origin.z < minZ || origin.z > maxZ) return -1;
        } else {
            double t1 = (minZ - origin.z) / dz;
            double t2 = (maxZ - origin.z) / dz;
            if (t1 > t2) { double t = t1; t1 = t2; t2 = t; }
            tmin = Math.max(tmin, t1);
            tmax = Math.min(tmax, t2);
            if (tmin > tmax) return -1;
        }
        return tmin;
    }

    double gcdDivisor(double a, double b) {
        if (a == 0) return 0;
        if (a < b) { double t = a; a = b; b = t; }
        while (b > 0.0086) {
            double t = a - Math.floor(a / b) * b;
            a = b;
            b = t;
        }
        return a;
    }

    int solidBlocksBetween(RavenVec3 from, RavenVec3 to) {
        double distance = from.distanceTo(to);
        int steps = Math.max((int) Math.ceil(distance * 2), 1);
        int solid = 0;
        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            double x = from.x + (to.x - from.x) * t;
            double y = from.y + (to.y - from.y) * t;
            double z = from.z + (to.z - from.z) * t;
            String block = world.getBlockAt((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)).name;
            if (!isPassableBlock(block)) solid++;
        }
        return solid;
    }

    double getFacingAngle3D(float yaw, float pitch, RavenVec3 from, RavenVec3 to) {
        double[] target = getAnglesToTarget(new RavenVec3(from.x, from.y + 1.62, from.z), to);
        double yawDiff = Math.abs(target[0] - yaw);
        if (yawDiff > 180) yawDiff = 360 - yawDiff;
        double pitchDiff = Math.abs(target[1] - pitch);
        return Math.max(yawDiff, pitchDiff);
    }

    boolean isMinerOfBlock(RavenVec3 blockPos, Map<String, Object> anticheatPlayer) {
        RavenVec3 pos = (RavenVec3) anticheatPlayer.get("position");
        if (pos == null) return false;
        double myDist = pos.distanceTo(blockPos);
        if (myDist > 6.5) return false;
        RavenPlayer me = localPlayer;
        if (me != null && me.getSwingProgress() > 0) {
            RavenVec3 myPos = me.getPosition();
            if (myPos != null && myPos.distanceTo(blockPos) < myDist - 0.5) return false;
        }
        boolean closest = true;
        for (Map.Entry<RavenPlayer, Map<String, Object>> e : anticheatPlayers.entrySet()) {
            if (e.getValue() == anticheatPlayer) continue;
            RavenVec3 o = (RavenVec3) e.getValue().get("position");
            if (o != null && o.distanceTo(blockPos) < myDist - 0.5) closest = false;
        }
        if (closest) return true;
        float yaw = (float) anticheatPlayer.getOrDefault("yaw", 0f);
        float pitch = (float) anticheatPlayer.getOrDefault("pitch", 0f);
        return getFacingAngle3D(yaw, pitch, pos, blockPos) < 30.0;
    }

    boolean hasSurroundingBreak(RavenVec3 bedPos, long time) {
        for (Object[] br : blockBreaks) {
            RavenVec3 blockPos = (RavenVec3) br[0];
            long t = (Long) br[1];
            if (t >= time || time - t > 1500) continue;
            if (Math.abs(blockPos.y - bedPos.y) > 2) continue;
            double dx = Math.abs(blockPos.x - bedPos.x);
            double dz = Math.abs(blockPos.z - bedPos.z);
            if (dx <= 2.5 && dz <= 2.5 && !(dx <= 1.1 && dz <= 1.1)) return true;
        }
        return false;
    }

    boolean isSuspectAimCheck(String check) {
        // Checks that run on the SUSPECT's own data and detect attack/aim behavior.
        // They only flag when the suspect holds a melee weapon and is looking at a
        // real player (context gate in the dispatch loop).
        if (check.equals("NCP KillAura") || check.equals("Vulcan KillAura")) return true;
        if (check.equals("Grim Aim") || check.equals("Vulcan Aim") || check.equals("Karhu SmoothAim") || check.equals("Matrix Angle")) return true;
        if (check.equals("Intave Heuristic") || check.equals("Grim SimAim") || check.equals("Intave Rhythm")) return true;
        if (check.equals("Grim AimGCD") || check.equals("Vulcan RotationSpeed") || check.equals("Intave GCD") || check.equals("Vulcan DivisorY")) return true;
        if (check.equals("Vulcan AimFlick") || check.equals("Karhu PitchLock") || check.equals("Matrix SilentAim")) return true;
        if (check.equals("Grim AimAccel") || check.equals("NCP AimSnap") || check.equals("Karhu AimTracking")) return true;
        if (check.equals("Karhu FastHit") || check.equals("Vulcan Criticals")) return true;
        if (check.equals("Intave ClickStats") || check.equals("Intave PreAttack") || check.equals("Intave StdDev") || check.equals("Intave Reset")) return true;
        return false;
    }

    boolean isMeleeWeapon(RavenItem item) {
        // Sword / axe / pickaxe / shovel / hoe / stick (Hypixel BedWars knockback
        // stick) all deal melee damage in 1.8. Bows, fishing rods, blocks, food and
        // tools like flint&steel do not.
        if (item == null || item.type == null) return false;
        return item.type.contains("Sword") || item.type.contains("Axe") || item.type.contains("Pickaxe")
        || item.type.contains("Spade") || item.type.contains("Hoe") || item.type.contains("Stick");
    }

    boolean isLookingAtPlayer(Map<String, Object> p) {
        // Context gate: is this player's look direction aimed near a REAL player
        // (not a bot per the anti-bot heuristic, not himself) within combat range?
        // This excludes swinging/turning at walls, the void, or NPCs.
        RavenVec3 position = (RavenVec3) p.get("position");
        if (position == null) return false;
        float yaw = (float) p.getOrDefault("yaw", 0f);
        float pitch = (float) p.getOrDefault("pitch", 0f);
        double radYaw = Math.toRadians(yaw);
        double radPitch = Math.toRadians(pitch);
        double dx = -Math.sin(radYaw) * Math.cos(radPitch);
        double dy = -Math.sin(radPitch);
        double dz = Math.cos(radYaw) * Math.cos(radPitch);
        RavenVec3 eye = position.offset(0, 1.62, 0);
        boolean antiBot = true;
        for (Map.Entry<RavenPlayer, Map<String, Object>> entry : anticheatPlayers.entrySet()) {
            Map<String, Object> other = entry.getValue();
            if (other == null || other == p) continue;
            if (antiBot && isBot(other)) continue;
            RavenVec3 otherPos = (RavenVec3) other.get("position");
            if (otherPos == null) continue;
            if (eye.distanceTo(otherPos) > 8.0) continue;
            double tx = otherPos.x - eye.x;
            double ty = otherPos.y + 1.0 - eye.y;
            double tz = otherPos.z - eye.z;
            double len = Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (len < 1e-6) continue;
            double dot = (tx * dx + ty * dy + tz * dz) / len;
            double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
            if (angle < 35.0) return true;
        }
        return false;
    }

    void AutoClickerA(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "AutoClickerA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "AutoClickerA_LastAlert", 0L);
        boolean riding = anticheatPlayer.get( "riding") != null;
        RavenItem heldItem = (RavenItem) anticheatPlayer.get( "heldItem");
        List <Integer > swings = (List <Integer >) anticheatPlayer.get( "swingTimes");
        int ticksExisted = (int) anticheatPlayer.get( "ticksExisted");
        if (holdingWeapon(heldItem) && !riding && swings != null && swings.size() >= 5) {
            // NCP AttackFrequency: score against cumulative bucket limits. The
            // violation is the worst excess across all windows (0.5s..8s).
            int[] windows = {10, 20, 40, 80, 160};
            int[] limits = {8, 15, 30, 60, 95};
            int maxVL = 0;
            for (int w = 0; w < windows.length; w++) {
                int count = 0;
                for (int tick : swings) {
                    if (ticksExisted - tick <= windows[w]) count++;
                }
                int over = count - limits[w];
                if (over > maxVL) maxVL = over;
            }
            if (maxVL > 0) {
                vl += maxVL;
                if (vl >= vlThreshold && now - lastAlert > cooldown) {
                    anticheatPlayer.put("AutoClickerA_LastAlert", now);
                    printFlag(anticheatPlayer, "NCP AutoClicker", vl);
                }
            } else {
                vl = Math.max(vl - 1, 0);
            }
            // NCP AttackFrequency, attack-side: the same bucket limits but on
            // ATTRIBUTED hits (8/15/30/60/95 per 0.5/1/2/4/8s). Killaura emits hit
            // bursts far above any legit sword exchange (~2.5 CPS sustained).
            List<Integer> attackTimes = (List<Integer>) anticheatPlayer.getOrDefault("AutoClickerA_AttackTimes", null);
            if (attackTimes == null) {
                attackTimes = new ArrayList<>();
                anticheatPlayer.put("AutoClickerA_AttackTimes", attackTimes);
            }
            boolean attackedTick = false;
            for (Map.Entry<RavenPlayer, Map<String, Object>> entry : anticheatPlayers.entrySet()) {
                Map<String, Object> v = entry.getValue();
                if (v == null || v == anticheatPlayer) continue;
                if ((int) v.getOrDefault("lastHitTick", 0) == ticksExisted && findRecentAttacker(v, ticksExisted) == anticheatPlayer) {
                    attackedTick = true;
                    break;
                }
            }
            if (attackedTick) {
                attackTimes.add(ticksExisted);
                if (attackTimes.size() > 320) attackTimes.remove(0);
                int attackMaxVL = 0;
                for (int w = 0; w < windows.length; w++) {
                    int count = 0;
                    for (int tick : attackTimes) {
                        if (ticksExisted - tick <= windows[w]) count++;
                    }
                    int over = count - limits[w];
                    if (over > attackMaxVL) attackMaxVL = over;
                }
                if (attackMaxVL > 0) {
                    vl += attackMaxVL;
                    if (vl >= vlThreshold && now - lastAlert > cooldown) {
                        anticheatPlayer.put("AutoClickerA_LastAlert", now);
                        printFlag(anticheatPlayer, "NCP AutoClicker", vl);
                    }
                }
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        // Matrix timing module (check_timing): an autoclicker emits click intervals
        // that repeat (one interval value dominating); humans jitter. Over 10+ gaps
        // at >= 6.7 CPS, a single interval making up 75%+ of all gaps is not human.
        // The signal feeds the same flag so it only fires alongside real clicking.
        List<Integer> gaps = new ArrayList<>();
        if (swings != null) {
            for (int i = 1; i < swings.size(); i++) {
                int g = swings.get(i) - swings.get(i - 1);
                if (g > 0 && g <= 20) gaps.add(g);
            }
        }
        int timingGain = 0;
        if (gaps.size() >= 10) {
            double avgGap = 0;
            for (int g : gaps) avgGap += g;
            avgGap /= gaps.size();
            if (avgGap <= 3.0) {
                Map<Integer, Integer> freq = new HashMap<>();
                int modeCount = 0;
                for (int g : gaps) {
                    int c = freq.getOrDefault(g, 0) + 1;
                    freq.put(g, c);
                    if (c > modeCount) modeCount = c;
                }
                if ((double) modeCount / gaps.size() >= 0.75) timingGain = 1;
            }
        }
        if (timingGain > 0) {
            int timingVl = (int) anticheatPlayer.getOrDefault("AutoClickerA_TimingVL", 0);
            timingVl += timingGain;
            anticheatPlayer.put("AutoClickerA_TimingVL", timingVl);
            if (timingVl >= 5 && now - lastAlert > cooldown) {
                anticheatPlayer.put("AutoClickerA_LastAlert", now);
                printFlag(anticheatPlayer, "NCP AutoClicker", vl + timingVl);
            }
        } else {
            anticheatPlayer.put("AutoClickerA_TimingVL", Math.max((int) anticheatPlayer.getOrDefault("AutoClickerA_TimingVL", 0) - 1, 0));
        }
        anticheatPlayer.put("AutoClickerA_VL", vl);
    }

    void AutoClickerB(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "AutoClickerB_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "AutoClickerB_LastAlert", 0L);
        boolean riding = anticheatPlayer.get( "riding") != null;
        RavenItem heldItem = (RavenItem) anticheatPlayer.get( "heldItem");
        List <Integer > swings = (List <Integer >) anticheatPlayer.get( "swingTimes");
        if (holdingWeapon(heldItem) && !riding && swings != null && swings.size() >= 12) {
            int min = 99, max = 0;
            for (int i = 1; i < swings.size(); i++) {
                int interval = swings.get(i - 1) - swings.get(i);
                if (interval < min) min = interval;
                if (interval > max) max = interval;
            }
            if (min == max) {
                vl++;
                if (vl >= vlThreshold && now - lastAlert > cooldown) {
                    anticheatPlayer.put("AutoClickerB_LastAlert", now);
                    printFlag(anticheatPlayer, "Karhu AutoClicker", vl);
                }
            } else {
                vl = Math.max(vl - 1, 0);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("AutoClickerB_VL", vl);
    }

    void AimA(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "AimA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "AimA_LastAlert", 0L);
        boolean riding = anticheatPlayer.get( "riding") != null;
        RavenItem heldItem = (RavenItem) anticheatPlayer.get( "heldItem");
        int ticksExisted = (int) anticheatPlayer.get( "ticksExisted");
        int lastSwing = (int) anticheatPlayer.getOrDefault( "lastSwingTick", 0);
        float yaw = (float) anticheatPlayer.get( "yaw");
        float pitch = (float) anticheatPlayer.get( "pitch");
        float prevYaw = (float) anticheatPlayer.get( "previousYaw");
        float prevPitch = (float) anticheatPlayer.get( "previousPitch");
        RavenVec3 position = (RavenVec3) anticheatPlayer.get( "position");
        boolean staticRotation = yaw == prevYaw  && pitch == prevPitch;
        boolean combat = holdingWeapon(heldItem)  && !riding  && ticksExisted - lastSwing  <= 20;
        RavenVec3 followed = null;
        if (combat && staticRotation && !isReplay()) {
            double bestDist = 5.0;
            for (Map.Entry<RavenPlayer, Map<String, Object>> entry : anticheatPlayers.entrySet()) {
                if (entry.getValue() == anticheatPlayer) continue;
                RavenVec3 otherPos = (RavenVec3) entry.getValue().get("position");
                if (otherPos == null) continue;
                double d = position.distanceTo(otherPos);
                if (d < bestDist) {
                    bestDist = d;
                    followed = otherPos;
                }
            }
        }
        int lockTicks = (int) anticheatPlayer.getOrDefault("AimA_LockTicks", 0);
        if (followed != null) {
            RavenVec3 lastFollowed = (RavenVec3) anticheatPlayer.getOrDefault("AimA_LastFollowed", null);
            if (lastFollowed != null && lastFollowed.distanceTo(followed) > 0.02) lockTicks++;
            else if (lastFollowed != null) lockTicks = Math.max(lockTicks - 1, 0);
            anticheatPlayer.put("AimA_LastFollowed", followed);
        } else {
            lockTicks = Math.max(lockTicks - 2, 0);
        }
        if (lockTicks >= 15) {
            lockTicks = 0;
            vl++;
            if (vl >= vlThreshold && now - lastAlert > cooldown) {
                anticheatPlayer.put("AimA_LastAlert", now);
                printFlag(anticheatPlayer, "Grim Aim", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("AimA_LockTicks", lockTicks);
        anticheatPlayer.put("AimA_VL", vl);
    }

    void AimB(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "AimB_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "AimB_LastAlert", 0L);
        boolean riding = anticheatPlayer.get( "riding") != null;
        RavenItem heldItem = (RavenItem) anticheatPlayer.get( "heldItem");
        int ticksExisted = (int) anticheatPlayer.get( "ticksExisted");
        int lastSwing = (int) anticheatPlayer.getOrDefault( "lastSwingTick", 0);
        float yaw = (float) anticheatPlayer.get( "yaw");
        float prevYaw = (float) anticheatPlayer.get( "previousYaw");
        RavenVec3 position = (RavenVec3) anticheatPlayer.get( "position");
        double yawDelta = Math.abs(yaw - prevYaw);
        if (yawDelta  > 180) yawDelta = 360 - yawDelta;
        boolean targetNearby = false;
        for (Map.Entry<RavenPlayer, Map<String, Object>> entry : anticheatPlayers.entrySet()) {
            if (entry.getValue() == anticheatPlayer) continue;
            RavenVec3 otherPos = (RavenVec3) entry.getValue().get("position");
            if (otherPos != null && position.distanceTo(otherPos) < 5.0) {
                targetNearby = true;
                break;
            }
        }
        if (holdingWeapon(heldItem) && !riding && !isReplay() && targetNearby && ticksExisted - lastSwing <= 2 && yawDelta >= 45) {
            vl++;
            if (vl >= vlThreshold && now - lastAlert > cooldown) {
                anticheatPlayer.put("AimB_LastAlert", now);
                printFlag(anticheatPlayer, "Vulcan Aim", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("AimB_VL", vl);
    }

    void AngleA(Map<String, Object> anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault("AngleA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault("AngleA_LastAlert", 0L);
        RavenItem heldItem = (RavenItem) anticheatPlayer.get("heldItem");
        int ticksExisted = (int) anticheatPlayer.get("ticksExisted");
        int lastSwing = (int) anticheatPlayer.getOrDefault("lastSwingTick", 0);
        float pitch = (float) anticheatPlayer.get("pitch");
        if (holdingWeapon(heldItem) && !isReplay() && ticksExisted - lastSwing <= 2 && Math.abs(pitch + 90f) < 0.02f) {
            vl++;
            if (vl >= vlThreshold && now - lastAlert > cooldown) {
                anticheatPlayer.put("AngleA_LastAlert", now);
                printFlag(anticheatPlayer, "Matrix Angle", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("AngleA_VL", vl);
    }

    void ReachB(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "ReachB_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "ReachB_LastAlert", 0L);
        int lastHit = (int) anticheatPlayer.getOrDefault( "lastHitTick", 0);
        int ticksExisted = (int) anticheatPlayer.get( "ticksExisted");
        if (lastHit != ticksExisted) {
            anticheatPlayer.put( "ReachB_VL", Math.max(vl - 1, 0));
            return;
        }
        boolean burning = Boolean.TRUE.equals(anticheatPlayer.get( "burning"));
        boolean inWater = Boolean.TRUE.equals(anticheatPlayer.get( "inWater"));
        boolean inLava = Boolean.TRUE.equals(anticheatPlayer.get( "inLava"));
        boolean riding = anticheatPlayer.get( "riding") != null;
        if (burning || inWater || inLava || riding) {
            anticheatPlayer.put( "ReachB_VL", Math.max(vl - 1, 0));
            return;
        }
        RavenPlayer victim = (RavenPlayer) anticheatPlayer.get( "entity");
        RavenVec3 victimPos = (RavenVec3) anticheatPlayer.get( "position");
        Map <String, Object > attacker = null;
        double attackerDistance = 6.5;
        for (Map.Entry <RavenPlayer, Map <String, Object > > entry : anticheatPlayers.entrySet()) {
            if (entry.getKey() == victim) continue;
            Map <String, Object > other = entry.getValue();
            if (other == null || other == anticheatPlayer) continue;
            int lastSwing = (int) other.getOrDefault( "lastSwingTick", 0);
            if (ticksExisted - lastSwing  > 2) continue;
            RavenItem otherHeld = (RavenItem) other.get("heldItem");
            // Aim context gate: only attribute attacks from players holding a melee
            // weapon and looking at a real player (not a bot).
            if (!isMeleeWeapon(otherHeld) || !isLookingAtPlayer(other)) continue;
            RavenVec3 otherPos = (RavenVec3) other.get( "position");
            if (otherPos == null) continue;
            double distance = victimPos.distanceTo(otherPos);
            if (distance  < attackerDistance) {
                attackerDistance = distance;
                attacker = other;
            }
        }
        if (attacker != null) {
            RavenVec3 attackerPos = (RavenVec3) attacker.get( "position");
            RavenVec3 eye = attackerPos.offset(0, 1.35, 0);
            RavenVec3 body = victimPos.offset(0, 0.9, 0);
            if (solidBlocksBetween(eye, body)  >= 3) {
                vl++;
                if (vl  >= vlThreshold  && now - lastAlert  > cooldown) {
                    anticheatPlayer.put( "ReachB_LastAlert", now);
                    attacker.put("ReachB_FlagTime", now);
                    printFlag(attacker,  "AAC Reach", vl);
                }
            } else {
                vl = Math.max(vl - 1, 0);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put( "ReachB_VL", vl);
    }

    void KillAuraB(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "KillAuraB_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "KillAuraB_LastAlert", 0L);
        RavenItem heldItem = (RavenItem) anticheatPlayer.get( "heldItem");
        int ticksExisted = (int) anticheatPlayer.get( "ticksExisted");
        int lastSwing = (int) anticheatPlayer.getOrDefault( "lastSwingTick", 0);
        boolean riding = anticheatPlayer.get( "riding") != null;
        float yaw = (float) anticheatPlayer.get( "yaw");
        RavenVec3 position = (RavenVec3) anticheatPlayer.get( "position");
        RavenVec3 nearest = null;
        double bestDist = 8.0;
        for (Map.Entry<RavenPlayer, Map<String, Object>> entry : anticheatPlayers.entrySet()) {
            if (entry.getValue() == anticheatPlayer) continue;
            RavenVec3 otherPos = (RavenVec3) entry.getValue().get("position");
            if (otherPos == null) continue;
            double d = position.distanceTo(otherPos);
            if (d < bestDist) {
                bestDist = d;
                nearest = otherPos;
            }
        }
        int offAim = (int) anticheatPlayer.getOrDefault("KillAuraB_OffAim", 0);
        if (holdingWeapon(heldItem) && !riding && !isReplay() && ticksExisted - lastSwing <= 1 && nearest != null) {
            double angle = getFacingAngle(yaw, position, nearest);
            if (Math.abs(angle) > 110) offAim++;
            else offAim = Math.max(offAim - 1, 0);
        } else {
            offAim = Math.max(offAim - 1, 0);
        }
        // Matrix rotation check (type_a/b + miss_player_hitbox): on each ATTRIBUTED
        // hit the suspect's look must be on the victim's hitbox. Silent-aim and
        // angle killaura hit while looking away; five consecutive off-aim hits at
        // >30 degrees of look error is beyond human aiming.
        int hitOffStreak = (int) anticheatPlayer.getOrDefault("KillAuraB_HitOffAim", 0);
        if (position != null && !riding && !isReplay()) {
            boolean hitOffAim = false;
            for (Map.Entry<RavenPlayer, Map<String, Object>> entry : anticheatPlayers.entrySet()) {
                Map<String, Object> v = entry.getValue();
                if (v == null || v == anticheatPlayer) continue;
                if ((int) v.getOrDefault("lastHitTick", 0) != ticksExisted) continue;
                if (findRecentAttacker(v, ticksExisted) != anticheatPlayer) continue;
                RavenVec3 vPos = (RavenVec3) v.get("position");
                if (vPos == null) break;
                RavenVec3 eye = position.offset(0, 1.62, 0);
                RavenVec3 center = vPos.offset(0, 0.9, 0);
                double[] required = getAnglesToTarget(eye, center);
                double errYaw = Math.abs(yaw - required[0]);
                if (errYaw > 180) errYaw = 360 - errYaw;
                double errPitch = Math.abs((float) anticheatPlayer.get("pitch") - required[1]);
                if (errYaw > 30.0 || errPitch > 30.0) hitOffAim = true;
                break;
            }
            if (hitOffAim) hitOffStreak++;
            else hitOffStreak = Math.max(hitOffStreak - 1, 0);
        } else {
            hitOffStreak = Math.max(hitOffStreak - 1, 0);
        }
        int violation = 0;
        if (offAim >= 7) {
            offAim = 0;
            violation += 1;
        }
        if (hitOffStreak >= 5) {
            hitOffStreak = 0;
            violation += 2;
        }
        if (violation > 0) {
            vl += violation;
            if (vl >= vlThreshold && now - lastAlert > cooldown) {
                anticheatPlayer.put("KillAuraB_LastAlert", now);
                printFlag(anticheatPlayer, "Vulcan KillAura", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("KillAuraB_OffAim", offAim);
        anticheatPlayer.put("KillAuraB_HitOffAim", hitOffStreak);
        anticheatPlayer.put("KillAuraB_VL", vl);
    }

    void NoSwingA(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "NoSwingA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "NoSwingA_LastAlert", 0L);
        int lastHit = (int) anticheatPlayer.getOrDefault( "lastHitTick", 0);
        int ticksExisted = (int) anticheatPlayer.get( "ticksExisted");
        if (lastHit != ticksExisted) {
            anticheatPlayer.put( "NoSwingA_VL", Math.max(vl - 1, 0));
            return;
        }
        boolean burning = Boolean.TRUE.equals(anticheatPlayer.get( "burning"));
        boolean inWater = Boolean.TRUE.equals(anticheatPlayer.get( "inWater"));
        boolean inLava = Boolean.TRUE.equals(anticheatPlayer.get( "inLava"));
        boolean riding = anticheatPlayer.get( "riding") != null;
        boolean creative = Boolean.TRUE.equals(anticheatPlayer.get( "creative"));
        if (burning || inWater || inLava || riding || creative) {
            anticheatPlayer.put( "NoSwingA_VL", Math.max(vl - 1, 0));
            return;
        }
        RavenPlayer victim = (RavenPlayer) anticheatPlayer.get( "entity");
        RavenVec3 victimPos = (RavenVec3) anticheatPlayer.get( "position");
        Map <String, Object > suspect = null;
        double suspectDistance = 4.2;
        for (Map.Entry<RavenPlayer, Map<String, Object>> entry : anticheatPlayers.entrySet()) {
            if (entry.getKey() == victim) continue;
            Map<String, Object> other = entry.getValue();
            if (other == null || other == anticheatPlayer) continue;
            RavenItem otherHeld = (RavenItem) other.get("heldItem");
            // Aim context gate: only attribute attacks from players holding a melee
            // weapon and looking at a real player (not a bot).
            if (!isMeleeWeapon(otherHeld) || !isLookingAtPlayer(other)) continue;
            RavenVec3 otherPos = (RavenVec3) other.get("position");
            if (otherPos == null) continue;
            double distance = victimPos.distanceTo(otherPos);
            if (distance < suspectDistance) {
                suspectDistance = distance;
                suspect = other;
            }
        }
        if (suspect != null) {
            int lastSwing = (int) suspect.getOrDefault("lastSwingTick", 0);
            // Grim PacketOrderB (pre-1.9): every attack requires a swing animation
            // since the previous attack. A single swing-miss can be client/network
            // skew, so require two consecutive attacks with no animation in between.
            int lastProcessed = (int) suspect.getOrDefault("NoSwingA_LastTick", 0);
            int attacks = (int) suspect.getOrDefault("NoSwingA_Attacks", 0);
            if (lastProcessed != ticksExisted) {
                if (lastProcessed > 0 && ticksExisted - lastProcessed > 6) attacks = 0;
                if (ticksExisted - lastSwing > 3) attacks++;
                else attacks = 0;
                suspect.put("NoSwingA_LastTick", ticksExisted);
                suspect.put("NoSwingA_Attacks", attacks);
            }
            if (attacks >= 2) {
                vl++;
                if (vl >= vlThreshold && now - lastAlert > cooldown) {
                    anticheatPlayer.put("NoSwingA_LastAlert", now);
                    printFlag(suspect, "NCP NoSwing", vl);
                }
            } else {
                vl = Math.max(vl - 1, 0);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("NoSwingA_VL", vl);
    }

    Map <String, Object > findRecentAttacker(Map <String, Object > victimData, int ticksExisted) {
        RavenPlayer victim = (RavenPlayer) victimData.get( "entity");
        if (victim == null) return null;
        RavenVec3 victimPos = (RavenVec3) victimData.get( "position");
        if (victimPos == null) return null;
        Map <String, Object > attacker = null;
        double best = 6.5;
        for (Map.Entry <RavenPlayer, Map <String, Object > > entry : anticheatPlayers.entrySet()) {
            if (entry.getKey() == victim) continue;
            Map <String, Object > other = entry.getValue();
            if (other == null || other == victimData) continue;
            int lastSwing = (int) other.getOrDefault( "lastSwingTick", 0);
            if (ticksExisted - lastSwing  > 2) continue;
            // Negativity Reach IGNORED_TYPE: bow / fishing rod "hits" are projectile
            // hits - the victim is not in melee range of the attacker.
            RavenItem otherHeld = (RavenItem) other.get("heldItem");
            if (otherHeld != null && (otherHeld.type.contains("Bow") || otherHeld.type.contains("FishingRod"))) continue;
            // Aim context gate: only attribute attacks from players holding a melee
            // weapon and looking at a real player (not a bot).
            if (!isMeleeWeapon(otherHeld) || !isLookingAtPlayer(other)) continue;
            RavenVec3 otherPos = (RavenVec3) other.get( "position");
            if (otherPos == null) continue;
            double distance = victimPos.distanceTo(otherPos);
            if (distance  < best) {
                best = distance;
                attacker = other;
            }
        }
        return attacker;
    }

    double getPitchTo(RavenVec3 eye, RavenVec3 target) {
        double dx = target.x - eye.x;
        double dz = target.z - eye.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        if (h < 1e-8) return 0;
        return Math.toDegrees(Math.atan2(eye.y - target.y, h));
    }

    void ReachC(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "ReachC_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "ReachC_LastAlert", 0L);
        int lastHit = (int) anticheatPlayer.getOrDefault( "lastHitTick", 0);
        int ticksExisted = (int) anticheatPlayer.get( "ticksExisted");
        if (lastHit != ticksExisted) {
            anticheatPlayer.put( "ReachC_VL", Math.max(vl - 1, 0));
            return;
        }
        boolean burning = Boolean.TRUE.equals(anticheatPlayer.get( "burning"));
        boolean inWater = Boolean.TRUE.equals(anticheatPlayer.get( "inWater"));
        boolean inLava = Boolean.TRUE.equals(anticheatPlayer.get( "inLava"));
        boolean riding = anticheatPlayer.get( "riding") != null;
        if (burning || inWater || inLava || riding) {
            anticheatPlayer.put( "ReachC_VL", Math.max(vl - 1, 0));
            return;
        }
        Map <String, Object > attacker = findRecentAttacker(anticheatPlayer, ticksExisted);
        if (attacker == null) {
            anticheatPlayer.put( "ReachC_VL", Math.max(vl - 1, 0));
            return;
        }
        RavenVec3 attackerPos = (RavenVec3) attacker.get( "position");
        RavenVec3 victimPos = (RavenVec3) anticheatPlayer.get( "position");
        if (attackerPos == null || victimPos == null) {
            anticheatPlayer.put( "ReachC_VL", Math.max(vl - 1, 0));
            return;
        }
        RavenVec3 eye = attackerPos.offset(0, 1.35, 0);
        RavenVec3 center = victimPos.offset(0, 0.9, 0);
        double distance = eye.distanceTo(center);
        if (distance  > 4.3) {
            // Matrix traceback: a hit explained by the victim's position up to 8 ticks
            // back (latency) is not flagged.
            List<RavenVec3> victimHistory = (List<RavenVec3>) anticheatPlayer.get("previousPositions");
            double traceDist = Double.MAX_VALUE;
            if (victimHistory != null) {
                int limit = Math.min(victimHistory.size(), 8);
                for (int i = 0; i < limit; i++) {
                    RavenVec3 h = victimHistory.get(i);
                    if (h != null) traceDist = Math.min(traceDist, h.distanceTo(attackerPos));
                }
            }
            if (traceDist <= 3.15) {
                vl = Math.max(vl - 1, 0);
            } else {
                // Matrix dynamic VL: (reach - max_reach) * 10, clamped 3..8, with a
                // burst cap of 15 VL per 5 seconds.
                double excess = Math.max(0, distance - 3.0);
                int gain = (int) Math.round(Math.min(8.0, Math.max(3.0, excess * 10.0)));
                int burst = (int) anticheatPlayer.getOrDefault( "ReachC_Burst", 0);
                long burstStart = (long) anticheatPlayer.getOrDefault( "ReachC_BurstStart", 0L);
                if (now - burstStart > 5000) {
                    burst = 0;
                    burstStart = now;
                }
                if (burst + gain > 15) gain = Math.max(0, 15 - burst);
                burst += gain;
                anticheatPlayer.put( "ReachC_Burst", burst);
                anticheatPlayer.put( "ReachC_BurstStart", burstStart);
                vl += gain;
                attacker.put("ReachC_FlagTime", now);
                if (vl  >= vlThreshold  && now - lastAlert  > cooldown) {
                    anticheatPlayer.put( "ReachC_LastAlert", now);
                    printFlag(attacker,  "Verus Reach", vl);
                }
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put( "ReachC_VL", vl);
    }

    void HitBoxA(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "HitBoxA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "HitBoxA_LastAlert", 0L);
        int lastHit = (int) anticheatPlayer.getOrDefault( "lastHitTick", 0);
        int ticksExisted = (int) anticheatPlayer.get( "ticksExisted");
        if (lastHit != ticksExisted) {
            anticheatPlayer.put( "HitBoxA_VL", Math.max(vl - 1, 0));
            return;
        }
        boolean riding = anticheatPlayer.get( "riding") != null;
        if (riding) {
            anticheatPlayer.put( "HitBoxA_VL", Math.max(vl - 1, 0));
            return;
        }
        Map <String, Object > attacker = findRecentAttacker(anticheatPlayer, ticksExisted);
        if (attacker == null) {
            anticheatPlayer.put( "HitBoxA_VL", Math.max(vl - 1, 0));
            return;
        }
        RavenVec3 attackerPos = (RavenVec3) attacker.get( "position");
        RavenVec3 victimPos = (RavenVec3) anticheatPlayer.get( "position");
        if (attackerPos == null || victimPos == null) {
            anticheatPlayer.put( "HitBoxA_VL", Math.max(vl - 1, 0));
            return;
        }
        RavenVec3 eye = attackerPos.offset(0, 1.62, 0);
        RavenVec3 center = victimPos.offset(0, 0.9, 0);
        double range = eye.distanceTo(center);
        if (range  <= 4.5) {
            double dx = center.x - eye.x;
            double dz = center.z - eye.z;
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            // Grim Reach dual-look (1.8): the server resolves the hit against the look
            // of the attack tick and the previous tick. A killaura that snaps and hits
            // in the same tick only passes if ONE of the two looks covers the box, so
            // flag only when BOTH miss.
            double yawMissCur = Math.abs(getFacingAngle((float) attacker.get("yaw"), eye, center));
            double yawMissPrev = Math.abs(getFacingAngle((float) attacker.getOrDefault("previousYaw", (float) attacker.get("yaw")), eye, center));
            double yawMiss = Math.min(yawMissCur, yawMissPrev);
            double yawRadius = Math.toDegrees(Math.atan(0.3 / Math.max(horizontal, 0.1)));
            double pitchMiss = Math.abs((float) attacker.get( "pitch") - getPitchTo(eye, center));
            double pitchRadius = Math.toDegrees(Math.atan(0.9 / Math.max(range, 0.1)));
            if (yawMiss - yawRadius  > 55  && pitchMiss - pitchRadius  > 15) {
                vl++;
                if (vl  >= vlThreshold  && now - lastAlert  > cooldown) {
                    anticheatPlayer.put( "HitBoxA_LastAlert", now);
                    attacker.put("HitBoxA_FlagTime", now);
                    printFlag(attacker,  "Grim Hitbox", vl);
                }
            } else {
                vl = Math.max(vl - 1, 0);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put( "HitBoxA_VL", vl);
    }

    void MultiAuraA(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "MultiAuraA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "MultiAuraA_LastAlert", 0L);
        int lastHit = (int) anticheatPlayer.getOrDefault( "lastHitTick", 0);
        int ticksExisted = (int) anticheatPlayer.get( "ticksExisted");
        if (lastHit != ticksExisted) {
            anticheatPlayer.put( "MultiAuraA_VL", Math.max(vl - 1, 0));
            return;
        }
        boolean riding = anticheatPlayer.get( "riding") != null;
        if (riding) {
            anticheatPlayer.put( "MultiAuraA_VL", Math.max(vl - 1, 0));
            return;
        }
        Map <String, Object > attacker = findRecentAttacker(anticheatPlayer, ticksExisted);
        if (attacker == null) {
            anticheatPlayer.put( "MultiAuraA_VL", Math.max(vl - 1, 0));
            return;
        }
        List <Object[] > hits = (List <Object[] >) attacker.get( "MultiAuraA_Hits");
        if (hits == null) {
            hits = new ArrayList < >();
            attacker.put( "MultiAuraA_Hits", hits);
        }
        hits.add(new Object[]{ ticksExisted, anticheatPlayer.get( "name") });
        while (!hits.isEmpty()  && ticksExisted - (Integer) hits.get(0)[0]  > 15) {
            hits.remove(0);
        }
        HashSet <String > victims = new HashSet < >();
        int count = 0;
        for (Object[] hit : hits) {
            if (ticksExisted - (Integer) hit[0]  <= 12) {
                count++;
                victims.add((String) hit[1]);
            }
        }
        // Grim MultiInteractA: interacting with two different entities in the SAME
        // movement tick is impossible in vanilla - the strongest multi-aura signal.
        boolean sameTickMulti = false;
        for (int i = 0; i < hits.size(); i++) {
            Object[] a = hits.get(i);
            for (int j = i + 1; j < hits.size(); j++) {
                Object[] b = hits.get(j);
                if (((Integer) a[0]).equals((Integer) b[0]) && !((String) a[1]).equals((String) b[1])) {
                    sameTickMulti = true;
                    break;
                }
            }
            if (sameTickMulti) break;
        }
        if (sameTickMulti || (count  >= 3  && victims.size()  >= 2)) {
            vl++;
            if (vl  >= vlThreshold  && now - lastAlert  > cooldown) {
                anticheatPlayer.put( "MultiAuraA_LastAlert", now);
                printFlag(attacker,  "Karhu MultiAura", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put( "MultiAuraA_VL", vl);
    }

    void VelocityD(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "VelocityD_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "VelocityD_LastAlert", 0L);
        double expected = (Double) anticheatPlayer.getOrDefault( "VelocityD_Expected", 0d);
        if (expected  <= 0) {
            anticheatPlayer.put( "VelocityD_VL", Math.max(vl - 1, 0));
            return;
        }
        int expTick = (int) anticheatPlayer.getOrDefault( "VelocityD_Tick", 0);
        int ticksExisted = (int) anticheatPlayer.get( "ticksExisted");
        int since = ticksExisted - expTick;
        if (since  > 4) {
            anticheatPlayer.put( "VelocityD_Expected", 0d);
            anticheatPlayer.put( "VelocityD_VL", Math.max(vl - 1, 0));
            return;
        }
        RavenVec3 position = (RavenVec3) anticheatPlayer.get( "position");
        String feet = world.getBlockAt((int) Math.floor(position.x), (int) Math.floor(position.y), (int) Math.floor(position.z)).name;
        String below = world.getBlockAt((int) Math.floor( position.x), (int) Math.floor(position.y) - 1, (int) Math.floor(position.z)).name;
        boolean inWeb = feet.equals( "web") || below.equals( "web");
        if (since >= 1 && !inWeb && !checkSurroundingBlocks(position)) {
            boolean onGround = Boolean.TRUE.equals(anticheatPlayer.get("onGround"));
            double friction = onGround ? 0.546 : 0.91;
            double predicted = expected * (1 - Math.pow(friction, since + 1)) / (1 - friction);
            RavenVec3 start = (RavenVec3) anticheatPlayer.get("VelocityD_StartPos");
            if (start != null && predicted >= 0.75) {
                double actual = Math.sqrt(Math.pow(position.x - start.x, 2) + Math.pow(position.z - start.z, 2));
                if (actual < predicted * 0.3) {
                    vl++;
                    if (vl >= vlThreshold && now - lastAlert > cooldown) {
                        anticheatPlayer.put("VelocityD_LastAlert", now);
                        printFlag(anticheatPlayer, "Grim Velocity", vl);
                    }
                } else {
                    vl = Math.max(vl - 1, 0);
                }
            } else {
                vl = Math.max(vl - 1, 0);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("VelocityD_VL", vl);
    }

    void AimC(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "AimC_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "AimC_LastAlert", 0L);
        boolean riding = anticheatPlayer.get( "riding") != null;
        RavenItem heldItem = (RavenItem) anticheatPlayer.get( "heldItem");
        int ticksExisted = (int) anticheatPlayer.get( "ticksExisted");
        int lastSwing = (int) anticheatPlayer.getOrDefault( "lastSwingTick", 0);
        float yaw = (float) anticheatPlayer.get( "yaw");
        float prevYaw = (float) anticheatPlayer.get( "previousYaw");
        float pitch = (float) anticheatPlayer.get( "pitch");
        float prevPitch = (float) anticheatPlayer.get( "previousPitch");
        RavenVec3 position = (RavenVec3) anticheatPlayer.get( "position");
        double yawDelta = Math.abs(yaw - prevYaw);
        if (yawDelta  > 180) yawDelta = 360 - yawDelta;
        double pitchDelta = Math.abs(pitch - prevPitch);
        boolean targetNearby = false;
        for (Map.Entry<RavenPlayer, Map<String, Object>> entry : anticheatPlayers.entrySet()) {
            if (entry.getValue() == anticheatPlayer) continue;
            RavenVec3 otherPos = (RavenVec3) entry.getValue().get("position");
            if (otherPos != null && position.distanceTo(otherPos) < 8.0) {
                targetNearby = true;
                break;
            }
        }
        boolean tracking = holdingWeapon(heldItem) && !riding && !isReplay() && targetNearby && ticksExisted - lastSwing <= 8 && pitchDelta > 0.05;
        int seq = (int) anticheatPlayer.getOrDefault("AimC_Seq", 0);
        double lastDelta = (Double) anticheatPlayer.getOrDefault("AimC_LastDelta", 0d);
        if (tracking && yawDelta > 1.0 && Math.abs(yawDelta - lastDelta) < 5e-5) seq++;
        else seq = (tracking && yawDelta > 1.0) ? 1 : 0;
        anticheatPlayer.put("AimC_LastDelta", (tracking && yawDelta > 1.0) ? yawDelta : 0d);
        // Negativity AimBot "ratio": while turning (yaw moving > 0.65), the pitch
        // delta repeats almost identically (within 0.005) on consecutive ticks.
        // Human pitch movement jitters; only smoothed aim does not.
        boolean aiming = holdingWeapon(heldItem) && !riding && !isReplay() && targetNearby && ticksExisted - lastSwing <= 8;
        double lastPitchDelta = (Double) anticheatPlayer.getOrDefault("AimC_LastPitchDelta", 0d);
        int ratioSeq = (int) anticheatPlayer.getOrDefault("AimC_RatioSeq", 0);
        if (aiming && pitchDelta > 0 && Math.abs(pitchDelta - lastPitchDelta) < 0.005 && yawDelta > 0.65) ratioSeq++;
        else ratioSeq = 0;
        anticheatPlayer.put("AimC_LastPitchDelta", pitchDelta);
        anticheatPlayer.put("AimC_RatioSeq", ratioSeq);
        boolean flagged = false;
        if (seq >= 6) {
            seq = 0;
            flagged = true;
        }
        if (ratioSeq > 7) {
            ratioSeq = 0;
            flagged = true;
        }
        if (flagged) {
            vl++;
            if (vl >= vlThreshold && now - lastAlert > cooldown) {
                anticheatPlayer.put("AimC_LastAlert", now);
                printFlag(anticheatPlayer, "Karhu SmoothAim", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("AimC_Seq", seq);
        anticheatPlayer.put("AimC_VL", vl);
    }

    void AimD(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "AimD_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "AimD_LastAlert", 0L);
        boolean riding = anticheatPlayer.get( "riding") != null;
        RavenItem heldItem = (RavenItem) anticheatPlayer.get( "heldItem");
        int ticksExisted = (int) anticheatPlayer.get( "ticksExisted");
        int lastSwing = (int) anticheatPlayer.getOrDefault( "lastSwingTick", 0);
        float yaw = (float) anticheatPlayer.get( "yaw");
        float prevYaw = (float) anticheatPlayer.get( "previousYaw");
        float pitch = (float) anticheatPlayer.get( "pitch");
        float prevPitch = (float) anticheatPlayer.get( "previousPitch");
        double yawDelta = Math.abs(yaw - prevYaw);
        if (yawDelta  > 180) yawDelta = 360 - yawDelta;
        double pitchDelta = Math.abs(pitch - prevPitch);
        // Aim assist often accelerates then decelerates smoothly. Humans show
        // random jitter. Track consecutive deltas and flag a smooth ramp pattern.
        List <Double > deltas = (List <Double >) anticheatPlayer.get( "AimD_Deltas");
        if (deltas == null) {
            deltas = new ArrayList < >();
            anticheatPlayer.put( "AimD_Deltas", deltas);
        }
        double total = Math.max(yawDelta, pitchDelta);
        if (total > 0.05 && total < 25 && !riding) deltas.add(total);
        if (deltas.size()  > 30) deltas.remove(0);
        if (deltas.size()  >= 10) {
            boolean smooth = true;
            for (int i = 1; i < deltas.size(); i++) {
                if (Math.abs(deltas.get(i) - deltas.get(i - 1))  > 0.05) {
                    smooth = false;
                    break;
                }
            }
            if (smooth && ticksExisted - lastSwing  <= 8 && holdingWeapon(heldItem)) {
                vl++;
                if (vl  >= vlThreshold  && now - lastAlert  > cooldown) {
                    anticheatPlayer.put( "AimD_LastAlert", now);
                    printFlag(anticheatPlayer,  "Grim AimAccel", vl);
                }
                deltas.clear();
            } else {
                vl = Math.max(vl - 1, 0);
            }
        }
        anticheatPlayer.put( "AimD_VL", vl);
    }

    void AimE(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "AimE_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "AimE_LastAlert", 0L);
        boolean riding = anticheatPlayer.get( "riding") != null;
        RavenItem heldItem = (RavenItem) anticheatPlayer.get( "heldItem");
        int ticksExisted = (int) anticheatPlayer.get( "ticksExisted");
        int lastSwing = (int) anticheatPlayer.getOrDefault( "lastSwingTick", 0);
        float yaw = (float) anticheatPlayer.get( "yaw");
        float prevYaw = (float) anticheatPlayer.get( "previousYaw");
        float pitch = (float) anticheatPlayer.get( "pitch");
        RavenVec3 position = (RavenVec3) anticheatPlayer.get( "position");
        if (position == null) { anticheatPlayer.put( "AimE_VL", Math.max(vl - 1, 0)); return; }
        double yawDelta = Math.abs(yaw - prevYaw);
        if (yawDelta  > 180) yawDelta = 360 - yawDelta;
        if (!holdingWeapon(heldItem) || riding || ticksExisted - lastSwing  > 2 || yawDelta  < 15) {
            anticheatPlayer.put( "AimE_VL", Math.max(vl - 1, 0));
            return;
        }
        // Instant snap onto a target: yaw rotates a large amount in a single tick
        // and ends exactly on the target's required angle (within <1 degree on both
        // axes). The rotation must also move TOWARD the target, so unrelated rotations
        // (e.g. opening an Ender Chest) are not flagged.
        double bestDist = 6.0;
        RavenVec3 targetPos = null;
        for (Map.Entry<RavenPlayer, Map<String, Object>> e : anticheatPlayers.entrySet()) {
            if (e.getValue() == anticheatPlayer) continue;
            RavenVec3 oPos = (RavenVec3) e.getValue().get("position");
            if (oPos == null) continue;
            double d = position.distanceTo(oPos);
            if (d < bestDist) { bestDist = d; targetPos = oPos; }
        }
        if (targetPos == null) { anticheatPlayer.put( "AimE_VL", Math.max(vl - 1, 0)); return; }
        RavenVec3 eye = position.offset(0, 1.62, 0);
        RavenVec3 center = targetPos.offset(0, 0.9, 0);
        double[] angles = getAnglesToTarget(eye, center);
        double errYaw = Math.abs(yaw - angles[0]);
        if (errYaw  > 180) errYaw = 360 - errYaw;
        double prevErrYaw = Math.abs(prevYaw - angles[0]);
        if (prevErrYaw  > 180) prevErrYaw = 360 - prevErrYaw;
        double errPitch = Math.abs(pitch - angles[1]);
        if (errYaw  < 1.0 && errPitch < 1.5 && prevErrYaw > errYaw) {
            vl++;
            if (vl  >= vlThreshold  && now - lastAlert  > cooldown) {
                anticheatPlayer.put( "AimE_LastAlert", now);
                printFlag(anticheatPlayer,  "NCP AimSnap", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put( "AimE_VL", vl);
    }

    void AimF(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "AimF_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "AimF_LastAlert", 0L);
        boolean riding = anticheatPlayer.get( "riding") != null;
        RavenItem heldItem = (RavenItem) anticheatPlayer.get( "heldItem");
        int ticksExisted = (int) anticheatPlayer.get( "ticksExisted");
        int lastSwing = (int) anticheatPlayer.getOrDefault( "lastSwingTick", 0);
        RavenVec3 position = (RavenVec3) anticheatPlayer.get( "position");
        if (position == null) { anticheatPlayer.put( "AimF_VL", Math.max(vl - 1, 0)); return; }
        if (!holdingWeapon(heldItem) || riding || ticksExisted - lastSwing  > 8) {
            anticheatPlayer.put( "AimF_VL", Math.max(vl - 1, 0));
            return;
        }
        // Perfect tracking: while a target moves, the player's yaw/pitch track the
        // required angle within a tiny tolerance for many consecutive ticks.
        double bestDist = 6.0;
        RavenVec3 targetPos = null;
        for (Map.Entry<RavenPlayer, Map<String, Object>> e : anticheatPlayers.entrySet()) {
            if (e.getValue() == anticheatPlayer) continue;
            RavenVec3 oPos = (RavenVec3) e.getValue().get("position");
            if (oPos == null) continue;
            double d = position.distanceTo(oPos);
            if (d < bestDist) { bestDist = d; targetPos = oPos; }
        }
        if (targetPos == null) { anticheatPlayer.put( "AimF_VL", Math.max(vl - 1, 0)); return; }
        RavenVec3 eye = position.offset(0, 1.62, 0);
        RavenVec3 center = targetPos.offset(0, 0.9, 0);
        double[] angles = getAnglesToTarget(eye, center);
        float yaw = (float) anticheatPlayer.get( "yaw");
        float pitch = (float) anticheatPlayer.get( "pitch");
        double errYaw = Math.abs(yaw - angles[0]);
        if (errYaw  > 180) errYaw = 360 - errYaw;
        double errPitch = Math.abs(pitch - angles[1]);
        int trackTicks = (int) anticheatPlayer.getOrDefault( "AimF_TrackTicks", 0);
        if (errYaw  < 0.5  && errPitch  < 0.5) {
            trackTicks++;
        } else {
            trackTicks = 0;
        }
        anticheatPlayer.put( "AimF_TrackTicks", trackTicks);
        if (trackTicks  >= 12) {
            anticheatPlayer.put( "AimF_TrackTicks", 0);
            vl++;
            if (vl  >= vlThreshold  && now - lastAlert  > cooldown) {
                anticheatPlayer.put( "AimF_LastAlert", now);
                printFlag(anticheatPlayer,  "Karhu AimTracking", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put( "AimF_VL", vl);
    }

    void AutoBlockC(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "AutoBlockC_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "AutoBlockC_LastAlert", 0L);
        RavenItem heldItem = (RavenItem) anticheatPlayer.get( "heldItem");
        int ticksExisted = (int) anticheatPlayer.get( "ticksExisted");
        int lastSwing = (int) anticheatPlayer.getOrDefault( "lastSwingTick", 0);
        int lastUsing = (int) anticheatPlayer.getOrDefault( "lastUsingTick", 0);
        int lastStopUsing = (int) anticheatPlayer.getOrDefault( "lastStopUsingTick", 0);
        boolean holdingSword = heldItem != null  && heldItem.type.equals( "ItemSword");
        int cycles = (int) anticheatPlayer.getOrDefault( "AutoBlockC_Cycles", 0);
        int lastCycle = (int) anticheatPlayer.getOrDefault( "AutoBlockC_LastCycleTick", 0);
        if (holdingSword && ticksExisted - lastSwing <= 1) {
            boolean tap = Math.abs(ticksExisted - lastUsing) <= 2 && lastStopUsing >= lastUsing && ticksExisted - lastStopUsing <= 2;
            if (tap) {
                cycles = (lastCycle > 0 && ticksExisted - lastCycle <= 7) ? cycles + 1 : 1;
                anticheatPlayer.put("AutoBlockC_LastCycleTick", ticksExisted);
            } else {
                cycles = Math.max(cycles - 1, 0);
            }
        } else {
            cycles = Math.max(cycles - 1, 0);
        }
        if (cycles >= 5) {
            cycles = 0;
            vl++;
            if (vl >= vlThreshold && now - lastAlert > cooldown) {
                anticheatPlayer.put("AutoBlockC_LastAlert", now);
                printFlag(anticheatPlayer, "Hawk AutoBlock", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("AutoBlockC_Cycles", cycles);
        anticheatPlayer.put("AutoBlockC_VL", vl);
    }

    void JumpA(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "JumpA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "JumpA_LastAlert", 0L);
        int ticksExisted = (int) anticheatPlayer.get( "ticksExisted");
        int lastOnGroundTick = (int) anticheatPlayer.getOrDefault( "lastOnGroundTick", 0);
        int lastStopFallingTick = (int) anticheatPlayer.getOrDefault( "lastStopFallingTick", 0);
        boolean inWater = Boolean.TRUE.equals(anticheatPlayer.get( "inWater"));
        boolean inLava = Boolean.TRUE.equals(anticheatPlayer.get( "inLava"));
        boolean onLadder = Boolean.TRUE.equals(anticheatPlayer.get( "onLadder"));
        boolean riding = anticheatPlayer.get( "riding") != null;
        boolean creative = Boolean.TRUE.equals(anticheatPlayer.get( "creative"));
        int hurtTime = (int) anticheatPlayer.get( "hurtTime");
        RavenVec3 position = (RavenVec3) anticheatPlayer.get( "position");
        String below = world.getBlockAt((int) Math.floor(position.x), (int) Math.floor(position.y) - 1, (int) Math.floor(position.z)).name;
        String feet = world.getBlockAt((int) Math.floor(position.x), (int) Math.floor(position.y), (int) Math.floor(position.z)).name;
        boolean jumpBoost = false;
        List <Object[] > effects = (List <Object[] >) anticheatPlayer.get( "potionEffects");
        if (effects != null) {
            for (Object[] effect : effects) {
                if ((Integer) effect[0] == 8) {
                    jumpBoost = true;
                    break;
                }
            }
        }
        double startY = (Double) anticheatPlayer.getOrDefault( "JumpA_StartY", 0d);
        double maxY = (Double) anticheatPlayer.getOrDefault( "JumpA_MaxY", 0d);
        if (lastOnGroundTick == ticksExisted && !inWater && !inLava && !onLadder && !riding && !creative && hurtTime <= 0 && !below.equals("slime") && !below.equals("vine") && !feet.equals("web") && !below.equals("web")) {
            anticheatPlayer.put("JumpA_StartY", ((RavenVec3)anticheatPlayer.get("lastPosition")).y);
            anticheatPlayer.put("JumpA_Tracking", true);
            anticheatPlayer.put("JumpA_MaxY", position.y);
        } else if (lastOnGroundTick > 0 && ticksExisted - lastOnGroundTick <= 20 && !Boolean.TRUE.equals(anticheatPlayer.get("onGround"))) {
            if (position.y > maxY) anticheatPlayer.put("JumpA_MaxY", position.y);
        } else if (lastStopFallingTick == ticksExisted && Boolean.TRUE.equals(anticheatPlayer.get("JumpA_Tracking"))) {
            double peak = maxY - startY;
            anticheatPlayer.put("JumpA_StartY", 0d);
            anticheatPlayer.put("JumpA_MaxY", 0d);
            anticheatPlayer.put("JumpA_Tracking", false);
            if (peak > 1.35 && !jumpBoost) {
                vl++;
                if (vl >= vlThreshold && now - lastAlert > cooldown) {
                    anticheatPlayer.put("JumpA_LastAlert", now);
                    printFlag(anticheatPlayer, "Intave Jump", vl);
                }
            } else {
                vl = Math.max(vl - 1, 0);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("JumpA_VL", vl);
    }

    void JumpB(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "JumpB_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "JumpB_LastAlert", 0L);
        int ticksExisted = (int) anticheatPlayer.get( "ticksExisted");
        int lastOnGroundTick = (int) anticheatPlayer.getOrDefault( "lastOnGroundTick", 0);
        int lastStopFallingTick = (int) anticheatPlayer.getOrDefault( "lastStopFallingTick", 0);
        boolean inWater = Boolean.TRUE.equals(anticheatPlayer.get( "inWater"));
        boolean inLava = Boolean.TRUE.equals(anticheatPlayer.get( "inLava"));
        boolean onLadder = Boolean.TRUE.equals(anticheatPlayer.get( "onLadder"));
        boolean riding = anticheatPlayer.get( "riding") != null;
        boolean creative = Boolean.TRUE.equals(anticheatPlayer.get( "creative"));
        int hurtTime = (int) anticheatPlayer.get( "hurtTime");
        RavenVec3 position = (RavenVec3) anticheatPlayer.get( "position");
        RavenVec3 lastPosition = (RavenVec3) anticheatPlayer.get( "lastPosition");
        double speed = Math.sqrt(Math.pow(position.x - lastPosition.x, 2) + Math.pow(position.z - lastPosition.z, 2));
        int streak = (int) anticheatPlayer.getOrDefault( "JumpB_Streak", 0);
        if (lastOnGroundTick == ticksExisted && ticksExisted - lastStopFallingTick <= 1 && speed >= 0.28 && !inWater && !inLava && !onLadder && !riding && !creative && hurtTime <= 0) {
            streak++;
            if (streak >= 6) {
                streak = 0;
                vl++;
                if (vl >= vlThreshold && now - lastAlert > cooldown) {
                    anticheatPlayer.put("JumpB_LastAlert", now);
                    printFlag(anticheatPlayer, "AAC BunnyHop", vl);
                }
            }
        } else if (lastOnGroundTick == ticksExisted || (ticksExisted - lastStopFallingTick > 2 && ticksExisted - lastOnGroundTick > 2)) {
            streak = Math.max(streak - 1, 0);
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("JumpB_Streak", streak);
        anticheatPlayer.put("JumpB_VL", vl);
    }

    void SpeedC(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "SpeedC_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "SpeedC_LastAlert", 0L);
        boolean onGround = Boolean.TRUE.equals(anticheatPlayer.get( "onGround"));
        boolean inWater = Boolean.TRUE.equals(anticheatPlayer.get( "inWater"));
        boolean inLava = Boolean.TRUE.equals(anticheatPlayer.get( "inLava"));
        boolean onLadder = Boolean.TRUE.equals(anticheatPlayer.get( "onLadder"));
        boolean riding = anticheatPlayer.get( "riding") != null;
        boolean creative = Boolean.TRUE.equals(anticheatPlayer.get( "creative"));
        int hurtTime = (int) anticheatPlayer.get( "hurtTime");
        int lastHit = (int) anticheatPlayer.getOrDefault( "lastHitTick", 0);
        int ticksExisted = (int) anticheatPlayer.get( "ticksExisted");
        RavenVec3 position = (RavenVec3) anticheatPlayer.get( "position");
        RavenVec3 lastPosition = (RavenVec3) anticheatPlayer.get( "lastPosition");
        double speed = Math.sqrt(Math.pow(position.x - lastPosition.x, 2) + Math.pow(position.z - lastPosition.z, 2));
        String blockOn = (String) anticheatPlayer.get( "blockOn");
        boolean slippery = blockOn != null  && blockOn.contains( "ice");
        double prevSpeed = (Double) anticheatPlayer.getOrDefault( "SpeedC_PrevSpeed", speed);
        double acceleration = speed - prevSpeed;
        anticheatPlayer.put( "SpeedC_PrevSpeed", speed);
        if (onGround && !inWater && !inLava && !onLadder && !riding && !creative && hurtTime <= 0 && lastHit > 0 && ticksExisted - lastHit > 10 && !slippery && speed >= 0.1) {
            if (acceleration > 0.4) {
                vl++;
                if (vl >= vlThreshold && now - lastAlert > cooldown) {
                    anticheatPlayer.put("SpeedC_LastAlert", now);
                    printFlag(anticheatPlayer, "Matrix Acceleration", vl);
                }
            } else {
                vl = Math.max(vl - 1, 0);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("SpeedC_VL", vl);
    }

    void VClipA(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "VClipA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "VClipA_LastAlert", 0L);
        boolean riding = anticheatPlayer.get( "riding") != null;
        boolean creative = Boolean.TRUE.equals(anticheatPlayer.get( "creative"));
        boolean inWater = Boolean.TRUE.equals(anticheatPlayer.get( "inWater"));
        boolean inLava = Boolean.TRUE.equals(anticheatPlayer.get( "inLava"));
        boolean onLadder = Boolean.TRUE.equals(anticheatPlayer.get( "onLadder"));
        boolean burning = Boolean.TRUE.equals(anticheatPlayer.get( "burning"));
        int hurtTime = (int) anticheatPlayer.get( "hurtTime");
        RavenVec3 position = (RavenVec3) anticheatPlayer.get( "position");
        RavenVec3 lastPosition = (RavenVec3) anticheatPlayer.get( "lastPosition");
        double deltaY = position.y - lastPosition.y;
        double deltaXZ = Math.sqrt(Math.pow(position.x - lastPosition.x, 2) + Math.pow(position.z - lastPosition.z, 2));
        String feet = world.getBlockAt((int) Math.floor(position.x), (int) Math.floor(position.y), (int) Math.floor(position.z)).name;
        String below = world.getBlockAt((int) Math.floor(position.x), (int) Math.floor(position.y) - 1, (int) Math.floor(position.z)).name;
        boolean inWeb = feet.equals( "web") || below.equals( "web");
        if (deltaY > 2.2 && deltaXZ <= 0.5 && !riding && !creative && !inWater && !inLava && !onLadder && !inWeb && !burning && hurtTime == 0) {
            vl++;
            if (vl >= vlThreshold && now - lastAlert > cooldown) {
                anticheatPlayer.put("VClipA_LastAlert", now);
                printFlag(anticheatPlayer, "Grim VClip", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("VClipA_VL", vl);
    }

    void EludeA(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "EludeA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "EludeA_LastAlert", 0L);
        RavenVec3 position = (RavenVec3) anticheatPlayer.get( "position");
        RavenVec3 lastPosition = (RavenVec3) anticheatPlayer.get( "lastPosition");
        double deltaY = position.y - lastPosition.y;
        boolean inWater = Boolean.TRUE.equals(anticheatPlayer.get( "inWater"));
        boolean inLava = Boolean.TRUE.equals(anticheatPlayer.get( "inLava"));
        boolean onLadder = Boolean.TRUE.equals(anticheatPlayer.get( "onLadder"));
        boolean riding = anticheatPlayer.get( "riding") != null;
        boolean creative = Boolean.TRUE.equals(anticheatPlayer.get( "creative"));
        int lastHit = (int) anticheatPlayer.getOrDefault( "lastHitTick", 0);
        int ticksExisted = (int) anticheatPlayer.get( "ticksExisted");
        double knockback = (Double) anticheatPlayer.getOrDefault( "VelocityD_Expected", 0d);
        String feet = world.getBlockAt((int) Math.floor(position.x), (int) Math.floor(position.y), (int) Math.floor(position.z)).name;
        String below = world.getBlockAt((int) Math.floor(position.x), (int) Math.floor(position.y) - 1, (int) Math.floor(position.z)).name;
        int driftTicks = (int) anticheatPlayer.getOrDefault( "EludeA_DriftTicks", 0);
        double prevDeltaY = (Double) anticheatPlayer.getOrDefault( "EludeA_PrevDeltaY", 0d);
        boolean airborne = isAirborne(anticheatPlayer, position);
        boolean recentlyKnocked = lastHit  > 0  && ticksExisted - lastHit  <= 5 || knockback  > 0;
        if (airborne && !inWater && !inLava && !onLadder && !riding && !creative && !recentlyKnocked) {
            double predicted = (prevDeltaY - 0.08) * 0.98;
            if (prevDeltaY != 0 && deltaY - predicted > 0.03) driftTicks++;
            else driftTicks = Math.max(driftTicks - 2, 0);
            anticheatPlayer.put("EludeA_PrevDeltaY", deltaY);
            if (driftTicks >= 6) {
                driftTicks = 0;
                vl++;
                if (vl >= vlThreshold && now - lastAlert > cooldown) {
                    anticheatPlayer.put("EludeA_LastAlert", now);
                    printFlag(anticheatPlayer, "Grim Elude", vl);
                }
            } else {
                vl = Math.max(vl - 1, 0);
            }
        } else {
            driftTicks = 0;
            anticheatPlayer.put("EludeA_PrevDeltaY", 0d);
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("EludeA_DriftTicks", driftTicks);
        anticheatPlayer.put("EludeA_VL", vl);
    }

    void JumpPredictionA(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "JumpPredictionA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "JumpPredictionA_LastAlert", 0L);
        int ticksExisted = (int) anticheatPlayer.get( "ticksExisted");
        int lastOnGroundTick = (int) anticheatPlayer.getOrDefault( "lastOnGroundTick", 0);
        boolean inWater = Boolean.TRUE.equals(anticheatPlayer.get( "inWater"));
        boolean inLava = Boolean.TRUE.equals(anticheatPlayer.get( "inLava"));
        boolean onLadder = Boolean.TRUE.equals(anticheatPlayer.get( "onLadder"));
        boolean riding = anticheatPlayer.get( "riding") != null;
        boolean creative = Boolean.TRUE.equals(anticheatPlayer.get( "creative"));
        int hurtTime = (int) anticheatPlayer.get( "hurtTime");
        RavenVec3 position = (RavenVec3) anticheatPlayer.get( "position");
        RavenVec3 lastPosition = (RavenVec3) anticheatPlayer.get( "lastPosition");
        double deltaY = position.y - lastPosition.y;
        String feet = world.getBlockAt((int) Math.floor(position.x), (int) Math.floor(position.y), (int) Math.floor(position.z)).name;
        String below = world.getBlockAt((int) Math.floor(position.x), (int) Math.floor(position.y) - 1, (int) Math.floor(position.z)).name;
        boolean jumpBoost = false;
        List <Object[] > effects = (List <Object[] >) anticheatPlayer.get( "potionEffects");
        if (effects != null) {
            for (Object[] effect : effects) {
                if ((Integer) effect[0] == 8) {
                    jumpBoost = true;
                    break;
                }
            }
        }
        if (lastOnGroundTick == ticksExisted  && deltaY  > 0.55  && !inWater  && !inLava  && !onLadder  && !riding  && !creative  && hurtTime == 0  && !jumpBoost  && !below.equals( "slime")  && !feet.equals( "web")  && !below.equals( "web")) {
            vl++;
            if (vl  >= vlThreshold  && now - lastAlert  > cooldown) {
                anticheatPlayer.put( "JumpPredictionA_LastAlert", now);
                printFlag(anticheatPlayer,  "Grim JumpPrediction", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put( "JumpPredictionA_VL", vl);
    }

    void FallSpeedA(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "FallSpeedA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "FallSpeedA_LastAlert", 0L);
        RavenVec3 position = (RavenVec3) anticheatPlayer.get( "position");
        RavenVec3 lastPosition = (RavenVec3) anticheatPlayer.get( "lastPosition");
        double deltaY = position.y - lastPosition.y;
        boolean inWater = Boolean.TRUE.equals(anticheatPlayer.get( "inWater"));
        boolean inLava = Boolean.TRUE.equals(anticheatPlayer.get( "inLava"));
        boolean onLadder = Boolean.TRUE.equals(anticheatPlayer.get( "onLadder"));
        boolean riding = anticheatPlayer.get( "riding") != null;
        boolean creative = Boolean.TRUE.equals(anticheatPlayer.get( "creative"));
        int lastHit = (int) anticheatPlayer.getOrDefault( "lastHitTick", 0);
        int ticksExisted = (int) anticheatPlayer.get( "ticksExisted");
        double knockback = (Double) anticheatPlayer.getOrDefault( "VelocityD_Expected", 0d);
        String feet = world.getBlockAt((int) Math.floor(position.x), (int) Math.floor(position.y), (int) Math.floor(position.z)).name;
        String below = world.getBlockAt((int) Math.floor(position.x), (int) Math.floor(position.y) - 1, (int) Math.floor(position.z)).name;
        boolean inWeb = feet.equals( "web") || below.equals( "web");
        if (isAirborne(anticheatPlayer, position) && deltaY < -0.35 && !inWater && !inLava && !onLadder && !riding && !creative && !inWeb && (lastHit <= 0 || ticksExisted - lastHit > 5) && knockback <= 0) {
            vl++;
            if (vl >= vlThreshold && now - lastAlert > cooldown) {
                anticheatPlayer.put("FallSpeedA_LastAlert", now);
                printFlag(anticheatPlayer, "Grim Vertical", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("FallSpeedA_VL", vl);
    }

    void InvisibleHitA(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "InvisibleHitA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "InvisibleHitA_LastAlert", 0L);
        int lastHit = (int) anticheatPlayer.getOrDefault( "lastHitTick", 0);
        int ticksExisted = (int) anticheatPlayer.get( "ticksExisted");
        if (lastHit != ticksExisted) {
            anticheatPlayer.put( "InvisibleHitA_VL", Math.max(vl - 1, 0));
            return;
        }
        boolean invisible = Boolean.TRUE.equals(anticheatPlayer.get( "invisible"));
        if (!invisible) {
            anticheatPlayer.put( "InvisibleHitA_VL", Math.max(vl - 1, 0));
            return;
        }
        RavenPlayer victim = (RavenPlayer) anticheatPlayer.get( "entity");
        RavenVec3 victimPos = (RavenVec3) anticheatPlayer.get( "position");
        Map <String, Object > attacker = findRecentAttacker(anticheatPlayer, ticksExisted);
        if (attacker == null || victimPos == null) {
            anticheatPlayer.put( "InvisibleHitA_VL", Math.max(vl - 1, 0));
            return;
        }
        RavenVec3 attackerPos = (RavenVec3) attacker.get( "position");
        if (attackerPos == null) {
            anticheatPlayer.put( "InvisibleHitA_VL", Math.max(vl - 1, 0));
            return;
        }
        double best = 99;
        RavenPlayer nearest = null;
        for (Map.Entry <RavenPlayer, Map <String, Object > > entry : anticheatPlayers.entrySet()) {
            if (entry.getValue() == attacker) continue;
            RavenVec3 otherPos = (RavenVec3) entry.getValue().get( "position");
            if (otherPos == null) continue;
            double d = attackerPos.distanceTo(otherPos);
            if (d  < best) {
                best = d;
                nearest = entry.getKey();
            }
        }
        if (nearest == victim  && best  <= 4.0) {
            vl++;
            if (vl  >= vlThreshold  && now - lastAlert  > cooldown) {
                anticheatPlayer.put( "InvisibleHitA_LastAlert", now);
                printFlag(attacker,  "Grim InvisibleHit", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put( "InvisibleHitA_VL", vl);
    }

    void IntaveHeuristic(Map<String, Object> p, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) p.getOrDefault("IntaveHeuristic_VL", 0);
        long lastAlert = (long) p.getOrDefault("IntaveHeuristic_LastAlert", 0L);
        float yaw = (float) p.get("yaw");
        float pitch = (float) p.get("pitch");
        float prevYaw = (float) p.get("previousYaw");
        float prevPitch = (float) p.get("previousPitch");
        double deltaYaw = Math.abs(yaw - prevYaw);
        if (deltaYaw > 180) deltaYaw = 360 - deltaYaw;
        double deltaPitch = Math.abs(pitch - prevPitch);
        double currentVelocity = Math.sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch);
        double lastVelocity = (Double) p.getOrDefault("IntaveHeuristic_LastVel", 0d);
        p.put("IntaveHeuristic_LastVel", currentVelocity);
        RavenVec3 myPos = (RavenVec3) p.get("position");
        RavenVec3 targetPos = null;
        double bestDist = 6.0;
        for (Map.Entry<RavenPlayer, Map<String, Object>> e : anticheatPlayers.entrySet()) {
            if (e.getValue() == p) continue;
            RavenVec3 oPos = (RavenVec3) e.getValue().get("position");
            if (oPos != null) {
                double d = myPos.distanceTo(oPos);
                if (d < bestDist) { bestDist = d; targetPos = oPos; }
            }
        }
        if (targetPos != null && currentVelocity < 1.0 && lastVelocity > 12.0) {
            RavenVec3 eye = myPos.offset(0, 1.62, 0);
            RavenVec3 center = targetPos.offset(0, 0.9, 0);
            double[] angles = getAnglesToTarget(eye, center);
            double errYaw = Math.abs(yaw - angles[0]);
            if (errYaw > 180) errYaw = 360 - errYaw;
            double errPitch = Math.abs(pitch - angles[1]);
            if (errYaw < 3.0 && errPitch < 3.0) {
                vl++;
                if (vl >= vlThreshold && now - lastAlert > cooldown) {
                    p.put("IntaveHeuristic_LastAlert", now);
                    printFlag(p, "Intave Heuristic", vl);
                }
            } else {
                vl = Math.max(vl - 1, 0);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        p.put("IntaveHeuristic_VL", vl);
    }

    void GrimSimAim(Map<String, Object> p, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) p.getOrDefault("GrimSimAim_VL", 0);
        long lastAlert = (long) p.getOrDefault("GrimSimAim_LastAlert", 0L);
        RavenVec3 myPos = (RavenVec3) p.get("position");
        RavenVec3 targetPos = null;
        double bestDist = 6.0;
        for (Map.Entry<RavenPlayer, Map<String, Object>> e : anticheatPlayers.entrySet()) {
            if (e.getValue() == p) continue;
            RavenVec3 oPos = (RavenVec3) e.getValue().get("position");
            if (oPos != null) {
                double d = myPos.distanceTo(oPos);
                if (d < bestDist) { bestDist = d; targetPos = oPos; }
            }
        }
        if (targetPos != null) {
            RavenVec3 eye = myPos.offset(0, 1.62, 0);
            RavenVec3 center = targetPos.offset(0, 0.9, 0);
            double[] angles = getAnglesToTarget(eye, center);
            float yaw = (float) p.get("yaw");
            float pitch = (float) p.get("pitch");
            double errYaw = Math.abs(yaw - angles[0]);
            if (errYaw > 180) errYaw = 360 - errYaw;
            double errPitch = Math.abs(pitch - angles[1]);
            int perfectTicks = (int) p.getOrDefault("GrimSimAim_PerfectTicks", 0);
            if (errYaw < 0.1 && errPitch < 0.1) {
                perfectTicks++;
            } else {
                perfectTicks = 0;
            }
            p.put("GrimSimAim_PerfectTicks", perfectTicks);
            if (perfectTicks >= 10) {
                perfectTicks = 0;
                vl++;
                if (vl >= vlThreshold && now - lastAlert > cooldown) {
                    p.put("GrimSimAim_LastAlert", now);
                    printFlag(p, "Grim SimAim", vl);
                }
            } else {
                vl = Math.max(vl - 1, 0);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        p.put("GrimSimAim_VL", vl);
    }

    void IntaveRhythm(Map<String, Object> p, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) p.getOrDefault("IntaveRhythm_VL", 0);
        long lastAlert = (long) p.getOrDefault("IntaveRhythm_LastAlert", 0L);
        List<Integer> swings = (List<Integer>) p.get("swingTimes");
        if (swings != null && swings.size() >= 8) {
            double sum = 0;
            for (int i = 1; i < swings.size(); i++) {
                sum += (swings.get(i) - swings.get(i - 1));
            }
            double mean = sum / (swings.size() - 1);
            double sqSum = 0;
            for (int i = 1; i < swings.size(); i++) {
                double diff = (swings.get(i) - swings.get(i - 1)) - mean;
                sqSum += diff * diff;
            }
            double variance = sqSum / (swings.size() - 1);
            if (variance < 0.05 && mean <= 2.5) {
                vl++;
                if (vl >= vlThreshold && now - lastAlert > cooldown) {
                    p.put("IntaveRhythm_LastAlert", now);
                    printFlag(p, "Intave Rhythm", vl);
                }
            } else {
                vl = Math.max(vl - 1, 0);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        p.put("IntaveRhythm_VL", vl);
    }

    void GrimSimHitbox(Map<String, Object> victimData, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) victimData.getOrDefault("GrimSimHitbox_VL", 0);
        long lastAlert = (long) victimData.getOrDefault("GrimSimHitbox_LastAlert", 0L);
        int lastHit = (int) victimData.getOrDefault("lastHitTick", 0);
        int ticksExisted = (int) victimData.get("ticksExisted");
        if (lastHit != ticksExisted) {
            victimData.put("GrimSimHitbox_VL", Math.max(vl - 1, 0));
            return;
        }
        Map<String, Object> attacker = findRecentAttacker(victimData, ticksExisted);
        if (attacker == null) {
            victimData.put("GrimSimHitbox_VL", Math.max(vl - 1, 0));
            return;
        }
        RavenVec3 attackerPos = (RavenVec3) attacker.get("position");
        RavenVec3 victimPos = (RavenVec3) victimData.get("position");
        if (attackerPos == null || victimPos == null) {
            victimData.put("GrimSimHitbox_VL", Math.max(vl - 1, 0));
            return;
        }
        RavenVec3 eye = attackerPos.offset(0, 1.62, 0);
        RavenVec3 center = victimPos.offset(0, 0.9, 0);
        double range = eye.distanceTo(center);
        if (range <= 4.5) {
            double[] requiredAngles = getAnglesToTarget(eye, center);
            float actualYaw = (float) attacker.get("yaw");
            float actualPitch = (float) attacker.get("pitch");
            double yawMiss = Math.abs(actualYaw - requiredAngles[0]);
            if (yawMiss > 180) yawMiss = 360 - yawMiss;
            double pitchMiss = Math.abs(actualPitch - requiredAngles[1]);
            double dx = center.x - eye.x;
            double dz = center.z - eye.z;
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            double yawRadius = Math.toDegrees(Math.atan(0.4 / Math.max(horizontal, 0.1)));
            double pitchRadius = Math.toDegrees(Math.atan(0.9 / Math.max(range, 0.1)));
            if (yawMiss - yawRadius > 15 && pitchMiss - pitchRadius > 10) {
                vl++;
                if (vl >= vlThreshold && now - lastAlert > cooldown) {
                    victimData.put("GrimSimHitbox_LastAlert", now);
                    printFlag(attacker, "Grim SimHitbox", vl);
                }
            } else {
                vl = Math.max(vl - 1, 0);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        victimData.put("GrimSimHitbox_VL", vl);
    }

    long gcd(long a, long b) {
        if (a == 0) return b;
        if (b == 0) return a;
        a = Math.abs(a);
        b = Math.abs(b);
        while (b != 0) {
            long t = a % b;
            a = b;
            b = t;
        }
        return a;
    }

    boolean inCombatState(Map <String, Object > p) {
        RavenItem heldItem = (RavenItem) p.get( "heldItem");
        boolean riding = p.get( "riding") != null;
        if (!holdingWeapon(heldItem) || riding) return false;
        int ticksExisted = (int) p.get( "ticksExisted");
        int lastSwing = (int) p.getOrDefault( "lastSwingTick", 0);
        if (ticksExisted - lastSwing  <= 4) return true;
        RavenVec3 pos = (RavenVec3) p.get( "position");
        for (Map.Entry <RavenPlayer, Map <String, Object > > e : anticheatPlayers.entrySet()) {
            if (e.getValue() == p) continue;
            RavenVec3 o = (RavenVec3) e.getValue().get( "position");
            if (o != null  && pos != null  && pos.distanceTo(o)  < 6.0) return true;
        }
        return false;
    }

    void FakeLagA(Map <String, Object > p, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) p.getOrDefault( "FakeLagA_VL", 0);
        long lastAlert = (long) p.getOrDefault( "FakeLagA_LastAlert", 0L);
        int jumpTick = (int) p.getOrDefault( "FreezeA_JumpTick", 0);
        int seenTick = (int) p.getOrDefault( "FakeLagA_SeenTick", 0);
        if (jumpTick  > 0  && jumpTick  > seenTick) {
            p.put( "FakeLagA_SeenTick", jumpTick);
            int freezeLen = (int) p.getOrDefault( "FreezeA_FreezeLen", 0);
            double jumpSize = (Double) p.getOrDefault( "FreezeA_JumpSize", 0d);
            int activity = (int) p.getOrDefault( "FreezeA_Activity", 0);
            boolean wasMoving = Boolean.TRUE.equals(p.get( "FreezeA_WasMoving"));
            int minFreeze = 10;
            double minJump = 1.0;
            int minActivity = 2;
            if (wasMoving  && freezeLen  >= minFreeze  && jumpSize  >= minJump  && activity  >= minActivity) {
                vl++;
                if (vl  >= vlThreshold  && now - lastAlert  > cooldown) {
                    p.put( "FakeLagA_LastAlert", now);
                    printFlag(p,  "NCP FakeLag", vl);
                }
            } else {
                vl = Math.max(vl - 1, 0);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        p.put( "FakeLagA_VL", vl);
    }

    void BacktrackA(Map <String, Object > p, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) p.getOrDefault( "BacktrackA_VL", 0);
        long lastAlert = (long) p.getOrDefault( "BacktrackA_LastAlert", 0L);
        int jumpTick = (int) p.getOrDefault( "FreezeA_JumpTick", 0);
        int seenTick = (int) p.getOrDefault( "BacktrackA_SeenTick", 0);
        if (jumpTick  <= 0 || jumpTick  <= seenTick || localHitTick  <= 0) {
            p.put( "BacktrackA_VL", Math.max(vl - 1, 0));
            return;
        }
        int ticksExisted = (int) p.get( "ticksExisted");
        int sinceHit = ticksExisted - localHitTick;
        int window = 4;
        if (sinceHit  >= 1  && sinceHit  <= window) {
            p.put( "BacktrackA_SeenTick", jumpTick);
            int freezeLen = (int) p.getOrDefault( "FreezeA_FreezeLen", 0);
            double jumpSize = (Double) p.getOrDefault( "FreezeA_JumpSize", 0d);
            int activity = (int) p.getOrDefault( "FreezeA_Activity", 0);
            boolean wasMoving = Boolean.TRUE.equals(p.get( "FreezeA_WasMoving"));
            int minFreeze = 8;
            double minJump = 0.5;
            int minActivity = 1;
            if (wasMoving  && freezeLen  >= minFreeze  && jumpSize  >= minJump  && activity  >= minActivity  && p.get( "riding") == null) {
                vl++;
                if (vl  >= vlThreshold  && now - lastAlert  > cooldown) {
                    p.put( "BacktrackA_LastAlert", now);
                    printFlag(p,  "Grim Backtrack", vl);
                }
            } else {
                vl = Math.max(vl - 1, 0);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        p.put( "BacktrackA_VL", vl);
    }

    void AimGcdA(Map <String, Object > p, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) p.getOrDefault( "AimGcdA_VL", 0);
        long lastAlert = (long) p.getOrDefault( "AimGcdA_LastAlert", 0L);
        if (isReplay() || !inCombatState(p)) {
            p.put( "AimGcdA_VL", Math.max(vl - 1, 0));
            return;
        }
        float yaw = (float) p.get( "yaw");
        float prevYaw = (float) p.get( "previousYaw");
        float pitch = (float) p.get( "pitch");
        float prevPitch = (float) p.get( "previousPitch");
        double deltaYaw = Math.abs(yaw - prevYaw);
        if (deltaYaw  > 180) deltaYaw = 360 - deltaYaw;
        double deltaPitch = Math.abs(pitch - prevPitch);
        List <Double > divisors = (List <Double >) p.get( "AimGcdA_Divisors");
        if (divisors == null) {
            divisors = new ArrayList < >();
            p.put( "AimGcdA_Divisors", divisors);
        }
        // Grim AimProcessor: per-axis divisor = gcd(delta, lastDelta) (Euclid with
        // MINIMUM_DIVISOR termination). Samples only enter when the divisor is a
        // plausible rotation unit, then a RunningMode-style popularity map finds the
        // most frequent divisor. A dominant repeated divisor means every rotation
        // delta snaps to one fixed unit - impossible for a mouse.
        double lastYawDelta = (Double) p.getOrDefault( "AimGcdA_LastYawDelta", 0d);
        double lastPitchDelta = (Double) p.getOrDefault( "AimGcdA_LastPitchDelta", 0d);
        if (deltaYaw  > 0  && deltaYaw  < 5) {
            double divisor = gcdDivisor(deltaYaw, lastYawDelta);
            if (divisor  > 1e-4) divisors.add(divisor);
            p.put( "AimGcdA_LastYawDelta", deltaYaw);
        }
        if (deltaPitch  > 0  && deltaPitch  < 5) {
            double divisor = gcdDivisor(deltaPitch, lastPitchDelta);
            if (divisor  > 1e-4) divisors.add(divisor);
            p.put( "AimGcdA_LastPitchDelta", deltaPitch);
        }
        while (divisors.size()  > 80) divisors.remove(0);
        int samples = 15;
        if (divisors.size()  < samples) {
            p.put( "AimGcdA_VL", Math.max(vl - 1, 0));
            return;
        }
        double mode = 0;
        int modeCount = 0;
        for (int i = 0; i < divisors.size(); i++) {
            double v = divisors.get(i);
            int count = 0;
            for (int j = 0; j < divisors.size(); j++) {
                if (Math.abs(divisors.get(j) - v)  < 0.001) count++;
            }
            if (count  > modeCount) {
                modeCount = count;
                mode = v;
            }
        }
        boolean flagged = false;
        if (modeCount  >= samples) {
            double sens = Math.cbrt(mode / 0.15 / 8.0);
            sens = (sens - 0.2) / 0.6;
            if (mode  < 0.0005 || sens  > 2.0) flagged = true;
        }
        if (flagged) {
            vl++;
            if (vl  >= vlThreshold  && now - lastAlert  > cooldown) {
                p.put( "AimGcdA_LastAlert", now);
                printFlag(p,  "Grim AimGCD", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        p.put( "AimGcdA_VL", vl);
    }

    void GcdB(Map<String, Object> p, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) p.getOrDefault("GcdB_VL", 0);
        long lastAlert = (long) p.getOrDefault("GcdB_LastAlert", 0L);
        if (isReplay() || !inCombatState(p)) { p.put("GcdB_VL", Math.max(vl - 1, 0)); return; }
        // Intave RotationSensitivityHeuristic (ROTATION_SENSITIVITY): a running
        // Euclidean GCD over consecutive PITCH deltas. A legit mouse keeps a stable
        // rotation granularity; aim assist injecting rotations makes the running GCD
        // jump ("rotations are out of sync"). Tuned less strict than Intave's raw
        // values: epsilon 0.005 (was 0.001), sync threshold 0.01 (was 0.001), min
        // pitch move 2.0 (was 1.0), and VL only rises after 3 sustained GCD jumps
        // within 1s - a single jump is just mouse noise.
        float pitch = (float) p.get("pitch");
        float prevPitch = (float) p.get("previousPitch");
        double pitchDifference = Math.abs(pitch - prevPitch);
        double prevPitchGCD = (double) p.getOrDefault("GcdB_PrevGCD", 0.0);
        double pitchA = prevPitchGCD;
        double pitchB = pitchDifference;
        if (pitchB > 0) {
            int countdown = 100;
            double pitchR;
            while ((pitchR = pitchA % pitchB) > Math.max(pitchA, pitchB) * 0.005) {
                pitchA = pitchB;
                pitchB = pitchR;
                if (countdown-- < 0) break;
            }
        }
        double pitchGCD = pitchB;
        double gcdDifference = Math.abs(pitchGCD - prevPitchGCD);
        p.put("GcdB_PrevGCD", pitchGCD);
        int jumps = (int) p.getOrDefault("GcdB_Jumps", 0);
        if (gcdDifference > 0.01 && pitchDifference > 2.0) {
            jumps++;
            if (jumps == 1) p.put("GcdB_FirstJump", now);
            p.put("GcdB_Jumps", jumps);
            long firstJump = (long) p.getOrDefault("GcdB_FirstJump", now);
            if (jumps >= 3 && now - firstJump <= 1000) {
                vl++;
                p.put("GcdB_Jumps", 0);
                if (vl >= vlThreshold && now - lastAlert > cooldown) {
                    p.put("GcdB_LastAlert", now);
                    printFlag(p, "Intave GCD", vl);
                }
            }
        } else {
            if (jumps > 0) p.put("GcdB_Jumps", jumps - 1);
            if (vl > 0) vl--;
        }
        p.put("GcdB_VL", Math.max(vl, 0));
    }

    void DivisorYA(Map<String, Object> p, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) p.getOrDefault("DivisorYA_VL", 0);
        long lastAlert = (long) p.getOrDefault("DivisorYA_LastAlert", 0L);
        if (isReplay() || !inCombatState(p)) { p.put("DivisorYA_VL", Math.max(vl - 1, 0)); return; }
        // Vulcan "DivisorY" (Aim Type S, "Subtle aim assist modifications"): aim
        // assist applies pitch corrections as fixed degree increments (0.15, 0.30,
        // 0.45...), so the pitch deltas snap to exact multiples of a "nice" step.
        // A real mouse produces varying deltas that do not stay quantized to one
        // clean divisor across a whole window.
        float pitch = (float) p.get("pitch");
        float prevPitch = (float) p.get("previousPitch");
        double deltaPitch = Math.abs(pitch - prevPitch);
        List<Long> deltas = (List<Long>) p.get("DivisorYA_Deltas");
        if (deltas == null) {
            deltas = new ArrayList<>();
            p.put("DivisorYA_Deltas", deltas);
        }
        if (deltaPitch > 1e-7 && deltaPitch < 30) deltas.add(Math.round(deltaPitch * 65536.0));
        if (deltas.size() < 12) { p.put("DivisorYA_VL", Math.max(vl - 1, 0)); return; }
        boolean flagged = false;
        double[] steps = {0.1, 0.15, 0.2, 0.25, 0.3, 0.5};
        for (double s : steps) {
            long stepRaw = Math.round(s * 65536.0);
            long tol = Math.round(0.01 * 65536.0);
            int matches = 0;
            List<Integer> multiples = new ArrayList<>();
            for (Long v : deltas) {
                long k = Math.round(v / (double) stepRaw);
                if (k < 1) continue;
                if (Math.abs(v - k * stepRaw) <= tol) {
                    matches++;
                    if (!multiples.contains((int) k)) multiples.add((int) k);
                }
            }
            if (matches >= 10 && multiples.size() >= 3) { flagged = true; break; }
        }
        deltas.clear();
        if (flagged) {
            vl++;
            if (vl >= vlThreshold && now - lastAlert > cooldown) {
                p.put("DivisorYA_LastAlert", now);
                printFlag(p, "Vulcan DivisorY", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        p.put("DivisorYA_VL", vl);
    }

    void RotationSpeedA(Map <String, Object > p, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) p.getOrDefault( "RotationSpeedA_VL", 0);
        long lastAlert = (long) p.getOrDefault( "RotationSpeedA_LastAlert", 0L);
        if (isReplay()) {
            p.put( "RotationSpeedA_VL", Math.max(vl - 1, 0));
            return;
        }
        if (!inCombatState(p)) {
            p.put( "RotationSpeedA_Streak", 0);
            p.put( "RotationSpeedA_VL", Math.max(vl - 1, 0));
            return;
        }
        float yaw = (float) p.get( "yaw");
        float prevYaw = (float) p.get( "previousYaw");
        double delta = Math.abs(yaw - prevYaw);
        if (delta  > 180) delta = 360 - delta;
        // NCP yawrate: a sliding 1-second window scores total rotation against a
        // 290 deg/s limit normalized to elapsed time. Burst flicks and sustained
        // fast tracking both trip it; jitter barely moves the score.
        List<Object[]> rotStream = (List<Object[]>) p.getOrDefault("RotationSpeedA_RotStream", null);
        if (rotStream == null) {
            rotStream = new ArrayList<>();
            p.put("RotationSpeedA_RotStream", rotStream);
        }
        long now = this.now;
        rotStream.add(new Object[]{ now, delta });
        long cutoff = now - 1000;
        int prune = 0;
        while (prune < rotStream.size() && (Long) rotStream.get(prune)[0] < cutoff) prune++;
        if (prune > 0) rotStream.subList(0, prune).clear();
        int streak = (int) p.getOrDefault( "RotationSpeedA_Streak", 0);
        if (rotStream.size() >= 5) {
            double total = 0;
            for (Object[] r : rotStream) total += (Double) r[1];
            long spanMs = (Long) rotStream.get(rotStream.size() - 1)[0] - (Long) rotStream.get(0)[0];
            double rate = spanMs >= 100 ? total / (spanMs / 1000.0) : 0;
            if (rate  > 290) streak++;
            else if (rate  <= 3.0) streak = Math.max(streak - 2, 0);
            else streak = Math.max(streak - 1, 0);
        } else {
            streak = Math.max(streak - 1, 0);
        }
        p.put( "RotationSpeedA_Streak", streak);
        int minStreak = 3;
        if (streak  >= minStreak) {
            p.put( "RotationSpeedA_Streak", 0);
            vl++;
            if (vl  >= vlThreshold  && now - lastAlert  > cooldown) {
                p.put( "RotationSpeedA_LastAlert", now);
                printFlag(p,  "Vulcan RotationSpeed", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        p.put( "RotationSpeedA_VL", vl);
    }

    void AimFlickA(Map <String, Object > p, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) p.getOrDefault( "AimFlickA_VL", 0);
        long lastAlert = (long) p.getOrDefault( "AimFlickA_LastAlert", 0L);
        if (isReplay()) {
            p.put( "AimFlickA_VL", Math.max(vl - 1, 0));
            return;
        }
        if (!inCombatState(p)) {
            p.put( "AimFlickA_Streak", 0);
            p.put( "AimFlickA_VL", Math.max(vl - 1, 0));
            return;
        }
        float yaw = (float) p.get( "yaw");
        float prevYaw = (float) p.get( "previousYaw");
        float pitch = (float) p.get( "pitch");
        float prevPitch = (float) p.get( "previousPitch");
        double deltaYaw = Math.abs(yaw - prevYaw);
        if (deltaYaw  > 180) deltaYaw = 360 - deltaYaw;
        double deltaPitch = Math.abs(pitch - prevPitch);
        double minYaw = 5;
        int streak = (int) p.getOrDefault( "AimFlickA_Streak", 0);
        if (deltaYaw  >= minYaw  && deltaPitch  <= 1e-6) streak++;
        else streak = Math.max(streak - 1, 0);
        p.put( "AimFlickA_Streak", streak);
        int minStreak = 8;
        if (streak  >= minStreak) {
            p.put( "AimFlickA_Streak", 0);
            vl++;
            if (vl  >= vlThreshold  && now - lastAlert  > cooldown) {
                p.put( "AimFlickA_LastAlert", now);
                printFlag(p,  "Vulcan AimFlick", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        p.put( "AimFlickA_VL", vl);
    }

    void PitchLockA(Map <String, Object > p, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) p.getOrDefault( "PitchLockA_VL", 0);
        long lastAlert = (long) p.getOrDefault( "PitchLockA_LastAlert", 0L);
        if (isReplay()) {
            p.put( "PitchLockA_VL", Math.max(vl - 1, 0));
            return;
        }
        RavenItem heldItem = (RavenItem) p.get( "heldItem");
        boolean riding = p.get( "riding") != null;
        int ticksExisted = (int) p.get( "ticksExisted");
        int lastSwing = (int) p.getOrDefault( "lastSwingTick", 0);
        if (!holdingWeapon(heldItem) || riding || ticksExisted - lastSwing  > 8) {
            p.put( "PitchLockA_Streak", Math.max((int) p.getOrDefault( "PitchLockA_Streak", 0) - 1, 0));
            p.put( "PitchLockA_VL", Math.max(vl - 1, 0));
            return;
        }
        RavenVec3 myPos = (RavenVec3) p.get( "position");
        Map <String, Object > targetData = null;
        RavenVec3 targetPos = null;
        double bestDist = 6.0;
        for (Map.Entry <RavenPlayer, Map <String, Object > > e : anticheatPlayers.entrySet()) {
            if (e.getValue() == p) continue;
            RavenVec3 o = (RavenVec3) e.getValue().get( "position");
            if (o != null  && myPos != null) {
                double d = myPos.distanceTo(o);
                if (d  < bestDist) {
                    bestDist = d;
                    targetPos = o;
                    targetData = e.getValue();
                }
            }
        }
        if (targetPos == null || targetData == null || myPos == null) {
            p.put( "PitchLockA_Streak", 0);
            p.put( "PitchLockA_VL", Math.max(vl - 1, 0));
            return;
        }
        RavenVec3 targetLast = (RavenVec3) targetData.get( "lastPosition");
        boolean victimMoving = targetLast != null  && targetPos.distanceTo(targetLast)  > 0.02;
        RavenVec3 eye = myPos.offset(0, 1.62, 0);
        RavenVec3 head = targetPos.offset(0, 1.55, 0);
        double requiredPitch = getPitchTo(eye, head);
        float pitch = (float) p.get( "pitch");
        double miss = Math.abs(pitch - requiredPitch);
        double precision = 0.2;
        int streak = (int) p.getOrDefault( "PitchLockA_Streak", 0);
        if (miss  < precision  && victimMoving) streak++;
        else streak = Math.max(streak - 1, 0);
        p.put( "PitchLockA_Streak", streak);
        int minStreak = 20;
        if (streak  >= minStreak) {
            p.put( "PitchLockA_Streak", 0);
            vl++;
            if (vl  >= vlThreshold  && now - lastAlert  > cooldown) {
                p.put( "PitchLockA_LastAlert", now);
                printFlag(p,  "Karhu PitchLock", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        p.put( "PitchLockA_VL", vl);
    }

    void SilentAimA(Map <String, Object > victimData, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) victimData.getOrDefault( "SilentAimA_VL", 0);
        long lastAlert = (long) victimData.getOrDefault( "SilentAimA_LastAlert", 0L);
        int lastHit = (int) victimData.getOrDefault( "lastHitTick", 0);
        int ticksExisted = (int) victimData.get( "ticksExisted");
        if (lastHit != ticksExisted || isReplay()) {
            victimData.put( "SilentAimA_VL", Math.max(vl - 1, 0));
            return;
        }
        Map <String, Object > attacker = findRecentAttacker(victimData, ticksExisted);
        if (attacker == null) {
            victimData.put( "SilentAimA_VL", Math.max(vl - 1, 0));
            return;
        }
        RavenVec3 attackerPos = (RavenVec3) attacker.get( "position");
        RavenVec3 victimPos = (RavenVec3) victimData.get( "position");
        float yaw = (float) attacker.get( "yaw");
        float prevYaw = (float) attacker.get( "previousYaw");
        float pitch = (float) attacker.get( "pitch");
        float prevPitch = (float) attacker.get( "previousPitch");
        RavenItem heldItem = (RavenItem) attacker.get( "heldItem");
        if (attackerPos == null || victimPos == null || !holdingWeapon(heldItem)) {
            victimData.put( "SilentAimA_VL", Math.max(vl - 1, 0));
            return;
        }
        boolean staticRotation = yaw == prevYaw  && pitch == prevPitch;
        double facing = Math.abs(getFacingAngle(yaw, attackerPos, victimPos));
        if (staticRotation  && facing  > 45) {
            vl++;
            if (vl  >= vlThreshold  && now - lastAlert  > cooldown) {
                victimData.put( "SilentAimA_LastAlert", now);
                printFlag(attacker,  "Matrix SilentAim", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        victimData.put( "SilentAimA_VL", vl);
    }

    void WallHitA(Map <String, Object > victimData, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) victimData.getOrDefault( "WallHitA_VL", 0);
        long lastAlert = (long) victimData.getOrDefault( "WallHitA_LastAlert", 0L);
        int lastHit = (int) victimData.getOrDefault( "lastHitTick", 0);
        int ticksExisted = (int) victimData.get( "ticksExisted");
        if (lastHit != ticksExisted) {
            victimData.put( "WallHitA_VL", Math.max(vl - 1, 0));
            return;
        }
        Map <String, Object > attacker = findRecentAttacker(victimData, ticksExisted);
        RavenVec3 attackerPos = attacker != null ? (RavenVec3) attacker.get( "position") : null;
        RavenVec3 victimPos = (RavenVec3) victimData.get( "position");
        if (attackerPos == null || victimPos == null) {
            victimData.put( "WallHitA_VL", Math.max(vl - 1, 0));
            return;
        }
        RavenVec3 eye = attackerPos.offset(0, 1.35, 0);
        RavenVec3 body = victimPos.offset(0, 0.9, 0);
        int blocks = 3;
        if (solidBlocksBetween(eye, body)  >= blocks) {
            vl++;
            if (vl  >= vlThreshold  && now - lastAlert  > cooldown) {
                victimData.put( "WallHitA_LastAlert", now);
                printFlag(attacker,  "NCP WallHit", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        victimData.put( "WallHitA_VL", vl);
    }

    void HitboxVA(Map <String, Object > victimData, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) victimData.getOrDefault( "HitboxVA_VL", 0);
        long lastAlert = (long) victimData.getOrDefault( "HitboxVA_LastAlert", 0L);
        int lastHit = (int) victimData.getOrDefault( "lastHitTick", 0);
        int ticksExisted = (int) victimData.get( "ticksExisted");
        if (lastHit != ticksExisted) {
            victimData.put( "HitboxVA_VL", Math.max(vl - 1, 0));
            return;
        }
        Map <String, Object > attacker = findRecentAttacker(victimData, ticksExisted);
        RavenVec3 attackerPos = attacker != null ? (RavenVec3) attacker.get( "position") : null;
        RavenVec3 victimPos = (RavenVec3) victimData.get( "position");
        if (attackerPos == null || victimPos == null) {
            victimData.put( "HitboxVA_VL", Math.max(vl - 1, 0));
            return;
        }
        double dx = victimPos.x - attackerPos.x;
        double dz = victimPos.z - attackerPos.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double eyeY =  attackerPos.y + 1.35;
        double offset = 0.7;
        if (horizontal  < 4.0  && eyeY  < victimPos.y - offset) {
            vl++;
            if (vl  >= vlThreshold  && now - lastAlert  > cooldown) {
                victimData.put( "HitboxVA_LastAlert", now);
                printFlag(attacker,  "NCP HitboxV", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        victimData.put( "HitboxVA_VL", vl);
    }

    void FastHitA(Map <String, Object > p, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) p.getOrDefault( "FastHitA_VL", 0);
        long lastAlert = (long) p.getOrDefault( "FastHitA_LastAlert", 0L);
        List <Integer > ticks = (List <Integer >) p.get( "FastHitA_Ticks");
        int streak = 8;
        int ticksExisted = (int) p.get( "ticksExisted");
        if (ticks != null  && ticks.size()  >= streak  && ticksExisted - ticks.get(0)  <= 4) {
            boolean perfect = true;
            for (int i = 0; i  < streak  && perfect; i++) {
                if (ticks.get(i) - ticks.get(i + 1) != 1) perfect = false;
            }
            if (perfect) {
                vl++;
                if (vl  >= vlThreshold  && now - lastAlert  > cooldown) {
                    p.put( "FastHitA_LastAlert", now);
                    printFlag(p,  "Karhu FastHit", vl);
                }
            } else {
                vl = Math.max(vl - 1, 0);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        p.put( "FastHitA_VL", vl);
    }

    void VelocityDirA(Map <String, Object > p, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) p.getOrDefault( "VelocityDirA_VL", 0);
        long lastAlert = (long) p.getOrDefault( "VelocityDirA_LastAlert", 0L);
        int lastHit = (int) p.getOrDefault( "lastHitTick", 0);
        int ticksExisted = (int) p.get( "ticksExisted");
        boolean active = Boolean.TRUE.equals(p.get( "VelocityDirA_Active"));
        boolean checked = Boolean.TRUE.equals(p.get( "VelocityDirA_Checked"));
        if (lastHit == ticksExisted) {
            Map <String, Object > attacker = findRecentAttacker(p, ticksExisted);
            RavenVec3 attackerPos = attacker != null ? (RavenVec3) attacker.get( "position") : null;
            RavenVec3 victimPos = (RavenVec3) p.get( "position");
            boolean inWater = Boolean.TRUE.equals(p.get( "inWater"));
            boolean inLava = Boolean.TRUE.equals(p.get( "inLava"));
            boolean riding = p.get( "riding") != null;
            boolean onGround = Boolean.TRUE.equals(p.get( "onGround"));
            String below = world.getBlockAt((int) Math.floor(victimPos.x), (int) Math.floor(victimPos.y) - 1, (int) Math.floor(victimPos.z)).name;
            String feet = world.getBlockAt((int) Math.floor(victimPos.x), (int) Math.floor(victimPos.y), (int) Math.floor(victimPos.z)).name;
            boolean inWeb = feet.equals( "web") || below.equals( "web");
            if (attackerPos != null  && victimPos != null  && !inWater  && !inLava  && !riding  && !inWeb  && !below.equals( "slime")  && onGround) {
                p.put( "VelocityDirA_Active", true);
                p.put( "VelocityDirA_Checked", false);
                p.put( "VelocityDirA_AttackerX", attackerPos.x);
                p.put( "VelocityDirA_AttackerZ", attackerPos.z);
                p.put( "VelocityDirA_StartX", victimPos.x);
                p.put( "VelocityDirA_StartZ", victimPos.z);
            } else {
                p.put( "VelocityDirA_Active", false);
            }
            vl = Math.max(vl - 1, 0);
            p.put( "VelocityDirA_VL", vl);
            return;
        }
        if (!active) {
            p.put( "VelocityDirA_VL", Math.max(vl - 1, 0));
            return;
        }
        int since = ticksExisted - lastHit;
        if (since == 2 || since == 3) {
            if (!checked) {
                RavenVec3 pos = (RavenVec3) p.get( "position");
                boolean onGround = Boolean.TRUE.equals(p.get( "onGround"));
                if (onGround  && pos != null) {
                    double startX = (Double) p.get( "VelocityDirA_StartX");
                    double startZ = (Double) p.get( "VelocityDirA_StartZ");
                    double attackerX = (Double) p.get( "VelocityDirA_AttackerX");
                    double attackerZ = (Double) p.get( "VelocityDirA_AttackerZ");
                    double dispX = pos.x - startX;
                    double dispZ = pos.z - startZ;
                    double awayX = startX - attackerX;
                    double awayZ = startZ - attackerZ;
                    double awayLen = Math.sqrt(awayX * awayX + awayZ * awayZ);
                    double disp = Math.sqrt(dispX * dispX + dispZ * dispZ);
                    double dotThreshold = -0.3;
                    if (disp  > 0.04  && awayLen  > 0.01) {
                        double dot = (dispX * awayX + dispZ * awayZ) / awayLen;
                        p.put( "VelocityDirA_Checked", true);
                        if (dot  < dotThreshold) {
                            vl++;
                            if (vl  >= vlThreshold  && now - lastAlert  > cooldown) {
                                p.put( "VelocityDirA_LastAlert", now);
                                printFlag(p,  "Verus VelocityDir", vl);
                            }
                        } else {
                            vl = Math.max(vl - 1, 0);
                        }
                    }
                }
            }
        }
        if (since  > 5) p.put( "VelocityDirA_Active", false);
        p.put( "VelocityDirA_VL", vl);
    }

    void CriticalsA(Map <String, Object > p, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) p.getOrDefault( "CriticalsA_VL", 0);
        long lastAlert = (long) p.getOrDefault( "CriticalsA_LastAlert", 0L);
        RavenItem heldItem = (RavenItem) p.get( "heldItem");
        boolean riding = p.get( "riding") != null;
        int ticksExisted = (int) p.get( "ticksExisted");
        int lastSwing = (int) p.getOrDefault( "lastSwingTick", 0);
        int lastOnGroundTick = (int) p.getOrDefault( "lastOnGroundTick", 0);
        boolean onGround = Boolean.TRUE.equals(p.get( "onGround"));
        int streak = (int) p.getOrDefault( "CriticalsA_Streak", 0);
        // NCP Critical: a critical is only legal during the 6-tick jump phase (after
        // leaving the ground). Every hit landing within that window - repeated 6+
        // times - means every swing was a guaranteed crit, which is automated.
        if (!riding  && holdingWeapon(heldItem)  && lastSwing == ticksExisted  && !onGround  && lastOnGroundTick > 0  && ticksExisted - lastOnGroundTick <= 6) {
            streak++;
        } else {
            streak = Math.max(streak - 1, 0);
        }
        p.put( "CriticalsA_Streak", streak);
        int minStreak = 6;
        if (streak  >= minStreak) {
            p.put( "CriticalsA_Streak", 0);
            vl++;
            if (vl  >= vlThreshold  && now - lastAlert  > cooldown) {
                p.put( "CriticalsA_LastAlert", now);
                printFlag(p,  "Vulcan Criticals", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        p.put( "CriticalsA_VL", vl);
    }

    void AutoClickerStatA(Map <String, Object > p, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) p.getOrDefault( "AutoClickerStatA_VL", 0);
        long lastAlert = (long) p.getOrDefault( "AutoClickerStatA_LastAlert", 0L);
        RavenItem heldItem = (RavenItem) p.get( "heldItem");
        boolean riding = p.get( "riding") != null;
        List <Integer > swings = (List <Integer >) p.get( "swingTimes");
        int ticksExisted = (int) p.get( "ticksExisted");
        if (!holdingWeapon(heldItem) || riding || swings == null || swings.size()  < 12 || ticksExisted - swings.get(swings.size() - 1)  > 4) {
            p.put( "AutoClickerStatA_VL", Math.max(vl - 1, 0));
            return;
        }
        double sum = 0;
        int maxInterval = 0;
        int n = swings.size() - 1;
        for (int i = 1; i  < swings.size(); i++) {
            int interval = Math.abs(swings.get(i) - swings.get(i - 1));
            sum += interval;
            if (interval  > maxInterval) maxInterval = interval;
        }
        double mean = sum / n;
        double sqSum = 0;
        for (int i = 1; i  < swings.size(); i++) {
            double diff = Math.abs(swings.get(i) - swings.get(i - 1)) - mean;
            sqSum += diff * diff;
        }
        double stddev = Math.sqrt(sqSum / n);
        double stdThreshold = 0.4;
        int maxThreshold = 4;
        if (mean  <= 2.5  && stddev  < stdThreshold  && maxInterval  <= maxThreshold) {
            vl++;
            if (vl  >= vlThreshold  && now - lastAlert  > cooldown) {
                p.put( "AutoClickerStatA_LastAlert", now);
                printFlag(p,  "Matrix AutoClicker", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        p.put( "AutoClickerStatA_VL", vl);
    }

    void MultiAuraB(Map <String, Object > victimData, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) victimData.getOrDefault( "MultiAuraB_VL", 0);
        long lastAlert = (long) victimData.getOrDefault( "MultiAuraB_LastAlert", 0L);
        int lastHit = (int) victimData.getOrDefault( "lastHitTick", 0);
        int ticksExisted = (int) victimData.get( "ticksExisted");
        if (lastHit != ticksExisted || isReplay()) {
            victimData.put( "MultiAuraB_VL", Math.max(vl - 1, 0));
            return;
        }
        Map <String, Object > attacker = findRecentAttacker(victimData, ticksExisted);
        if (attacker == null) {
            victimData.put( "MultiAuraB_VL", Math.max(vl - 1, 0));
            return;
        }
        // Attribution guard: only credit a hit to the attacker if they actually
        // swung on this tick (or the previous one). In a 2v1 the victim's hit
        // ticks come from several players and findRecentAttacker always picks
        // the nearest swinger, so without this check one attacker gets credited
        // with everyone's hits - the main MultiAuraB false-positive source.
        int attackerSwingTick = (int) attacker.getOrDefault("lastSwingTick", 0);
        if (ticksExisted - attackerSwingTick > 1) {
            victimData.put( "MultiAuraB_VL", Math.max(vl - 1, 0));
            return;
        }
        List <Object[] > hits = (List <Object[] >) attacker.get( "MultiAuraB_Hits");
        if (hits == null) {
            hits = new ArrayList < >();
            attacker.put( "MultiAuraB_Hits", hits);
        }
        RavenVec3 attackerPos = (RavenVec3) attacker.get( "position");
        float yaw = (float) attacker.get( "yaw");
        hits.add(new Object[]{ ticksExisted, victimData.get( "name"), victimData.get( "position"), yaw, attackerPos });
        int window = 20;
        while (!hits.isEmpty()  && ticksExisted - (Integer) hits.get(0)[0]  > window) {
            hits.remove(0);
        }
        // Negativity ForceField "packet": more than 16 swings AND more than 20
        // attacks per second. The AND is required - a fast legit clicker swings
        // often but cannot land 20 hits/s on entities (vanilla max is a few/s).
        List<Integer> hitTimes = (List<Integer>) attacker.get("MultiAuraB_HitTimes");
        if (hitTimes == null) {
            hitTimes = new ArrayList<>();
            attacker.put("MultiAuraB_HitTimes", hitTimes);
        }
        hitTimes.add(ticksExisted);
        while (!hitTimes.isEmpty() && ticksExisted - hitTimes.get(0) > 20) hitTimes.remove(0);
        List<Integer> attackerSwings = (List<Integer>) attacker.get("swingTimes");
        int swingsInSecond = 0;
        if (attackerSwings != null) {
            for (int st : attackerSwings) {
                if (ticksExisted - st <= 20) swingsInSecond++;
            }
        }
        if (swingsInSecond > 16 && hitTimes.size() > 20) {
            vl += 2;
            if (vl >= vlThreshold && now - lastAlert > cooldown) {
                victimData.put("MultiAuraB_LastAlert", now);
                printFlag(attacker, "Grim MultiAuraB", vl);
            }
            victimData.put("MultiAuraB_VL", vl);
            return;
        }
        HashSet <String > victims = new HashSet < >();
        HashMap <String, RavenVec3 > victimPositions = new HashMap < >();
        for (Object[] hit : hits) {
            victims.add((String) hit[1]);
            victimPositions.put((String) hit[1], (RavenVec3) hit[2]);
        }
        // Burst requirement: the angle analysis only runs when two DIFFERENT
        // victims were hit within 2 ticks of each other - a real multi-target
        // burst. Hits spread slowly across a group fight are legit and ignored.
        boolean burst = false;
        for (int i = 0; i < hits.size() && !burst; i++) {
            for (int j = i + 1; j < hits.size(); j++) {
                if (!((String) hits.get(i)[1]).equals((String) hits.get(j)[1])
                && Math.abs((Integer) hits.get(i)[0] - (Integer) hits.get(j)[0]) <= 2) {
                    burst = true;
                    break;
                }
            }
        }
        if (!burst || hits.size() < 4) {
            victimData.put( "MultiAuraB_VL", Math.max(vl - 1, 0));
            return;
        }
        if (attackerPos == null) {
            victimData.put( "MultiAuraB_VL", Math.max(vl - 1, 0));
            return;
        }
        // NCP Angle: attack statistics over the last second instead of a single
        // rotation snapshot. Averages of movement between attacks, attack cadence,
        // rotation jumps and target-switch speed are scored independently.
        int n = hits.size() - 1;
        double sumMove = 0, sumTime = 0, sumYaw = 0, sumSwitch = 0;
        for (int i = 1; i < hits.size(); i++) {
            Object[] cur = hits.get(i);
            Object[] prev = hits.get(i - 1);
            RavenVec3 cPos = (RavenVec3) cur[4];
            RavenVec3 pPos = (RavenVec3) prev[4];
            if (cPos == null || pPos == null) continue;
            double dx = cPos.x - pPos.x;
            double dy = cPos.y - pPos.y;
            double dz = cPos.z - pPos.z;
            sumMove += dx * dx + dy * dy + dz * dz;
            double yawDiff = Math.abs((Float) cur[3] - (Float) prev[3]);
            if (yawDiff > 180) yawDiff = 360 - yawDiff;
            sumYaw += yawDiff;
            sumTime += (Integer) cur[0] - (Integer) prev[0];
            if (!((String) cur[1]).equals((String) prev[1]) && yawDiff > 30) sumSwitch += 1.0;
        }
        double avgMove = sumMove / n;
        double avgTime = sumTime / n * 50.0;
        double avgYaw = sumYaw / n;
        double avgSwitch = sumSwitch / n;
        double vMove = avgMove < 0.1 ? 20.0 * (0.1 - avgMove) / 0.1 : 0;
        double vTime = avgTime < 130.0 ? 30.0 * (130.0 - avgTime) / 130.0 : 0;
        double vYaw = avgYaw > 60.0 ? 30.0 * avgYaw / 180.0 : 0;
        double vSwitch = avgSwitch > 0.0 ? 20.0 * avgSwitch : 0;
        double violation = Math.max(Math.max(vMove, vTime), Math.max(vYaw, vSwitch));
        if (vMove > 2.0 || vTime > 3.0 || vYaw > 3.0 || vSwitch > 2.0) {
            vl += (int) Math.ceil(violation);
            if (vl  >= vlThreshold  && now - lastAlert  > cooldown) {
                victimData.put( "MultiAuraB_LastAlert", now);
                printFlag(attacker,  "Grim MultiAuraB", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        victimData.put( "MultiAuraB_VL", vl);
    }

    void StrafeA(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "StrafeA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "StrafeA_LastAlert", 0L);
        RavenVec3 position = (RavenVec3) anticheatPlayer.get( "position");
        RavenVec3 lastPosition = (RavenVec3) anticheatPlayer.get( "lastPosition");
        if (position == null || lastPosition == null) {
            anticheatPlayer.put("StrafeA_VL", Math.max(vl - 1, 0));
            return;
        }
        boolean sprinting = Boolean.TRUE.equals(anticheatPlayer.get( "sprinting"));
        boolean onGround = Boolean.TRUE.equals(anticheatPlayer.get( "onGround"));
        boolean crouching = Boolean.TRUE.equals(anticheatPlayer.get( "crouching"));
        boolean using = Boolean.TRUE.equals(anticheatPlayer.get( "using"));
        boolean riding = anticheatPlayer.get( "riding") != null;
        boolean inWater = Boolean.TRUE.equals(anticheatPlayer.get( "inWater"));
        boolean inLava = Boolean.TRUE.equals(anticheatPlayer.get( "inLava"));
        boolean onLadder = Boolean.TRUE.equals(anticheatPlayer.get( "onLadder"));
        boolean creative = Boolean.TRUE.equals(anticheatPlayer.get( "creative"));
        double deltaX = position.x - lastPosition.x;
        double deltaZ = position.z - lastPosition.z;
        double moved = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        if (!sprinting || !onGround || crouching || using || riding || inWater || inLava || onLadder || creative || moved < 0.03) {
            anticheatPlayer.put("StrafeA_StrafeTicks", 0);
            anticheatPlayer.put("StrafeA_VL", Math.max(vl - 1, 0));
            return;
        }
        // Grim Strafe: while sprinting, vanilla physics cap the angle between the
        // movement direction and the look direction at ~45 degrees. Moving at a
        // steeper angle while sprinting means a strafe/keep-sprint cheat that
        // ignores the sprint direction lock. Needs to exceed the legit cap
        // consistently (6+ ticks) - a single odd tick is never enough.
        float moveYaw = getMoveYaw(deltaX, deltaZ, (float) anticheatPlayer.get( "yaw"));
        double angle = Math.abs(moveYaw);
        int strafeTicks = (int) anticheatPlayer.getOrDefault( "StrafeA_StrafeTicks", 0);
        if (angle > 55) {
            strafeTicks++;
            if (strafeTicks >= 6) {
                vl += 2;
                if (vl >= vlThreshold && now - lastAlert > cooldown) {
                    anticheatPlayer.put("StrafeA_LastAlert", now);
                    printFlag(anticheatPlayer, "Grim Strafe", vl);
                }
            }
        } else {
            strafeTicks = Math.max(strafeTicks - 1, 0);
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("StrafeA_StrafeTicks", strafeTicks);
        anticheatPlayer.put("StrafeA_VL", vl);
    }

    void FastEatA(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "FastEatA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "FastEatA_LastAlert", 0L);
        int ticksExisted = (int) anticheatPlayer.get( "ticksExisted");
        boolean using = Boolean.TRUE.equals(anticheatPlayer.get( "using"));
        boolean riding = anticheatPlayer.get( "riding") != null;
        boolean creative = Boolean.TRUE.equals(anticheatPlayer.get( "creative"));
        boolean hurt = (int) anticheatPlayer.getOrDefault( "hurtTime", 0) > 0;
        RavenItem held = (RavenItem) anticheatPlayer.get( "heldItem");
        boolean food = held != null && held.type != null && (held.type.startsWith( "ItemFood") || held.type.startsWith( "ItemApple"));
        int eatStart = (int) anticheatPlayer.getOrDefault( "FastEatA_StartTick", 0);
        // NCP FastEat: eating (golden apples etc.) takes 32 ticks in vanilla.
        // An eat that completes in under a second is a fast-eat cheat. Getting
        // hit cancels the eat legitimately - interrupted eats never flag.
        if (food && using && !riding && !creative) {
            if (eatStart == 0) {
                anticheatPlayer.put("FastEatA_StartTick", ticksExisted);
                anticheatPlayer.put("FastEatA_RequiredTicks", held.useTicks);
            }
            return;
        }
        if (eatStart > 0 && !using) {
            anticheatPlayer.put("FastEatA_StartTick", 0);
            int duration = ticksExisted - eatStart;
            RavenPlayer entity = (RavenPlayer)anticheatPlayer.get("entity");
            int required = (int)anticheatPlayer.getOrDefault("FastEatA_RequiredTicks", 32);
            // Releasing use is not completion; modern foods also have different use times.
            if (entity != null && entity.consumed && !hurt && duration > 1 && duration < Math.min(20, required * 0.625)) {
                vl += 2;
                if (vl >= vlThreshold && now - lastAlert > cooldown) {
                    anticheatPlayer.put("FastEatA_LastAlert", now);
                    printFlag(anticheatPlayer, "NCP FastEat", vl);
                }
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("FastEatA_VL", vl);
    }

    void AirSpeedA(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "AirSpeedA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "AirSpeedA_LastAlert", 0L);
        RavenVec3 position = (RavenVec3) anticheatPlayer.get( "position");
        RavenVec3 lastPosition = (RavenVec3) anticheatPlayer.get( "lastPosition");
        boolean riding = anticheatPlayer.get( "riding") != null;
        boolean creative = Boolean.TRUE.equals(anticheatPlayer.get( "creative"));
        boolean inWater = Boolean.TRUE.equals(anticheatPlayer.get( "inWater"));
        boolean inLava = Boolean.TRUE.equals(anticheatPlayer.get( "inLava"));
        boolean onLadder = Boolean.TRUE.equals(anticheatPlayer.get( "onLadder"));
        int hurtTime = (int) anticheatPlayer.getOrDefault( "hurtTime", 0);
        int lastHit = (int) anticheatPlayer.getOrDefault( "lastHitTick", 0);
        int ticksExisted = (int) anticheatPlayer.get( "ticksExisted");
        if (position == null || lastPosition == null || riding || creative || inWater || inLava || onLadder || hurtTime > 0 || (lastHit > 0 && ticksExisted - lastHit <= 10)) {
            anticheatPlayer.put("AirSpeedA_AirTicks", 0);
            anticheatPlayer.put("AirSpeedA_VL", Math.max(vl - 1, 0));
            return;
        }
        // Grim AirSpeed: per-tick horizontal speed while airborne. Legit sprint-jump
        // airspeed peaks around 0.36 blocks/tick and decays; 0.42+ sustained
        // (scaled by Speed potion) is an air-speed cheat. The 20-tick window checks
        // can miss short air bursts - this catches the per-tick profile with a
        // small sustain buffer.
        if (!isAirborne(anticheatPlayer, position) || isInVoid(position)) {
            anticheatPlayer.put("AirSpeedA_AirTicks", 0);
            anticheatPlayer.put("AirSpeedA_VL", Math.max(vl - 1, 0));
            return;
        }
        double dx = position.x - lastPosition.x;
        double dz = position.z - lastPosition.z;
        double speed = Math.sqrt(dx * dx + dz * dz);
        double cap = 0.42;
        List<Object[]> effects = (List<Object[]>) anticheatPlayer.get("potionEffects");
        if (effects != null) {
            for (Object[] effect : effects) {
                if ((Integer) effect[0] == 1) cap *= 1.0 + ((Integer) effect[2] + 1) * 0.2;
            }
        }
        int airTicks = (int) anticheatPlayer.getOrDefault( "AirSpeedA_AirTicks", 0);
        if (speed > cap) {
            airTicks++;
            if (airTicks >= 8) {
                vl += 2;
                if (vl >= vlThreshold && now - lastAlert > cooldown) {
                    anticheatPlayer.put("AirSpeedA_LastAlert", now);
                    printFlag(anticheatPlayer, "Grim AirSpeed", vl);
                }
            }
        } else {
            airTicks = Math.max(airTicks - 1, 0);
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("AirSpeedA_AirTicks", airTicks);
        anticheatPlayer.put("AirSpeedA_VL", vl);
    }

    void RegenA(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "RegenA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "RegenA_LastAlert", 0L);
        int ticksExisted = (int) anticheatPlayer.get( "ticksExisted");
        RavenVec3 position = (RavenVec3) anticheatPlayer.get( "position");
        if (position == null) {
            anticheatPlayer.put("RegenA_VL", Math.max(vl - 1, 0));
            return;
        }
        float health = (float) anticheatPlayer.getOrDefault( "health", 0f);
        boolean using = Boolean.TRUE.equals(anticheatPlayer.get( "using"));
        boolean riding = anticheatPlayer.get( "riding") != null;
        boolean creative = Boolean.TRUE.equals(anticheatPlayer.get( "creative"));
        int hurtTime = (int) anticheatPlayer.getOrDefault( "hurtTime", 0);
        float lastHealth = (float) anticheatPlayer.getOrDefault( "RegenA_LastHealth", health);
        int lastRegenTick = (int) anticheatPlayer.getOrDefault( "RegenA_LastRegenTick", 0);
        int regenTicks = (int) anticheatPlayer.getOrDefault( "RegenA_RegenTicks", 0);
        boolean regenPotion = false;
        List<Object[]> effects = (List<Object[]>) anticheatPlayer.get("potionEffects");
        if (effects != null) {
            for (Object[] effect : effects) {
                if ((Integer) effect[0] == 10) regenPotion = true;
            }
        }
        // NCP Regen: health jumping up without a food item, a regen potion, a bed
        // (BedWars beds regenerate you) or getting hit. Legit natural regen adds
        // about 1 HP per 4 seconds - 0.5+ HP in a single tick is a regen cheat.
        boolean onBed = false;
        String blockOn = world.getBlockAt((int) Math.floor(position.x), (int) Math.floor(position.y) - 1, (int) Math.floor(position.z)).name;
        String feet = world.getBlockAt((int) Math.floor(position.x), (int) Math.floor(position.y), (int) Math.floor(position.z)).name;
        if (blockOn != null && blockOn.contains("bed")) onBed = true;
        if (feet != null && feet.contains("bed")) onBed = true;
        if (!using && !riding && !creative && !regenPotion && !onBed && hurtTime == 0 && health > lastHealth + 0.5f) {
            if (lastRegenTick == 0 || ticksExisted - lastRegenTick <= 10) {
                regenTicks++;
                anticheatPlayer.put("RegenA_LastRegenTick", ticksExisted);
                if (regenTicks >= 4) {
                    regenTicks = 0;
                    vl += 2;
                    if (vl >= vlThreshold && now - lastAlert > cooldown) {
                        anticheatPlayer.put("RegenA_LastAlert", now);
                        printFlag(anticheatPlayer, "NCP Regen", vl);
                    }
                }
            }
        } else {
            regenTicks = Math.max(regenTicks - 1, 0);
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("RegenA_LastHealth", health);
        anticheatPlayer.put("RegenA_RegenTicks", regenTicks);
        anticheatPlayer.put("RegenA_VL", vl);
    }

    void FastInventoryA(Map <String, Object > anticheatPlayer, int cooldown, int vlThreshold, boolean alerts) {
        int vl = (int) anticheatPlayer.getOrDefault( "FastInventoryA_VL", 0);
        long lastAlert = (long) anticheatPlayer.getOrDefault( "FastInventoryA_LastAlert", 0L);
        int ticksExisted = (int) anticheatPlayer.get( "ticksExisted");
        boolean using = Boolean.TRUE.equals(anticheatPlayer.get( "using"));
        boolean riding = anticheatPlayer.get( "riding") != null;
        boolean creative = Boolean.TRUE.equals(anticheatPlayer.get( "creative"));
        List<Integer> changeTimes = (List<Integer>) anticheatPlayer.get("itemChangeTimes");
        if (using || riding || creative || changeTimes == null || changeTimes.isEmpty()) {
            anticheatPlayer.put("FastInventoryA_VL", Math.max(vl - 1, 0));
            return;
        }
        // Karhu FastInventory: slot macros (auto-armor, tool spam) swap items far
        // faster than a hand can - 9+ swaps inside a 20-tick window. Manual
        // BedWars hotkey play stays around 4 swaps per second.
        int changes = 0;
        for (int t : changeTimes) {
            if (ticksExisted - t <= 20) changes++;
        }
        if (changes >= 9) {
            vl++;
            if (vl >= vlThreshold && now - lastAlert > cooldown) {
                anticheatPlayer.put("FastInventoryA_LastAlert", now);
                printFlag(anticheatPlayer, "Karhu FastInventory", vl);
            }
        } else {
            vl = Math.max(vl - 1, 0);
        }
        anticheatPlayer.put("FastInventoryA_VL", vl);
    }
}
