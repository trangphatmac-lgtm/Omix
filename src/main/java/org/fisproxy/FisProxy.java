package org.fisproxy;

/** Alias of {@link Client}, matching the Python SDK export. */
public final class FisProxy extends Client {
    public FisProxy(String token) {
        super(token);
    }

    public FisProxy(String token, ClientOptions options) {
        super(token, options);
    }
}

