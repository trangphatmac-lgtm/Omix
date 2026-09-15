package cn.omix.util;

/** Client-thread item-use scheduling; a velocity callback only requests the next interaction. */
public final class LongJumpUseSchedule {
    private boolean pending = true;
    private boolean usedThisTick;
    private boolean closed;
    private LongJumpAim aim;

    public void beginTick() {
        usedThisTick = false;
        aim = null;
    }

    public void requestNextUse() {
        if (!closed) pending = true;
    }

    public boolean beginUse(boolean coolingDown, LongJumpAim aim) {
        if (closed || !pending || usedThisTick || coolingDown) return false;
        java.util.Objects.requireNonNull(aim);
        pending = false;
        usedThisTick = true;
        this.aim = aim;
        return true;
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
