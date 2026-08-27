package org.fisproxy;

import java.util.LinkedHashMap;
import java.util.Map;

/** Polling deadline while waiting for an async operation. */
public class OperationTimeoutException extends FisProxyException {
    private final String operationId;
    private final Operation last;

    public OperationTimeoutException(String operationId, Operation last) {
        super(408, payloadOf(operationId));
        this.operationId = operationId;
        this.last = last;
    }

    public String operationId() {
        return operationId;
    }

    public Operation last() {
        return last;
    }

    private static Map<String, Object> payloadOf(String operationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("errorCode", "OPERATION_TIMEOUT");
        payload.put("message", "operation " + operationId + " did not finish in time");
        return payload;
    }
}

