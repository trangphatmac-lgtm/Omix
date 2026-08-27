package org.fisproxy;

/** Query flags for {@link Client#listOperations(ListOperationsOptions)}. */
public final class ListOperationsOptions {
    private final String status;
    private final String kind;
    private final Integer limit;

    private ListOperationsOptions(Builder builder) {
        this.status = builder.status;
        this.kind = builder.kind;
        this.limit = builder.limit;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ListOperationsOptions defaults() {
        return builder().build();
    }

    public String status() {
        return status;
    }

    public String kind() {
        return kind;
    }

    public Integer limit() {
        return limit;
    }

    public static final class Builder {
        private String status;
        private String kind;
        private Integer limit;

        private Builder() {
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder kind(String kind) {
            this.kind = kind;
            return this;
        }

        public Builder limit(Integer limit) {
            this.limit = limit;
            return this;
        }

        public ListOperationsOptions build() {
            return new ListOperationsOptions(this);
        }
    }
}

