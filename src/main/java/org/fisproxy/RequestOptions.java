package org.fisproxy;

import java.util.Map;

/** Optional fields for {@link Client#request(String, String, RequestOptions)}. */
public final class RequestOptions {
    private final Map<String, ?> jsonBody;
    private final Map<String, ?> query;
    private final String idempotencyKey;
    private final Boolean sign;

    private RequestOptions(Builder builder) {
        this.jsonBody = builder.jsonBody;
        this.query = builder.query;
        this.idempotencyKey = builder.idempotencyKey;
        this.sign = builder.sign;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static RequestOptions none() {
        return builder().build();
    }

    public Map<String, ?> jsonBody() {
        return jsonBody;
    }

    public Map<String, ?> query() {
        return query;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public Boolean sign() {
        return sign;
    }

    public static final class Builder {
        private Map<String, ?> jsonBody;
        private Map<String, ?> query;
        private String idempotencyKey;
        private Boolean sign;

        private Builder() {
        }

        public Builder jsonBody(Map<String, ?> jsonBody) {
            this.jsonBody = jsonBody;
            return this;
        }

        public Builder query(Map<String, ?> query) {
            this.query = query;
            return this;
        }

        public Builder idempotencyKey(String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        public Builder sign(Boolean sign) {
            this.sign = sign;
            return this;
        }

        public RequestOptions build() {
            return new RequestOptions(this);
        }
    }
}

