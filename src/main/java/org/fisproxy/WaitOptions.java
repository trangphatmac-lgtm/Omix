package org.fisproxy;

import java.util.function.Predicate;

/** Polling options for {@link Client#waitOperation(String, WaitOptions)}. */
public final class WaitOptions {
    private final double timeoutSeconds;
    private final double intervalSeconds;
    private final Predicate<Operation> until;

    private WaitOptions(Builder builder) {
        this.timeoutSeconds = builder.timeoutSeconds;
        this.intervalSeconds = builder.intervalSeconds;
        this.until = builder.until;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static WaitOptions defaults() {
        return builder().build();
    }

    public double timeoutSeconds() {
        return timeoutSeconds;
    }

    public double intervalSeconds() {
        return intervalSeconds;
    }

    public Predicate<Operation> until() {
        return until;
    }

    public static final class Builder {
        private double timeoutSeconds = 180.0;
        private double intervalSeconds = 1.0;
        private Predicate<Operation> until;

        private Builder() {
        }

        public Builder timeoutSeconds(double timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
            return this;
        }

        public Builder intervalSeconds(double intervalSeconds) {
            this.intervalSeconds = intervalSeconds;
            return this;
        }

        public Builder until(Predicate<Operation> until) {
            this.until = until;
            return this;
        }

        public WaitOptions build() {
            return new WaitOptions(this);
        }
    }
}

