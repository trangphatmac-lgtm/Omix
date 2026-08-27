package org.fisproxy;

import java.util.Map;

/** User token is missing, revoked, banned, or otherwise rejected. */
public class AuthException extends FisProxyException {
    public AuthException(int status, Map<String, ?> payload) {
        super(status, payload);
    }
}

