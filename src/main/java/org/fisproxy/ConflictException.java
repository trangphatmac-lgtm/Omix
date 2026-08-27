package org.fisproxy;

import java.util.Map;
import org.fisproxy.internal.Json;

/** Another operation is already running (HTTP 409). */
public class ConflictException extends FisProxyException {
    private final Map<String, Object> existingOperation;

    public ConflictException(int status, Map<String, ?> payload) {
        super(status, payload);
        Object existing = this.payload().get("existingOperation");
        this.existingOperation = existing instanceof Map<?, ?> map ? Json.freeze(Json.asObject(map)) : null;
    }

    public Map<String, Object> existingOperation() {
        return existingOperation;
    }
}

