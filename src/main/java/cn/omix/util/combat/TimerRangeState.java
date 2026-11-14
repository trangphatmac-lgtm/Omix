package cn.omix.util.combat;

/** Southside TimerRange's range transitions and TimerRangeB's packet-driven cycle. */
public final class TimerRangeState {
    private enum RangeState { OUT, TELEPORT, IN, SLOW }

    private RangeState rangeState = RangeState.OUT;
    private int playerTicks;
    private int cooldownTick;

    public float tickA(boolean hasTarget, float boost, float slowSpeed) {
        if (!hasTarget) {
            rangeState = rangeState == RangeState.IN ? RangeState.SLOW : RangeState.OUT;
        } else {
            rangeState = rangeState == RangeState.OUT ? RangeState.TELEPORT : RangeState.IN;
        }

        return switch (rangeState) {
            case TELEPORT -> boost;
            case SLOW -> slowSpeed;
            default -> 1.0F;
        };
    }

    public float tickB(float positiveTicks, float positiveTimer, float negativeTimer) {
        if (playerTicks <= 0) return 1.0F;

        // The source divides remaining ticks, so the sequence starts at normal speed.
        float progress = playerTicks / positiveTicks;
        float speed = 1.0F;
        if (progress < 0.5D) {
            speed = positiveTimer;
        } else if (progress < 0.9D) {
            speed = negativeTimer;
        }
        playerTicks--;
        return speed;
    }

    /** Returns true when the packet starts a cycle and leaves the current speed intact. */
    public boolean onInteraction(boolean attack, double distance, float range, int positiveTicks, int negativeTicks) {
        if (!attack && playerTicks >= 1) return false;

        // NegativeTicks counts interaction packets in the source, including idle interactions.
        cooldownTick++;
        if (cooldownTick >= negativeTicks && distance > range) {
            playerTicks = positiveTicks;
            cooldownTick = 0;
            return true;
        }
        return false;
    }

    public boolean hasActiveCycle() {
        return playerTicks >= 1;
    }

    public void reset() {
        rangeState = RangeState.OUT;
        playerTicks = 0;
        cooldownTick = 0;
    }
}
