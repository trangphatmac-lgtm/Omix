package org.fisproxy;

import java.util.List;
import java.util.Map;
import org.fisproxy.internal.Json;

/** Base type for API, transport, and operation errors. */
public class FisProxyException extends RuntimeException {
    private final int status;
    private final Map<String, Object> payload;
    private final String errorCode;
    private final String detail;

    public FisProxyException(int status, Map<String, ?> payload) {
        this(status, payload, null);
    }

    public FisProxyException(int status, Map<String, ?> payload, String message) {
        super(combined(errorCodeOf(payload), message != null ? message : errorMessageOf(payload, "HTTP " + status)));
        this.status = status;
        this.payload = Json.freeze(payload);
        this.errorCode = errorCodeOf(this.payload);
        this.detail = message != null ? message : errorMessageOf(this.payload, "HTTP " + status);
    }

    public int status() {
        return status;
    }

    public Map<String, Object> payload() {
        return payload;
    }

    public String errorCode() {
        return errorCode;
    }

    /** API error message, without the {@code errorCode:} prefix used by {@link #getMessage()}. */
    public String detail() {
        return detail;
    }

    public static String errorCodeOf(Object payload) {
        if (!(payload instanceof Map<?, ?> map)) {
            return "";
        }
        Object code = map.get("errorCode");
        if (code instanceof String text && !text.isEmpty()) {
            return text;
        }
        code = map.get("code");
        if (code instanceof String text && !text.isEmpty()) {
            return text;
        }
        Object nested = map.get("error");
        if (nested instanceof Map<?, ?> error) {
            Object inner = error.get("code");
            if (inner instanceof String text && !text.isEmpty()) {
                return text;
            }
        }
        return "";
    }

    public static String errorMessageOf(Object payload, String fallback) {
        if (!(payload instanceof Map<?, ?> map)) {
            return fallback;
        }
        for (String key : List.of("message", "msg", "error")) {
            Object value = map.get(key);
            if (value instanceof String text && !text.isEmpty()) {
                return text;
            }
        }
        return fallback;
    }

    private static String combined(String code, String message) {
        return (code != null && !code.isEmpty()) ? code + ": " + message : message;
    }
}

