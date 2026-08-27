package org.fisproxy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.fisproxy.internal.Json;

/** Session plus connection entrances from {@code GET /api/v1/sessions/status}. */
public final class SessionStatus {
    private final boolean running;
    private final Map<String, Object> session;
    private final String address;
    private final String entrance;
    private final List<Entrance> entrances;
    private final Map<String, Object> raw;

    public SessionStatus(
            boolean running,
            Map<String, Object> session,
            String address,
            String entrance,
            List<Entrance> entrances,
            Map<String, Object> raw) {
        this.running = running;
        this.session = session == null ? null : Json.freeze(session);
        this.address = address;
        this.entrance = entrance;
        this.entrances = List.copyOf(entrances);
        this.raw = Json.freeze(raw);
    }

    public static SessionStatus fromResponse(Map<String, Object> payload) {
        Object sessionValue = payload.get("session");
        Map<String, Object> session = sessionValue instanceof Map<?, ?> map ? Json.asObject(map) : null;
        List<Entrance> entrances = new ArrayList<>();
        Object rawEntrances = payload.get("entrances");
        if (rawEntrances instanceof List<?> items) {
            for (Object item : items) {
                entrances.add(Entrance.fromMapping(item));
            }
        }
        String address = stringOrNull(payload.get("address"));
        if (address == null) {
            address = stringOrNull(payload.get("entrance"));
        }
        if (address == null && !entrances.isEmpty()) {
            address = entrances.get(0).address();
        }
        String entrance = stringOrNull(payload.get("entrance"));
        if (entrance == null) {
            entrance = address;
        }
        return new SessionStatus(
                Boolean.TRUE.equals(payload.get("running")),
                session,
                address,
                entrance,
                entrances,
                payload);
    }

    public boolean running() {
        return running;
    }

    public Map<String, Object> session() {
        return session;
    }

    public String address() {
        return address;
    }

    public String entrance() {
        return entrance;
    }

    public List<Entrance> entrances() {
        return entrances;
    }

    public Map<String, Object> raw() {
        return raw;
    }

    private static String stringOrNull(Object value) {
        return value instanceof String text ? text : null;
    }
}

