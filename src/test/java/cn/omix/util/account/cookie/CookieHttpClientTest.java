package cn.omix.util.account.cookie;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.*;

class CookieHttpClientTest {
    private static final String START = "https://login.live.com/oauth20_authorize.srf";

    @Test void followsRelativeAnd308RedirectsMergesCookiesAndExtractsTokenBeforeCallback() throws Exception {
        CookieJar jar = CookieJar.parse("JSH=original");
        List<HttpRequest> requests = new ArrayList<>();
        CookieHttpClient client = new CookieHttpClient(jar, request -> {
            requests.add(request);
            return switch (requests.size()) {
                case 1 -> new CookieHttpClient.Response(302, "/next", List.of("JSH=rotated; Secure; Path=/"));
                case 2 -> new CookieHttpClient.Response(308, "https://sisu.xboxlive.com/authorize", List.of());
                case 3 -> new CookieHttpClient.Response(302,
                        "https://sisu.xboxlive.com/connect/oauth/XboxLive#access_token=sample%2Btoken%3D&token_type=bearer", List.of());
                default -> throw new AssertionError("The OAuth callback must not be fetched.");
            };
        });
        assertEquals("sample+token=", client.followOAuthRedirects(START, 12, List.of("JSH")));
        assertEquals("JSH=original", requests.get(0).headers().firstValue("Cookie").orElseThrow());
        assertEquals("JSH=rotated", requests.get(1).headers().firstValue("Cookie").orElseThrow());
        assertTrue(requests.get(2).headers().firstValue("Cookie").isEmpty());
        assertEquals("JSH=rotated", CookieJar.deserialize(jar.serialize()).buildCookieHeader(URI.create(START)));
    }

    @Test void rejectsUnsafeRedirectsBeforeSendingCookiesOrAcceptingTokens() {
        for (String location : List.of("https://login.live.com.evil.test/#access_token=secret",
                "http://login.live.com/", "file:///private", "https://user@login.live.com/")) {
            List<HttpRequest> requests = new ArrayList<>();
            CookieHttpClient client = new CookieHttpClient(CookieJar.parse("JSH=sample"), request -> {
                requests.add(request);
                return new CookieHttpClient.Response(302, location, List.of());
            });
            IOException error = assertThrows(IOException.class, () -> client.followOAuthRedirects(START, 12, null));
            assertEquals(1, requests.size());
            assertFalse(error.getMessage().contains("secret"));
        }
    }

    @Test void doesNotFollowOrAcceptLocationFromSuccessfulPage() throws Exception {
        CookieHttpClient client = new CookieHttpClient(new CookieJar(), request ->
                new CookieHttpClient.Response(200, "https://login.live.com/#access_token=ignore", List.of()));
        assertNull(client.followOAuthRedirects(START, 12, null));
    }

    @Test void oauthErrorsAreActionableWithoutEchoingPrivateDescriptions() {
        CookieHttpClient client = new CookieHttpClient(new CookieJar(), request ->
                new CookieHttpClient.Response(302,
                        "https://login.live.com/return#error=interaction_required&error_description=private-token", List.of()));
        IOException error = assertThrows(IOException.class, () -> client.followOAuthRedirects(START, 12, null));
        assertTrue(error.getMessage().contains("fresh browser login"));
        assertFalse(error.getMessage().contains("private-token"));
    }

    @Test void stopsLoopsAtConfiguredLimit() {
        List<HttpRequest> requests = new ArrayList<>();
        CookieHttpClient client = new CookieHttpClient(new CookieJar(), request -> {
            requests.add(request);
            return new CookieHttpClient.Response(302, "/loop", List.of());
        });
        assertThrows(IOException.class, () -> client.followRedirects(START, 3));
        assertEquals(3, requests.size());
    }

    @Test void handlesMissingLocationsAndHttpFailures() {
        for (var response : List.of(new CookieHttpClient.Response(302, null, List.of()),
                new CookieHttpClient.Response(503, null, List.of()))) {
            CookieHttpClient client = new CookieHttpClient(new CookieJar(), request -> response);
            assertThrows(IOException.class, () -> client.followOAuthRedirects(START, 3, null));
        }
    }

    @Test void cancellationPreservesInterruptAndStopsRedirects() {
        CookieHttpClient client = new CookieHttpClient(new CookieJar(), request -> {
            throw new InterruptedException();
        });
        try {
            assertThrows(CancellationException.class, () -> client.followRedirects(START, 3));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
}
