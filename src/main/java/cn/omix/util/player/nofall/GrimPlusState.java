package cn.omix.util.player.nofall;

import cn.omix.event.impl.MotionEvent;
import cn.omix.event.impl.MoveInputEvent;
import cn.omix.event.impl.PlayerUpdateEvent;

public final class GrimPlusState {
    private static final double FALL_THRESHOLD = 3.0;
    private static final long RECENT_TRIGGER_WINDOW_MS = 400L;
    private static volatile long lastTriggerMillis;
    private double previousFallDistance;
    private boolean jumpPending;
    private int updatesToCancel;
    private int inputsToFreeze;
    private final Host host;

    public GrimPlusState(Host host) {
        this.host = host;
    }

    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (updatesToCancel > 0) {
            event.setCancelled(true);
            updatesToCancel--;
        }
    }

    public void onMotion(MotionEvent event) {
        if (!event.isPre()) return;
        double sampledFallDistance = host.fallDistance();
        if (previousFallDistance >= FALL_THRESHOLD && event.isOnGround() && !host.noSlowActivePhase()) {
            event.setOnGround(false);
            lastTriggerMillis = host.currentTimeMillis();
            host.sendGroundPacket(true, host.horizontalCollision());
            updatesToCancel++;
            jumpPending = true;
            inputsToFreeze = 1;
        }
        previousFallDistance = sampledFallDistance;
    }

    public void onInput(MoveInputEvent event) {
        if (inputsToFreeze > 0) {
            event.setForward(0.0F);
            event.setStrafe(0.0F);
            inputsToFreeze--;
        }
        if (previousFallDistance >= FALL_THRESHOLD && host.playerOnGround()) {
            event.setSneaking(false);
        }
        if (jumpPending) {
            event.setJumping(true);
            jumpPending = false;
        }
    }

    public void onDisable() {
        previousFallDistance = 0.0;
        jumpPending = false;
        updatesToCancel = 0;
        inputsToFreeze = 0;
        lastTriggerMillis = 0L;
    }

    /** A shared wall-clock query, not a trigger cooldown or an enabled-state check. */
    public static boolean triggeredRecently() {
        return System.currentTimeMillis() - lastTriggerMillis < RECENT_TRIGGER_WINDOW_MS;
    }

    // Preserve the reconstruction's clock/obfuscation boundary for differential verification.
    static boolean triggeredRecently(Host host) {
        boolean guardPresent = host.obfuscationGuardPresent();
        long elapsed = host.currentTimeMillis() - lastTriggerMillis;
        int comparison = Long.compare(elapsed, RECENT_TRIGGER_WINDOW_MS);
        return guardPresent ? comparison != 0 : comparison < 0;
    }

    public interface Host {
        double fallDistance();
        boolean playerOnGround();
        boolean horizontalCollision();
        boolean noSlowActivePhase();
        long currentTimeMillis();
        void sendGroundPacket(boolean onGround, boolean horizontalCollision);
        default boolean obfuscationGuardPresent() { return false; }
    }
}
