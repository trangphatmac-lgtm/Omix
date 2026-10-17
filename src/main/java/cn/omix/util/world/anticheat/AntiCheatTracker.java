package cn.omix.util.world.anticheat;

import java.util.Set;

import static cn.omix.util.world.anticheat.AntiCheatCheck.*;

/** Per-entity, client-tick observations. No Minecraft state is accessed off-thread. */
public final class AntiCheatTracker {
    public record Sample(double x, double y, double z, float yaw, float pitch,
                         boolean sneaking, boolean sprinting, boolean usingItem, boolean blocking,
                         boolean swinging, int swingTicks, boolean holdingBlock, boolean fishingRod,
                         boolean airBelow, boolean voidBelow, boolean hurt, boolean ladder,
                         boolean adventure, boolean recentPacket, boolean noFallAllowed,
                         boolean scaffoldAllowed, double distanceSquared) {}

    private Sample previous;
    private int tick, quickSwings, lastPitchTick, yawTicks, restTicks, sprintTicks,
            lastStopUsingTick, lastDeltaYTick, stillTicks, lastSneakTick, swingBlockTicks,
            useSprintTicks, perfectSneaks, fallingTicks, scaffoldTicks, horizontalTicks;
    private int breakProgress = -1;
    private int breakTick;
    private Sample fallStart;
    private int fallStartTick;

    public void breakAnimation(int progress) {
        breakProgress = progress;
        breakTick = tick;
    }

    public AntiCheatCheck update(Sample p, Set<AntiCheatCheck> enabled) {
        tick++;
        if (previous == null) {
            previous = p;
            return null;
        }
        double dx = p.x - previous.x, dy = p.y - previous.y, dz = p.z - previous.z;
        double horizontal = Math.max(Math.abs(dx), Math.abs(dz));
        // Teleports and long unobserved gaps must not seed movement heuristics.
        if (horizontal >= 3 || Math.abs(dy) >= 8) {
            resetMotion();
            previous = p;
            return null;
        }
        int lastRest = restTicks, lastSprint = sprintTicks, lastFalling = fallingTicks;
        restTicks = horizontal == 0 && dy == 0 ? restTicks + 1 : 0;
        horizontalTicks = horizontal >= 0.07 ? horizontalTicks + 1 : 0;
        if (Math.abs(dy) >= 0.1) lastDeltaYTick = tick;
        fallingTicks = dy < 0 && p.airBelow ? fallingTicks + 1 : 0;
        if (p.sneaking) lastSneakTick = tick;
        if (p.pitch != previous.pitch) lastPitchTick = tick;
        if (!p.usingItem && previous.usingItem) lastStopUsingTick = tick;
        // Wrap at +/-180 so a normal wrap does not count as a large turn.
        double yawDelta = Math.abs(Math.IEEEremainder(p.yaw - previous.yaw, 360));
        yawTicks = yawDelta >= 1 ? yawTicks + 1 : 0;
        swingBlockTicks = p.swinging && p.blocking ? swingBlockTicks + 1 : 0;
        sprintTicks = p.sprinting ? sprintTicks + 1 : 0;
        useSprintTicks = p.sprinting && p.usingItem ? useSprintTicks + 1 : 0;
        if (p.swinging && p.pitch >= 65 && p.holdingBlock) {
            scaffoldTicks = horizontalTicks >= 15 ? scaffoldTicks + 1 : 0;
            if (p.swingTicks == 1) perfectSneaks = !previous.sneaking && p.sneaking ? perfectSneaks + 1 : 0;
        } else {
            perfectSneaks = scaffoldTicks = 0;
        }
        if (tick - breakTick > 40) breakProgress = -1;
        if (p.swinging && (breakProgress < 0 || breakProgress > 9)) {
            if (p.swingTicks == 0) quickSwings = previous.swingTicks == 3 ? quickSwings + 1 : 0;
        } else {
            quickSwings = 0;
        }
        stillTicks = !p.sneaking && !p.swinging && horizontal == 0 && dy == 0
                && p.yaw == previous.yaw && p.pitch == previous.pitch ? stillTicks + 1 : 0;

        // Maintain this state even when another check wins this tick.
        boolean noFall = false;
        if (!enabled.contains(NO_FALL) || !p.noFallAllowed || p.hurt || p.ladder) {
            fallStart = null;
        } else {
            if (stillTicks == 10 && p.airBelow) { fallStart = p; fallStartTick = tick; }
            if (fallStart != null && tick - fallStartTick > 200) fallStart = null;
            if (fallStart != null && lastFalling > 0 && lastFalling < 6 && !p.airBelow) {
                double fx = p.x - fallStart.x, fz = p.z - fallStart.z;
                noFall = fx * fx + fz * fz < 144 && p.y - fallStart.y <= -5;
                fallStart = null;
            }
        }
        previous = p;
        if (enabled.contains(AIM_ASSIST) && p.pitch > -80 && p.pitch < 80 && yawTicks >= 25 && tick - lastPitchTick >= 25) return AIM_ASSIST;
        if (enabled.contains(AUTO_CLICKER) && !p.adventure && quickSwings >= 10 && p.distanceSquared < 700 && !p.fishingRod) return AUTO_CLICKER;
        if (enabled.contains(AUTO_BLOCK) && swingBlockTicks >= 15) return AUTO_BLOCK;
        if (enabled.contains(BLINK) && !p.adventure && lastSprint >= 2 && lastRest >= 6 && horizontal > 0.5 && horizontal < 3 && p.recentPacket) return BLINK;
        if (enabled.contains(LEGIT_SCAFFOLD) && perfectSneaks >= 3) return LEGIT_SCAFFOLD;
        if (noFall) return NO_FALL;
        if (enabled.contains(NO_SLOW) && useSprintTicks >= 11 && horizontal >= 0.08 && tick - lastStopUsingTick >= 35) return NO_SLOW;
        if (enabled.contains(SCAFFOLD) && scaffoldTicks > 0 && tick - lastSneakTick >= 25
                && (tick - lastDeltaYTick >= 20 || scaffoldTicks > 25) && p.voidBelow && p.scaffoldAllowed) return SCAFFOLD;
        if (enabled.contains(PROTOCOL) && Math.abs(p.pitch) >= 92) return PROTOCOL;
        return null;
    }

    private void resetMotion() {
        quickSwings = yawTicks = restTicks = sprintTicks = stillTicks = swingBlockTicks = 0;
        useSprintTicks = perfectSneaks = fallingTicks = scaffoldTicks = horizontalTicks = 0;
        lastPitchTick = lastStopUsingTick = lastDeltaYTick = lastSneakTick = tick;
        fallStart = null;
    }
}
