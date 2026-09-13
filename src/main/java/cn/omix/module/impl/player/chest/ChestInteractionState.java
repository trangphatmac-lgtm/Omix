package cn.omix.module.impl.player.chest;

/** Tracks the actual outgoing tick boundary, independently of local sprint state. */
public final class ChestInteractionState {
    private boolean sprintChanged;
    private boolean blockUsed;
    private boolean chestUsed;
    private boolean awaitingScreen;

    public void sprintChanged() {
        sprintChanged = true;
    }

    public void blockUsed() {
        blockUsed = true;
    }

    public boolean canUse() {
        return !sprintChanged && !blockUsed && !awaitingScreen;
    }

    public boolean beginUse() {
        if (!canUse()) return false;
        blockUsed = chestUsed = awaitingScreen = true;
        return true;
    }

    public boolean suppressOtherUse() {
        return chestUsed || awaitingScreen;
    }

    public boolean awaitingScreen() {
        return awaitingScreen;
    }

    public void finishUse() {
        awaitingScreen = false;
    }

    public void tickEnded() {
        sprintChanged = blockUsed = chestUsed = false;
    }

    public void reset() {
        tickEnded();
        finishUse();
    }

    /** Preserve full turns when entering or leaving a silent rotation. */
    public static float nearestYaw(float reference, float yaw) {
        float delta = (yaw - reference) % 360.0F;
        if (delta >= 180.0F) delta -= 360.0F;
        if (delta < -180.0F) delta += 360.0F;
        return reference + delta;
    }
}
