package org.fisproxy;

import java.util.Map;

/** The API rejected this request. Transient cases are retried automatically. */
public class AdmissionException extends FisProxyException {
    public AdmissionException(int status, Map<String, ?> payload) {
        super(status, payload);
    }
}

