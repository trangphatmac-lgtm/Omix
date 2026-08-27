package org.fisproxy;

import java.util.Map;

/** HTTP transport. Tests inject a fake; production uses {@code java.net.http.HttpClient}. */
@FunctionalInterface
public interface Transport {
    RawResponse exchange(String method, String url, Map<String, String> headers, byte[] body);
}

