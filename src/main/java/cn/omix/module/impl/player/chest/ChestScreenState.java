package cn.omix.module.impl.player.chest;

/** A close request must stay attached to the container that received it. */
public final class ChestScreenState {
    private int syncId = -1;
    private boolean neutralTickCompleted;
    private boolean closeRequested;

    public void open(int syncId) {
        this.syncId = syncId;
        neutralTickCompleted = closeRequested = false;
    }

    public boolean active() {
        return syncId >= 0;
    }

    public boolean ready(int syncId) {
        return active() && this.syncId == syncId && neutralTickCompleted;
    }

    public void completePlayerTick(int syncId, boolean neutralInput, boolean stoppedSprinting) {
        if (active() && this.syncId == syncId) {
            neutralTickCompleted = neutralInput && stoppedSprinting;
        }
    }

    public boolean deferClose(int syncId) {
        if (!active() || this.syncId != syncId || ready(syncId)) return false;
        closeRequested = true;
        return true;
    }

    public boolean shouldClose(int syncId) {
        return closeRequested && ready(syncId);
    }

    public void reset() {
        syncId = -1;
        neutralTickCompleted = closeRequested = false;
    }
}
