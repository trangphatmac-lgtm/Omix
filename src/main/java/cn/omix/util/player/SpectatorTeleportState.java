package cn.omix.util.player;

import net.minecraft.util.math.Vec3d;

/** Client-thread state for one spectator teleport and its five-tick grace period. */
public final class SpectatorTeleportState {
    private static final int GRACE_TICKS = 5;

    private Vec3d target;
    private Vec3d teleportTarget;
    private boolean spectating;
    private boolean flying;
    private int graceTicks;

    public boolean selectTarget(Vec3d position) {
        if (position == null || isInGrace()) return false;
        target = position;
        return true;
    }

    public Vec3d getTarget() {
        return target;
    }

    public boolean enterSpectator() {
        if (spectating) return false;
        spectating = true;
        return true;
    }

    public boolean observeFlying(boolean nextFlying) {
        boolean entered = nextFlying && !flying;
        flying = nextFlying;
        return entered && enterSpectator();
    }

    public Vec3d leaveSpectator() {
        Vec3d position = beginTeleport();
        if (position == null && !isInGrace()) spectating = false;
        return position;
    }

    public Vec3d correctionTarget() {
        return isInGrace() ? teleportTarget : beginTeleport();
    }

    private Vec3d beginTeleport() {
        if (!spectating || isInGrace() || target == null) return null;
        teleportTarget = target;
        graceTicks = GRACE_TICKS;
        return teleportTarget;
    }

    public boolean isInGrace() {
        return graceTicks > 0;
    }

    public Vec3d getTeleportTarget() {
        return teleportTarget;
    }

    public boolean tickGrace() {
        if (!isInGrace()) return false;
        graceTicks--;
        if (graceTicks > 0) return false;
        spectating = false;
        teleportTarget = null;
        return true;
    }

    /** A replacement player cannot inherit an old player's pending correction. */
    public void resetCycle(boolean spectator, boolean flying) {
        spectating = spectator || flying;
        this.flying = flying;
        teleportTarget = null;
        graceTicks = 0;
    }

    public void clear() {
        target = null;
        resetCycle(false, false);
    }
}
