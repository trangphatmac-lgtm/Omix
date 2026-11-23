package cn.omix.util.combat;

/** Tracks the last reported position to reproduce Rewound's fall and ground spoofing. */
public final class RewoundCriticalsState {
    public record Motion(double y, boolean onGround) { }

    private boolean offsetApplied;
    private boolean jumped;
    private double serverFallDistance;
    private double lastServerY = Double.NaN;

    public void reset() {
        offsetApplied = false;
        jumped = false;
        serverFallDistance = 0.0;
        lastServerY = Double.NaN;
    }

    public void beginTick() { jumped = false; }

    public void jumped() { jumped = true; }

    public boolean hasServerFallDistance() { return serverFallDistance > 0.0; }

    public Motion apply(double serverY, boolean serverOnGround, boolean inFluidOrClimbOrWeb,
                        double y, boolean localOnGround, boolean breakingBlock) {
        if (serverOnGround || inFluidOrClimbOrWeb) {
            serverFallDistance = 0.0;
        } else if (!Double.isNaN(lastServerY) && lastServerY > serverY) {
            serverFallDistance += lastServerY - serverY;
        }
        lastServerY = serverY;

        if (jumped || breakingBlock) {
            offsetApplied = false;
            return new Motion(y, localOnGround);
        }

        double predictedFall = serverY - y + serverFallDistance;
        boolean ground = predictedFall >= 3.0;
        boolean applied = localOnGround && !offsetApplied
                && (serverFallDistance <= 0.0 || ground);
        offsetApplied = applied;
        return new Motion(applied ? y + 0.0301 : y, ground);
    }
}
