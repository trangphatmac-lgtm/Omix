package cn.omix.util.world;

import cn.omix.management.rotation.RotationRequest.YawDirection;

/** One direction for both legs of a Telly round, with natural tracking after each arrival. */
public final class TellyRotationState {
    private enum Leg { NONE, PLACEMENT, RETURN }

    private String mode = "Default";
    private boolean active;
    private boolean canRotate;
    private boolean newRound = true;
    private int previousOffGroundTicks = -1;
    private YawDirection lastDirection = YawDirection.DEFAULT;
    private Leg leg = Leg.NONE;
    private boolean turnFinished;
    private float targetYaw;

    public void update(String selectedMode, boolean tellyBridge, boolean clutchActive,
                       boolean canRotate, int offGroundTicks) {
        if (!tellyBridge || !selectedMode.equals(mode)) reset();
        mode = selectedMode;
        active = tellyBridge && !clutchActive && !mode.equals("Default");
        this.canRotate = canRotate;
        // A landing also starts a new round when Telly Tick = 0 never releases rotation.
        // Its return leg still belongs to the previous round; choose again only on placement.
        if (!active || offGroundTicks < previousOffGroundTicks) newRound = true;
        if (!active) resetTurn();
        previousOffGroundTicks = offGroundTicks;
    }

    public boolean isActive() {
        return active;
    }

    public boolean shouldReturn() {
        return active && lastDirection != YawDirection.DEFAULT;
    }

    public YawDirection direction(float currentYaw, float requestedYaw) {
        if (!active || !canRotate) return YawDirection.DEFAULT;

        if (newRound) {
            float delta = YawDirection.DEFAULT.delta(currentYaw, requestedYaw);
            if (delta == 0.0F) return YawDirection.DEFAULT;
            if (lastDirection == YawDirection.DEFAULT) {
                lastDirection = delta < 0.0F ? YawDirection.LEFT : YawDirection.RIGHT;
            } else if (mode.equals("Always Change")) {
                lastDirection = lastDirection == YawDirection.LEFT ? YawDirection.RIGHT : YawDirection.LEFT;
            }
            newRound = false;
            resetTurn();
        }
        return track(Leg.PLACEMENT, currentYaw, requestedYaw);
    }

    public YawDirection returnDirection(float currentYaw, float cameraYaw) {
        if (!shouldReturn()) return YawDirection.DEFAULT;
        return track(Leg.RETURN, currentYaw, cameraYaw);
    }

    private YawDirection track(Leg nextLeg, float currentYaw, float requestedYaw) {
        if (leg != nextLeg) {
            leg = nextLeg;
            turnFinished = false;
            targetYaw = currentYaw + lastDirection.delta(currentYaw, requestedYaw);
        } else {
            // Follow the same equivalent target across +/-180 and moving placement faces.
            targetYaw += YawDirection.DEFAULT.delta(targetYaw, requestedYaw);
        }

        if (turnFinished) return YawDirection.DEFAULT;
        float remaining = targetYaw - currentYaw;
        if (lastDirection == YawDirection.LEFT ? remaining >= 0.0F : remaining <= 0.0F) {
            // Sensitivity rounding can pass the target slightly. Do not start another full turn.
            turnFinished = true;
            return YawDirection.DEFAULT;
        }
        return lastDirection;
    }

    public void reset() {
        mode = "Default";
        active = false;
        canRotate = false;
        newRound = true;
        previousOffGroundTicks = -1;
        lastDirection = YawDirection.DEFAULT;
        resetTurn();
    }

    private void resetTurn() {
        leg = Leg.NONE;
        turnFinished = false;
        targetYaw = 0.0F;
    }
}
