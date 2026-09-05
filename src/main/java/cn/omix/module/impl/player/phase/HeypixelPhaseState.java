package cn.omix.module.impl.player.phase;

/** The title-driven phase lifecycle, accessed only on the client thread. */
public final class HeypixelPhaseState {
    private boolean pendingPhase;
    private boolean freezeUpdates;

    public void onSubtitle(String text) {
        if (text.contains("稍等片刻")) pendingPhase = true;
    }

    public void onTitle(String text) {
        if (text.contains("开始")) {
            freezeUpdates = false;
            pendingPhase = false;
        }
    }

    public Update onUpdate(int playerAge) {
        if (freezeUpdates) return Update.FREEZE;
        if (pendingPhase && playerAge > 10) {
            pendingPhase = false;
            freezeUpdates = true;
            return Update.PHASE;
        }
        return Update.WAIT;
    }

    public enum Update {
        WAIT, PHASE, FREEZE
    }
}
