package cn.omix.util.account.cookie;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CancellationException;

/** Follows authentication redirects while applying cookies separately to each destination. */
final class CookieHttpClient {
    static final Duration TIMEOUT = Duration.ofSeconds(30);
    static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36";
    private final CookieJar jar;
    private final Transport transport;

    CookieHttpClient(CookieJar jar) {
        this(jar, request -> {
            HttpResponse<Void> response = CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
            return new Response(response.statusCode(), response.headers().firstValue("Location").orElse(null),
                    response.headers().allValues("Set-Cookie"));
        });
    }

    CookieHttpClient(CookieJar jar, Transport transport) {
        this.jar = jar;
        this.transport = transport;
    }

    void followRedirects(String startUrl, int maxRedirects) throws IOException {
        follow(startUrl, maxRedirects, null, false);
    }

    String followOAuthRedirects(String startUrl, int maxRedirects, List<String> preferredOrder) throws IOException {
        return follow(startUrl, maxRedirects, preferredOrder, true);
    }

    private String follow(String startUrl, int maxRedirects, List<String> preferredOrder, boolean oauth) throws IOException {
        URI uri = resolve(null, startUrl);
        for (int hop = 0; hop < maxRedirects; hop++) {
            checkInterrupted();
            requireAuthUri(uri);
            HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(TIMEOUT)
                    .header("User-Agent", USER_AGENT).header("Accept-Language", "en-US,en;q=0.9")
                    .header("Accept", oauth ? "*/*" : "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            String cookies = jar.buildCookieHeader(uri, preferredOrder);
            if (!cookies.isEmpty()) request.header("Cookie", cookies);
            Response response;
            try {
                response = transport.send(request.GET().build());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CancellationException("Cookie login cancelled.");
            }
            jar.mergeResponseCookies(uri, response.cookies());
            if (!isRedirect(response.status())) {
                if (response.status() < 200 || response.status() >= 300) {
                    throw new IOException("Authentication request failed (HTTP " + response.status() + ").");
                }
                return null;
            }
            if (response.location() == null || response.location().isBlank()) {
                throw new IOException("Authentication redirect did not include a destination.");
            }
            URI next = resolve(uri, response.location());
            requireAuthUri(next);
            if (oauth) {
                String error = parameter(next, "error");
                if (error != null) {
                    // Do not surface arbitrary redirect URLs/descriptions containing credentials.
                    throw new IOException("Microsoft requires a fresh browser login or consent. Export new cookies after signing in.");
                }
                String token = parameter(next, "access_token");
                if (token != null && !token.isBlank()) return token;
            }
            uri = next;
        }
        throw new IOException("Too many authentication redirects.");
    }

    static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Cookie login cancelled.");
        }
    }

    private static void requireAuthUri(URI uri) throws IOException {
        if (!CookieJar.isAuthUri(uri)) {
            throw new IOException("Unsupported authentication redirect destination.");
        }
    }

    private static URI resolve(URI base, String location) throws IOException {
        try {
            return base == null ? URI.create(location) : base.resolve(location);
        } catch (IllegalArgumentException e) {
            throw new IOException("Malformed authentication redirect.");
        }
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private static String parameter(URI uri, String name) throws IOException {
        for (String part : new String[]{uri.getRawFragment(), uri.getRawQuery()}) {
            if (part == null) continue;
            for (String pair : part.split("&")) {
                int equals = pair.indexOf('=');
                if (equals > 0 && pair.substring(0, equals).equals(name)) {
                    try {
                        return URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8);
                    } catch (IllegalArgumentException e) {
                        throw new IOException("Malformed OAuth response.");
                    }
                }
            }
        }
        return null;
    }

    @FunctionalInterface
    interface Transport {
        Response send(HttpRequest request) throws IOException, InterruptedException;
    }

    record Response(int status, String location, List<String> cookies) {
    }
}
