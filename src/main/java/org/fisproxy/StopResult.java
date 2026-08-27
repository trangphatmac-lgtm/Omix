package org.fisproxy;

import java.util.Map;
import org.fisproxy.internal.Hmac;
import org.fisproxy.internal.Json;

/** Settlement from {@code POST /api/v1/sessions/stop}. */
public final class StopResult {
    private final int duration;
    private final String deduction;
    private final Map<String, Object> session;
    private final int canceledOperations;
    private final Map<String, Object> raw;

    public StopResult(
            int duration,
            String deduction,
            Map<String, Object> session,
            int canceledOperations,
            Map<String, Object> raw) {
        this.duration = duration;
        this.deduction = deduction;
        this.session = session == null ? null : Json.freeze(session);
        this.canceledOperations = canceledOperations;
        this.raw = Json.freeze(raw);
    }

    public static StopResult fromResponse(Map<String, Object> payload) {
        Object durationValue = payload.get("duration");
        long duration = durationValue == null ? 0L : requireInt(durationValue, "duration");
        Object sessionValue = payload.get("session");
        Object canceledValue = payload.get("canceledOperations");
        long canceled = canceledValue == null ? 0L : requireInt(canceledValue, "canceledOperations");
        Object deduction = payload.get("deduction");
        return new StopResult(
                Math.toIntExact(duration),
                DecimalStrings.decimalStr(deduction == null ? 0 : deduction),
                sessionValue instanceof Map<?, ?> map ? Json.asObject(map) : null,
                Math.toIntExact(canceled),
                payload);
    }

    public int duration() {
        return duration;
    }

    public String deduction() {
        return deduction;
    }

    public Map<String, Object> session() {
        return session;
    }

    public int canceledOperations() {
        return canceledOperations;
    }

    public Map<String, Object> raw() {
        return raw;
    }

    private static long requireInt(Object value, String field) {
        Long parsed = Hmac.asLong(value);
        if (parsed == null) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        return parsed;
    }
}

