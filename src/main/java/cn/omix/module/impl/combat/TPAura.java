package cn.omix.module.impl.combat;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.MotionEvent;
import cn.omix.event.impl.MoveInputEvent;
import cn.omix.event.impl.Render3DEvent;
import cn.omix.event.impl.UpdateEvent;
import cn.omix.event.impl.WorldEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.impl.move.Fly;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.module.value.impl.ModeValue;
import cn.omix.module.value.impl.NumberValue;
import cn.omix.util.misc.TimerUtil;
import cn.omix.util.network.PacketUtil;
import cn.omix.util.player.EntityUtil;
import cn.omix.util.player.RotationUtil;
import cn.omix.util.player.pathfinder.MainPathFinder;
import cn.omix.util.player.pathfinder.PathFinder;
import lombok.Getter;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Getter
public final class TPAura extends Module {
    private static final double MAX_ATTACK_DISTANCE = 2.75;
    private static final double PATH_END_TOLERANCE = 1.75;
    private static final int ATTACK_POSITION_RADIUS = 2;
    private static final int MAX_MULTI_TARGETS = 20;

    private final NumberValue range = new NumberValue("Range", 30, 3, 100, 1);
    private final NumberValue delay = new NumberValue("Delay", 500, 0, 2000, 50);
    private final BoolValue instant = new BoolValue("Instant", true);
    private final ModeValue targetMode = new ModeValue("Target Mode", "Single", "Single", "Switch", "Multi");
    private final NumberValue maxTargets = new NumberValue(
            "Max Targets",
            5,
            1,
            MAX_MULTI_TARGETS,
            1,
            () -> targetMode.is("Multi")
    );
    private final ModeValue priority = new ModeValue(
            "Priority",
            "Distance",
            "Distance",
            "Health",
            "Fov",
            "LivingTime",
            "Armor"
    );
    private final BoolValue respectCooldown = new BoolValue("Respect Cooldown", true);
    private final BoolValue rotation = new BoolValue("Rotation", false);
    private final BoolValue onGroundPacket = new BoolValue("On Ground Packet", true);

    private final TimerUtil attackTimer = new TimerUtil();
    private LivingEntity target;
    private ArrayList<Vec3d> renderedOutboundPath = new ArrayList<>();
    private ArrayList<Vec3d> renderedReturnPath = new ArrayList<>();
    private ArrayList<Vec3d> activeOutboundPath = new ArrayList<>();
    private ArrayList<Vec3d> activeReturnPath = new ArrayList<>();
    private LivingEntity activeTarget;
    private Vec3d attackOrigin;
    private Vec3d attackPosition;
    private int outboundPathIndex;
    private int returnPathIndex;
    private boolean blinkAttackActive;
    private boolean attacked;
    private LivingEntity switchTarget;
    private int switchIndex;
    private final ArrayList<RouteLeg> activeMultiLegs = new ArrayList<>();
    private int multiLegIndex;
    private int multiPathIndex;

    public TPAura() {
        super("TPAura", Category.Combat);
    }

    @Override
    public void onEnable() {
        reset();
        attackTimer.setTime(0L);
    }

    @Override
    public void onDisable() {
        stopBlinkAttack(true);
        reset();
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!canRun()) {
            stopBlinkAttack(false);
            target = null;
            setSuffix("");
            return;
        }

        if (blinkAttackActive) {
            lockClientPosition();
            updateBlinkAttack();
            return;
        }

        if (targetMode.is("Multi")) {
            List<LivingEntity> candidates = getCandidates();
            target = candidates.isEmpty() ? null : candidates.getFirst();
            switchTarget = null;
            switchIndex = 0;
            setSuffix(target == null ? "" : "Multi");
            if (target == null
                    || !attackTimer.hasTimeElapsed(delay.getValue())
                    || respectCooldown.getValue() && mc.player.getAttackCooldownProgress(0.5F) < 1.0F) {
                return;
            }

            if (!beginMultiAttack(candidates)) {
                attackTimer.reset();
            }
            return;
        }

        target = selectTarget();
        setSuffix(target == null ? "" : targetMode.getValue());
        if (target == null
                || !attackTimer.hasTimeElapsed(delay.getValue())
                || respectCooldown.getValue() && mc.player.getAttackCooldownProgress(0.5F) < 1.0F) {
            return;
        }

        if (!beginBlinkAttack(target)) {
            advanceSwitchTarget(target);
            attackTimer.reset();
        }
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        stopBlinkAttack(false);
        reset();
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (blinkAttackActive && !event.isPost()) {
            event.setCancelled();
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (!blinkAttackActive) return;

        event.setForward(0.0F);
        event.setStrafe(0.0F);
        event.setJumping(false);
        event.setSneaking(false);
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        cn.omix.module.impl.exploits.PathFinder.renderPath(event, renderedOutboundPath);
        cn.omix.module.impl.exploits.PathFinder.renderPath(event, renderedReturnPath);
    }

    private LivingEntity selectTarget() {
        List<LivingEntity> candidates = getCandidates();
        if (candidates.isEmpty()) {
            switchTarget = null;
            switchIndex = 0;
            return null;
        }

        if (!targetMode.is("Switch")) {
            switchTarget = null;
            switchIndex = 0;
            return candidates.getFirst();
        }

        if (switchTarget != null && candidates.contains(switchTarget)) {
            return switchTarget;
        }

        switchIndex = Math.floorMod(switchIndex, candidates.size());
        switchTarget = candidates.get(switchIndex);
        return switchTarget;
    }

    private List<LivingEntity> getCandidates() {
        double rangeSquared = range.getValue() * range.getValue();
        return instance.getTargetManager().getTargets().stream()
                .filter(this::isValidTarget)
                .filter(entity -> mc.player.getEntityPos().squaredDistanceTo(entity.getEntityPos()) <= rangeSquared)
                .sorted(targetComparator())
                .toList();
    }

    private boolean isValidTarget(LivingEntity entity) {
        return entity != null
                && !entity.isRemoved()
                && entity.isAlive()
                && entity.getHealth() > 0.0F
                && EntityUtil.isSelected(entity);
    }

    private Comparator<LivingEntity> targetComparator() {
        return switch (priority.getValue()) {
            case "Health" -> Comparator.comparingDouble(
                    entity -> entity.getHealth() + entity.getAbsorptionAmount()
            );
            case "Fov" -> Comparator.comparingDouble(RotationUtil::getRotationDifference);
            case "LivingTime" -> Comparator.comparingInt((LivingEntity entity) -> entity.age).reversed();
            case "Armor" -> Comparator.comparingInt(LivingEntity::getArmor);
            default -> Comparator.comparingDouble(
                    entity -> mc.player.getEntityPos().squaredDistanceTo(entity.getEntityPos())
            );
        };
    }

    private boolean beginBlinkAttack(LivingEntity entity) {
        renderedOutboundPath.clear();
        renderedReturnPath.clear();

        Vec3d origin = mc.player.getEntityPos();
        Vec3d attackPosition = findAttackPosition(entity, origin);
        if (attackPosition == null) return false;

        // The return route is intentionally recomputed from the remote attack
        // position. It must not be a reversal of the outbound route because the
        // configured path finder can produce asymmetric or partial paths.
        ArrayList<Vec3d> outboundPath = MainPathFinder.computePath(origin, attackPosition);
        if (!pathReaches(outboundPath, origin, attackPosition)) return false;

        ArrayList<Vec3d> returnPath = MainPathFinder.computePath(attackPosition, origin);
        if (!pathReaches(returnPath, attackPosition, origin)) return false;

        renderedOutboundPath = withEndpoints(origin, outboundPath, attackPosition);
        renderedReturnPath = withEndpoints(attackPosition, returnPath, origin);

        activeOutboundPath = withEndpoint(outboundPath, attackPosition);
        activeReturnPath = withEndpoint(returnPath, origin);
        activeTarget = entity;
        attackOrigin = origin;
        this.attackPosition = attackPosition;
        outboundPathIndex = 0;
        returnPathIndex = 0;
        attacked = false;
        blinkAttackActive = true;
        if (!instant.getValue()) {
            mc.player.setVelocity(Vec3d.ZERO);
        }

        releaseSentinelAFlyBlink();
        instance.getPacketManager().getBlink().start(this);
        updateBlinkAttack();
        return true;
    }

    private boolean beginMultiAttack(List<LivingEntity> candidates) {
        renderedOutboundPath.clear();
        renderedReturnPath.clear();

        Vec3d origin = mc.player.getEntityPos();
        int targetLimit = Math.min(maxTargets.getValue().intValue(), candidates.size());
        ArrayList<MultiNode> nodes = new ArrayList<>(targetLimit);
        for (LivingEntity candidate : candidates) {
            Vec3d position = findAttackPosition(candidate, origin);
            if (position == null) continue;

            nodes.add(new MultiNode(candidate, position));
            if (nodes.size() >= targetLimit) break;
        }
        if (nodes.isEmpty()) return false;

        ArrayList<RouteLeg> route = findShortestMultiRoute(origin, nodes);
        if (route.isEmpty()) return false;

        activeMultiLegs.clear();
        activeMultiLegs.addAll(route);
        multiLegIndex = 0;
        multiPathIndex = 0;

        renderedOutboundPath.add(origin);
        for (int index = 0; index < route.size() - 1; index++) {
            appendPath(renderedOutboundPath, route.get(index).path());
        }
        RouteLeg returnLeg = route.getLast();
        Vec3d returnStart = route.size() == 1 ? origin : route.get(route.size() - 2).destination();
        renderedReturnPath = withEndpoints(returnStart, returnLeg.path(), origin);

        activeOutboundPath.clear();
        activeReturnPath.clear();
        RouteLeg firstLeg = route.getFirst();
        activeTarget = firstLeg.target();
        target = firstLeg.target();
        attackOrigin = origin;
        attackPosition = firstLeg.destination();
        outboundPathIndex = 0;
        returnPathIndex = 0;
        attacked = false;
        blinkAttackActive = true;
        if (!instant.getValue()) {
            mc.player.setVelocity(Vec3d.ZERO);
        }

        releaseSentinelAFlyBlink();
        instance.getPacketManager().getBlink().start(this);
        updateBlinkAttack();
        return true;
    }

    private void updateBlinkAttack() {
        if (!blinkAttackActive) return;

        if (!activeMultiLegs.isEmpty()) {
            updateMultiBlinkAttack();
            return;
        }

        if (instant.getValue()) {
            outboundPathIndex = sendLocations(activeOutboundPath, outboundPathIndex, activeOutboundPath.size());
            performAttack();
            returnPathIndex = sendLocations(activeReturnPath, returnPathIndex, activeReturnPath.size());
            finishBlinkAttack();
            return;
        }

        if (outboundPathIndex < activeOutboundPath.size()) {
            outboundPathIndex = sendLocations(activeOutboundPath, outboundPathIndex, outboundPathIndex + 1);
            if (outboundPathIndex >= activeOutboundPath.size()) {
                performAttack();
            }
            updateProgressSuffix();
            return;
        }

        if (!attacked) {
            performAttack();
            updateProgressSuffix();
            return;
        }

        if (returnPathIndex < activeReturnPath.size()) {
            returnPathIndex = sendLocations(activeReturnPath, returnPathIndex, returnPathIndex + 1);
        }
        updateProgressSuffix();

        if (returnPathIndex >= activeReturnPath.size()) {
            finishBlinkAttack();
        }
    }

    private void updateMultiBlinkAttack() {
        if (instant.getValue()) {
            for (; multiLegIndex < activeMultiLegs.size(); multiLegIndex++) {
                RouteLeg leg = activeMultiLegs.get(multiLegIndex);
                multiPathIndex = sendLocations(leg.path(), multiPathIndex, leg.path().size());
                if (leg.target() != null) {
                    performMultiAttack(leg);
                }
                multiPathIndex = 0;
            }
            finishBlinkAttack();
            return;
        }

        if (multiLegIndex >= activeMultiLegs.size()) {
            finishBlinkAttack();
            return;
        }

        RouteLeg leg = activeMultiLegs.get(multiLegIndex);
        if (leg.target() != null) {
            activeTarget = leg.target();
            target = leg.target();
            attackPosition = leg.destination();
        }
        if (multiPathIndex < leg.path().size()) {
            multiPathIndex = sendLocations(leg.path(), multiPathIndex, multiPathIndex + 1);
        }

        if (multiPathIndex >= leg.path().size()) {
            if (leg.target() != null) {
                performMultiAttack(leg);
            }
            multiLegIndex++;
            multiPathIndex = 0;
        }

        updateMultiProgressSuffix();
        if (multiLegIndex >= activeMultiLegs.size()) {
            finishBlinkAttack();
        }
    }

    private void performAttack() {
        if (attacked) return;

        if (canAttackActiveTarget()) {
            if (rotation.getValue()) {
                sendAttackRotation(activeTarget);
            }
            attack(activeTarget);
        }
        advanceSwitchTarget(activeTarget);
        attacked = true;
        attackTimer.reset();
    }

    private void performMultiAttack(RouteLeg leg) {
        activeTarget = leg.target();
        target = leg.target();
        attackPosition = leg.destination();
        if (canAttackActiveTarget()) {
            if (rotation.getValue()) {
                sendAttackRotation(activeTarget);
            }
            attack(activeTarget);
        }
        attackTimer.reset();
    }

    private void updateProgressSuffix() {
        int totalLocations = activeOutboundPath.size() + activeReturnPath.size();
        int completedLocations = outboundPathIndex + returnPathIndex;
        int progress = totalLocations == 0
                ? 100
                : Math.min(100, Math.round((float) completedLocations / totalLocations * 100.0F));
        setSuffix("Blink " + progress + "%");
    }

    private void updateMultiProgressSuffix() {
        int totalLocations = 0;
        int completedLocations = 0;
        for (int index = 0; index < activeMultiLegs.size(); index++) {
            int pathSize = activeMultiLegs.get(index).path().size();
            totalLocations += pathSize;
            if (index < multiLegIndex) {
                completedLocations += pathSize;
            } else if (index == multiLegIndex) {
                completedLocations += multiPathIndex;
            }
        }
        int progress = totalLocations == 0
                ? 100
                : Math.min(100, Math.round((float) completedLocations / totalLocations * 100.0F));
        setSuffix("Multi Blink " + progress + "%");
    }

    private int sendLocations(ArrayList<Vec3d> path, int fromIndex, int toIndex) {
        int endIndex = Math.min(path.size(), Math.max(fromIndex, toIndex));
        for (int index = fromIndex; index < endIndex; index++) {
            sendPosition(path.get(index));
        }
        return endIndex;
    }

    private boolean canAttackActiveTarget() {
        if (!isValidTarget(activeTarget) || attackPosition == null) return false;

        Vec3d attackEye = attackPosition.add(0.0, mc.player.getEyeHeight(mc.player.getPose()), 0.0);
        return activeTarget.getBoundingBox().squaredMagnitude(attackEye)
                <= MAX_ATTACK_DISTANCE * MAX_ATTACK_DISTANCE;
    }

    private void advanceSwitchTarget(LivingEntity currentTarget) {
        if (!targetMode.is("Switch")) return;

        List<LivingEntity> candidates = getCandidates();
        int currentIndex = candidates.indexOf(currentTarget);
        if (currentIndex >= 0 && !candidates.isEmpty()) {
            switchIndex = (currentIndex + 1) % candidates.size();
        } else if (!candidates.isEmpty()) {
            switchIndex = Math.floorMod(switchIndex, candidates.size());
        } else {
            switchIndex = 0;
        }
        switchTarget = null;
    }

    private void releaseSentinelAFlyBlink() {
        Fly fly = getModule(Fly.class);
        if (fly != null && fly.isEnabled() && fly.mode.is("SentinelA")) {
            instance.getPacketManager().getBlink().dispatch(fly);
        }
    }

    public boolean isBlinkAttackActive() {
        return isEnabled() && blinkAttackActive;
    }

    private void lockClientPosition() {
        if (mc.player == null || attackOrigin == null) return;

        mc.player.setVelocity(Vec3d.ZERO);
        mc.player.setPosition(attackOrigin);
    }

    private void finishBlinkAttack() {
        if (!blinkAttackActive) return;

        instance.getPacketManager().getBlink().dispatch(this);
        clearBlinkAttackState();
    }

    private void stopBlinkAttack(boolean completeReturn) {
        if (!blinkAttackActive) return;

        if (completeReturn && mc.player != null && mc.getNetworkHandler() != null) {
            if (!activeMultiLegs.isEmpty()) {
                while (multiLegIndex < activeMultiLegs.size()) {
                    ArrayList<Vec3d> path = activeMultiLegs.get(multiLegIndex).path();
                    multiPathIndex = sendLocations(path, multiPathIndex, path.size());
                    multiLegIndex++;
                    multiPathIndex = 0;
                }
            } else {
                outboundPathIndex = sendLocations(activeOutboundPath, outboundPathIndex, activeOutboundPath.size());
                returnPathIndex = sendLocations(activeReturnPath, returnPathIndex, activeReturnPath.size());
            }
        }

        if (instance.getPacketManager() != null) {
            instance.getPacketManager().getBlink().dispatch(this);
        }
        clearBlinkAttackState();
    }

    private void clearBlinkAttackState() {
        activeOutboundPath.clear();
        activeReturnPath.clear();
        activeTarget = null;
        attackOrigin = null;
        attackPosition = null;
        outboundPathIndex = 0;
        returnPathIndex = 0;
        activeMultiLegs.clear();
        multiLegIndex = 0;
        multiPathIndex = 0;
        blinkAttackActive = false;
        attacked = false;
    }

    private Vec3d findAttackPosition(LivingEntity entity, Vec3d origin) {
        BlockPos targetBlock = BlockPos.ofFloored(entity.getEntityPos());
        Box targetBox = entity.getBoundingBox();
        Vec3d bestPosition = null;
        double bestDistanceSquared = Double.MAX_VALUE;

        for (int yOffset = -1; yOffset <= 1; yOffset++) {
            int y = targetBlock.getY() + yOffset;
            for (int xOffset = -ATTACK_POSITION_RADIUS; xOffset <= ATTACK_POSITION_RADIUS; xOffset++) {
                for (int zOffset = -ATTACK_POSITION_RADIUS; zOffset <= ATTACK_POSITION_RADIUS; zOffset++) {
                    int x = targetBlock.getX() + xOffset;
                    int z = targetBlock.getZ() + zOffset;
                    if (!PathFinder.isValid(x, y, z, true)) continue;

                    Vec3d candidate = new Vec3d(x + 0.5, y, z + 0.5);
                    Vec3d candidateEye = candidate.add(0.0, mc.player.getEyeHeight(mc.player.getPose()), 0.0);
                    if (targetBox.squaredMagnitude(candidateEye) > MAX_ATTACK_DISTANCE * MAX_ATTACK_DISTANCE) {
                        continue;
                    }

                    double distanceSquared = origin.squaredDistanceTo(candidate);
                    if (distanceSquared < bestDistanceSquared) {
                        bestDistanceSquared = distanceSquared;
                        bestPosition = candidate;
                    }
                }
            }
        }

        return bestPosition;
    }

    private ArrayList<RouteLeg> findShortestMultiRoute(Vec3d origin, ArrayList<MultiNode> nodes) {
        int nodeCount = nodes.size();
        PathEdge[] fromOrigin = new PathEdge[nodeCount];
        PathEdge[] toOrigin = new PathEdge[nodeCount];
        PathEdge[][] between = new PathEdge[nodeCount][nodeCount];

        for (int index = 0; index < nodeCount; index++) {
            Vec3d position = nodes.get(index).position();
            fromOrigin[index] = computePathEdge(origin, position);
            toOrigin[index] = computePathEdge(position, origin);
            for (int other = 0; other < nodeCount; other++) {
                if (index == other) continue;
                between[index][other] = computePathEdge(position, nodes.get(other).position());
            }
        }

        int stateCount = 1 << nodeCount;
        double[][] costs = new double[stateCount][nodeCount];
        int[][] previous = new int[stateCount][nodeCount];
        for (int state = 0; state < stateCount; state++) {
            Arrays.fill(costs[state], Double.POSITIVE_INFINITY);
            Arrays.fill(previous[state], -1);
        }
        for (int index = 0; index < nodeCount; index++) {
            if (fromOrigin[index] != null) {
                costs[1 << index][index] = fromOrigin[index].cost();
            }
        }

        for (int state = 1; state < stateCount; state++) {
            for (int last = 0; last < nodeCount; last++) {
                if ((state & 1 << last) == 0 || !Double.isFinite(costs[state][last])) continue;

                for (int next = 0; next < nodeCount; next++) {
                    if ((state & 1 << next) != 0 || between[last][next] == null) continue;

                    int nextState = state | 1 << next;
                    double nextCost = costs[state][last] + between[last][next].cost();
                    if (nextCost < costs[nextState][next]) {
                        costs[nextState][next] = nextCost;
                        previous[nextState][next] = last;
                    }
                }
            }
        }

        int fullState = stateCount - 1;
        int bestLast = -1;
        double bestCost = Double.POSITIVE_INFINITY;
        for (int last = 0; last < nodeCount; last++) {
            if (toOrigin[last] == null || !Double.isFinite(costs[fullState][last])) continue;

            double routeCost = costs[fullState][last] + toOrigin[last].cost();
            if (routeCost < bestCost) {
                bestCost = routeCost;
                bestLast = last;
            }
        }
        if (bestLast < 0) return new ArrayList<>();

        ArrayList<Integer> order = new ArrayList<>(nodeCount);
        int state = fullState;
        int current = bestLast;
        while (current >= 0) {
            order.add(current);
            int prior = previous[state][current];
            state ^= 1 << current;
            current = prior;
        }
        Collections.reverse(order);

        ArrayList<RouteLeg> route = new ArrayList<>(nodeCount + 1);
        int first = order.getFirst();
        MultiNode firstNode = nodes.get(first);
        route.add(new RouteLeg(
                firstNode.target(),
                firstNode.position(),
                withEndpoint(fromOrigin[first].path(), firstNode.position())
        ));
        for (int index = 1; index < order.size(); index++) {
            int previousNodeIndex = order.get(index - 1);
            int nodeIndex = order.get(index);
            MultiNode node = nodes.get(nodeIndex);
            route.add(new RouteLeg(
                    node.target(),
                    node.position(),
                    withEndpoint(between[previousNodeIndex][nodeIndex].path(), node.position())
            ));
        }
        route.add(new RouteLeg(
                null,
                origin,
                withEndpoint(toOrigin[bestLast].path(), origin)
        ));
        return route;
    }

    private PathEdge computePathEdge(Vec3d start, Vec3d destination) {
        ArrayList<Vec3d> path = MainPathFinder.computePath(start, destination);
        if (!pathReaches(path, start, destination)) return null;

        double cost = 0.0;
        Vec3d previous = start;
        for (Vec3d location : path) {
            cost += previous.distanceTo(location);
            previous = location;
        }
        cost += previous.distanceTo(destination);
        return new PathEdge(path, cost);
    }

    private boolean pathReaches(ArrayList<Vec3d> path, Vec3d start, Vec3d destination) {
        Vec3d endpoint = path.isEmpty() ? start : path.getLast();
        return endpoint.squaredDistanceTo(destination) <= PATH_END_TOLERANCE * PATH_END_TOLERANCE;
    }

    private ArrayList<Vec3d> withEndpoints(Vec3d start, ArrayList<Vec3d> path, Vec3d end) {
        ArrayList<Vec3d> result = new ArrayList<>(path.size() + 2);
        addIfDifferent(result, start);
        for (Vec3d location : path) {
            addIfDifferent(result, location);
        }
        addIfDifferent(result, end);
        return result;
    }

    private ArrayList<Vec3d> withEndpoint(ArrayList<Vec3d> path, Vec3d end) {
        ArrayList<Vec3d> result = new ArrayList<>(path.size() + 1);
        result.addAll(path);
        addIfDifferent(result, end);
        return result;
    }

    private void appendPath(ArrayList<Vec3d> destination, ArrayList<Vec3d> path) {
        for (Vec3d location : path) {
            addIfDifferent(destination, location);
        }
    }

    private void addIfDifferent(ArrayList<Vec3d> path, Vec3d location) {
        if (path.isEmpty() || !path.getLast().equals(location)) {
            path.add(location);
        }
    }

    private void sendPosition(Vec3d position) {
        PacketUtil.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                position,
                onGroundPacket.getValue(),
                mc.player.horizontalCollision
        ));
    }

    private void sendAttackRotation(LivingEntity entity) {
        Box targetBox = entity.getBoundingBox();
        Vec3d targetCenter = new Vec3d(
                (targetBox.minX + targetBox.maxX) * 0.5,
                (targetBox.minY + targetBox.maxY) * 0.5,
                (targetBox.minZ + targetBox.maxZ) * 0.5
        );
        Vec3d attackEye = attackPosition.add(
                0.0,
                mc.player.getEyeHeight(mc.player.getPose()),
                0.0
        );
        float[] rotations = RotationUtil.getRotations(attackEye, targetCenter);

        PacketUtil.sendPacket(new PlayerMoveC2SPacket.Full(
                attackPosition,
                rotations[0],
                rotations[1],
                onGroundPacket.getValue(),
                mc.player.horizontalCollision
        ));
    }

    private void attack(LivingEntity entity) {
        PacketUtil.sendPacket(PlayerInteractEntityC2SPacket.attack(entity, mc.player.isSneaking()));
        mc.player.swingHand(Hand.MAIN_HAND);
        mc.player.resetTicksSinceLastAttack();
    }

    private boolean canRun() {
        return mc.player != null
                && mc.world != null
                && mc.getNetworkHandler() != null
                && mc.player.isAlive()
                && !mc.player.isSpectator();
    }

    private void reset() {
        target = null;
        switchTarget = null;
        switchIndex = 0;
        clearBlinkAttackState();
        renderedOutboundPath.clear();
        renderedReturnPath.clear();
        attackTimer.reset();
        setSuffix("");
    }

    private record MultiNode(LivingEntity target, Vec3d position) {}

    private record PathEdge(ArrayList<Vec3d> path, double cost) {}

    private record RouteLeg(LivingEntity target, Vec3d destination, ArrayList<Vec3d> path) {}
}
