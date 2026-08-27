package org.fisproxy;

/** Optional fields for {@link Client#changeIp(ChangeIpOptions)}. */
public final class ChangeIpOptions {
    private final String idempotencyKey;
    private final boolean wait;
    private final boolean waitRouteAck;
    private final double timeoutSeconds;
    private final double intervalSeconds;

    private ChangeIpOptions(Builder builder) {
        this.idempotencyKey = builder.idempotencyKey;
        this.wait = builder.wait;
        this.waitRouteAck = builder.waitRouteAck;
        this.timeoutSeconds = builder.timeoutSeconds;
        this.intervalSeconds = builder.intervalSeconds;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ChangeIpOptions defaults() {
        return builder().build();
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public boolean shouldWait() {
        return wait;
    }

    public boolean waitRouteAck() {
        return waitRouteAck;
    }

    public double timeoutSeconds() {
        return timeoutSeconds;
    }

    public double intervalSeconds() {
        return intervalSeconds;
    }

    public static final class Builder {
        private String idempotencyKey;
        private boolean wait = true;
        private boolean waitRouteAck = true;
        private double timeoutSeconds = 180.0;
        private double intervalSeconds = 1.0;

        private Builder() {
        }

        public Builder idempotencyKey(String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        public Builder wait(boolean wait) {
            this.wait = wait;
            return this;
        }

        public Builder waitRouteAck(boolean waitRouteAck) {
            this.waitRouteAck = waitRouteAck;
            return this;
        }

        public Builder timeoutSeconds(double timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
            return this;
        }

        public Builder intervalSeconds(double intervalSeconds) {
            this.intervalSeconds = intervalSeconds;
            return this;
        }

        public ChangeIpOptions build() {
            return new ChangeIpOptions(this);
        }
    }
}

