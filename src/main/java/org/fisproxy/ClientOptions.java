package org.fisproxy;

import java.time.Duration;

/** Optional Client constructor settings. */
public final class ClientOptions {
    private final String baseUrl;
    private final String clientId;
    private final Duration timeout;
    private final String userAgent;
    private final Transport transport;

    private ClientOptions(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.clientId = builder.clientId;
        this.timeout = builder.timeout;
        this.userAgent = builder.userAgent;
        this.transport = builder.transport;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ClientOptions defaults() {
        return builder().build();
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String clientId() {
        return clientId;
    }

    public Duration timeout() {
        return timeout;
    }

    public String userAgent() {
        return userAgent;
    }

    public Transport transport() {
        return transport;
    }

    public static final class Builder {
        private String baseUrl;
        private String clientId;
        private Duration timeout;
        private String userAgent;
        private Transport transport;

        private Builder() {
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public Builder transport(Transport transport) {
            this.transport = transport;
            return this;
        }

        public ClientOptions build() {
            return new ClientOptions(this);
        }
    }
}

