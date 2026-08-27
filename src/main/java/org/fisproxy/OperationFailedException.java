package org.fisproxy;

import java.util.LinkedHashMap;
import java.util.Map;

/** Async start / change-ip ended {@code failed} or {@code canceled}. */
public class OperationFailedException extends FisProxyException {
    private final Operation operation;

    public OperationFailedException(Operation operation) {
        super(400, payloadOf(operation));
        this.operation = operation;
    }

    public Operation operation() {
        return operation;
    }

    private static Map<String, Object> payloadOf(Operation operation) {
        Map<String, Object> payload = new LinkedHashMap<>();
        String code = operation.errorCode();
        payload.put("errorCode", (code != null && !code.isEmpty()) ? code : "OPERATION_FAILED");
        String message = operation.message();
        payload.put("message", (message != null && !message.isEmpty())
                ? message
                : "operation " + operation.id() + " " + (operation.status().isEmpty() ? "failed" : operation.status()));
        payload.put("operation", operation.raw());
        return payload;
    }
}

