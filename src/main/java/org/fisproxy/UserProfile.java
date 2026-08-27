package org.fisproxy;

import java.util.Map;
import org.fisproxy.internal.Json;

/** Profile from {@code GET /api/v1/me}. */
public final class UserProfile {
    private final String id;
    private final String username;
    private final Balances balances;
    private final Map<String, Object> session;
    private final Map<String, Object> raw;

    public UserProfile(
            String id,
            String username,
            Balances balances,
            Map<String, Object> session,
            Map<String, Object> raw) {
        this.id = id;
        this.username = username;
        this.balances = balances;
        this.session = session == null ? null : Json.freeze(session);
        this.raw = Json.freeze(raw);
    }

    public static UserProfile fromResponse(Map<String, Object> payload) {
        Object userValue = payload.get("user");
        if (!(userValue instanceof Map<?, ?> userMap)) {
            throw new IllegalArgumentException("missing user object");
        }
        Map<String, Object> user = Json.asObject(userMap);
        Object sessionValue = user.get("session");
        if (!(sessionValue instanceof Map<?, ?>)) {
            sessionValue = user.get("currentSession");
        }
        Map<String, Object> session = sessionValue instanceof Map<?, ?> map ? Json.asObject(map) : null;
        Object usernameValue = user.get("username");
        return new UserProfile(
                stringify(user.get("id")),
                usernameValue instanceof String text ? text : null,
                Balances.fromMapping(user.get("balances")),
                session,
                payload);
    }

    public String id() {
        return id;
    }

    public String username() {
        return username;
    }

    public Balances balances() {
        return balances;
    }

    public Map<String, Object> session() {
        return session;
    }

    public Map<String, Object> raw() {
        return raw;
    }

    private static String stringify(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}

