package org.fisproxy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.fisproxy.internal.Json;

/** Async start / change-ip operation. */
public final class Operation {
    private static final Set<String> TERMINAL = Set.of("succeeded", "failed", "canceled");

    private final String id;
    private final String kind;
    private final String status;
    private final String progressPhase;
    private final Map<String, Object> result;
    private final String message;
    private final String errorCode;
    private final Map<String, Object> raw;

    public Operation(
            String id,
            String kind,
            String status,
            String progressPhase,
            Map<String, Object> result,
            String message,
            String errorCode,
            Map<String, Object> raw) {
        this.id = id;
        this.kind = kind;
        this.status = status;
        this.progressPhase = progressPhase;
        this.result = Json.freeze(result);
        this.message = message;
        this.errorCode = errorCode;
        this.raw = Json.freeze(raw);
    }

    public static Operation fromMapping(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("operation must be an object");
        }
        Map<String, Object> data = Json.asObject(map);
        Object resultValue = data.get("result");
        Map<String, Object> result = resultValue instanceof Map<?, ?> resultMap
                ? Json.asObject(resultMap)
                : Map.of();
        Object messageValue = data.get("message");
        Object errorValue = data.get("errorCode");
        return new Operation(
                stringify(data.get("id")),
                stringify(data.get("kind")),
                stringify(data.get("status")),
                stringify(data.get("progressPhase")),
                result,
                messageValue instanceof String text ? text : null,
                errorValue instanceof String text ? text : null,
                data);
    }

    public String id() {
        return id;
    }

    public String kind() {
        return kind;
    }

    public String status() {
        return status;
    }

    public String progressPhase() {
        return progressPhase;
    }

    public Map<String, Object> result() {
        return result;
    }

    public String message() {
        return message;
    }

    public String errorCode() {
        return errorCode;
    }

    public Map<String, Object> raw() {
        return raw;
    }

    public boolean terminal() {
        return TERMINAL.contains(status);
    }

    public boolean succeeded() {
        return "succeeded".equals(status);
    }

    public boolean routeAcked() {
        return Boolean.TRUE.equals(result.get("routeAcked"));
    }

    public String sessionId() {
        Object value = result.get("sessionId");
        if (value instanceof String text && !text.isEmpty()) {
            return text;
        }
        return null;
    }

    public List<Entrance> entrances() {
        Object rawEntrances = result.get("entrances");
        if (!(rawEntrances instanceof List<?> items)) {
            return List.of();
        }
        List<Entrance> result = new ArrayList<>();
        for (Object item : items) {
            result.add(Entrance.fromMapping(item));
        }
        return List.copyOf(result);
    }

    private static String stringify(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}

