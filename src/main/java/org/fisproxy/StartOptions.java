package org.fisproxy;

/** Optional fields for {@link Client#start(StartOptions)}. */
public final class StartOptions {
    private final String serviceId;
    private final String target;
    private final Boolean autoNfa;
    private final String nfaItemId;
    private final String reuseNfaItemId;
    private final String nfaSource;
    private final String nfaSku;
    private final Boolean tryPreviousNfa;
    private final String idempotencyKey;
    private final boolean wait;
    private final double timeoutSeconds;
    private final double intervalSeconds;

    private StartOptions(Builder builder) {
        this.serviceId = builder.serviceId;
        this.target = builder.target;
        this.autoNfa = builder.autoNfa;
        this.nfaItemId = builder.nfaItemId;
        this.reuseNfaItemId = builder.reuseNfaItemId;
        this.nfaSource = builder.nfaSource;
        this.nfaSku = builder.nfaSku;
        this.tryPreviousNfa = builder.tryPreviousNfa;
        this.idempotencyKey = builder.idempotencyKey;
        this.wait = builder.wait;
        this.timeoutSeconds = builder.timeoutSeconds;
        this.intervalSeconds = builder.intervalSeconds;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static StartOptions defaults() {
        return builder().build();
    }

    public String serviceId() {
        return serviceId;
    }

    public String target() {
        return target;
    }

    public Boolean autoNfa() {
        return autoNfa;
    }

    public String nfaItemId() {
        return nfaItemId;
    }

    public String reuseNfaItemId() {
        return reuseNfaItemId;
    }

    public String nfaSource() {
        return nfaSource;
    }

    public String nfaSku() {
        return nfaSku;
    }

    public Boolean tryPreviousNfa() {
        return tryPreviousNfa;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public boolean shouldWait() {
        return wait;
    }

    public double timeoutSeconds() {
        return timeoutSeconds;
    }

    public double intervalSeconds() {
        return intervalSeconds;
    }

    public static final class Builder {
        private String serviceId;
        private String target;
        private Boolean autoNfa;
        private String nfaItemId;
        private String reuseNfaItemId;
        private String nfaSource;
        private String nfaSku;
        private Boolean tryPreviousNfa;
        private String idempotencyKey;
        private boolean wait = true;
        private double timeoutSeconds = 180.0;
        private double intervalSeconds = 1.0;

        private Builder() {
        }

        public Builder serviceId(String serviceId) {
            this.serviceId = serviceId;
            return this;
        }

        public Builder target(String target) {
            this.target = target;
            return this;
        }

        public Builder autoNfa(Boolean autoNfa) {
            this.autoNfa = autoNfa;
            return this;
        }

        public Builder nfaItemId(String nfaItemId) {
            this.nfaItemId = nfaItemId;
            return this;
        }

        public Builder reuseNfaItemId(String reuseNfaItemId) {
            this.reuseNfaItemId = reuseNfaItemId;
            return this;
        }

        public Builder nfaSource(String nfaSource) {
            this.nfaSource = nfaSource;
            return this;
        }

        public Builder nfaSku(String nfaSku) {
            this.nfaSku = nfaSku;
            return this;
        }

        public Builder tryPreviousNfa(Boolean tryPreviousNfa) {
            this.tryPreviousNfa = tryPreviousNfa;
            return this;
        }

        public Builder idempotencyKey(String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        public Builder wait(boolean wait) {
            this.wait = wait;
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

        public StartOptions build() {
            return new StartOptions(this);
        }
    }
}

