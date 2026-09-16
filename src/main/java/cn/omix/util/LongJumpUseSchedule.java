package cn.omix.util;

/** Client-thread scheduling: acknowledged follow-ups do not advance the simulation clock. */
public final class LongJumpUseSchedule {
    private boolean pending = true;
    private boolean usedThisTick;
    private boolean closed;
    private LongJumpAim aim;
    private LongJumpAim lastAim;
    private boolean tickOpen = true;
    private boolean continuation;
    private long cooldownUntil;

    public void beginTick() {
        usedThisTick = false;
        aim = null;
        tickOpen = true;
    }

    public void endTick() {
        tickOpen = false;
    }

    public void requestNextUse() {
        if (!closed) {
            pending = true;
            continuation = true;
        }
    }

    public boolean beginUse(boolean coolingDown, LongJumpAim aim) {
        if (closed || !pending || usedThisTick || coolingDown) return false;
        java.util.Objects.requireNonNull(aim);
        pending = false;
        usedThisTick = true;
        this.aim = aim;
        lastAim = aim;
        return true;
    }

    public boolean canContinue(long now) {
        return !closed && pending && continuation && !tickOpen && lastAim != null && now >= cooldownUntil;
    }

    public boolean beginContinuation(long now) {
        if (!canContinue(now)) return false;
        pending = false;
        continuation = false;
        usedThisTick = true;
        aim = lastAim;
        return true;
    }

    /** Tick-based item/server cooldown durations run at real 20 TPS while physics stays slowed. */
    public void setCooldown(long now, int ticks) {
        cooldownUntil = now + Math.max(0L, ticks) * 50_000_000L;
    }

    public LongJumpAim getLastAim() {
        return lastAim;
    }

    public LongJumpAim getAim() {
        return aim;
    }

    public boolean isPending() {
        return pending;
    }

    public boolean isUsedThisTick() {
        return usedThisTick;
    }

    public void close() {
        closed = true;
        pending = false;
    }
}
