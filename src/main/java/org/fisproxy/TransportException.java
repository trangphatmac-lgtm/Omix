package org.fisproxy;

import java.util.Map;

/** Network or HTTP transport failure before a JSON API error was returned. */
public class TransportException extends FisProxyException {
    public TransportException(int status, Map<String, ?> payload) {
        super(status, payload);
    }
}

