package org.fisproxy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Raw HTTP response used by {@link Transport}. */
public final class RawResponse {
    private final int status;
    private final Map<String, String> headers;
    private final byte[] body;

    public RawResponse(int status, Map<String, String> headers, byte[] body) {
        this.status = status;
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers == null ? Map.of() : headers));
        this.body = body == null ? new byte[0] : body.clone();
    }

    public int status() {
        return status;
    }

    public Map<String, String> headers() {
        return headers;
    }

    public byte[] body() {
        return body.clone();
    }

    byte[] bodyRaw() {
        return body;
    }
}

