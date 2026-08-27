package org.fisproxy;

import java.util.Map;
import org.fisproxy.internal.Json;

/** A connection entrance. Minecraft uses {@link #address()}. */
public final class Entrance {
    private final String id;
    private final String name;
    private final String host;
    private final String address;
    private final Map<String, Object> raw;

    public Entrance(String id, String name, String host, String address, Map<String, Object> raw) {
        this.id = id;
        this.name = name;
        this.host = host;
        this.address = address;
        this.raw = Json.freeze(raw);
    }

    public static Entrance fromMapping(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("entrance must be an object");
        }
        Map<String, Object> data = Json.asObject(map);
        String host = stringify(data.get("host"));
        String addressValue = stringify(data.get("address"));
        String address = addressValue.isEmpty() ? host : addressValue;
        String id = stringify(data.get("id"));
        String nameValue = stringify(data.get("name"));
        String name = nameValue.isEmpty() ? id : nameValue;
        return new Entrance(id, name, host, address, data);
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String host() {
        return host;
    }

    public String address() {
        return address;
    }

    public Map<String, Object> raw() {
        return raw;
    }

    private static String stringify(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}

