package me.ksyz.accountmanager.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.client.session.Session;

import java.io.InputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

public final class MicrosoftAuth {
    public static final String DEFAULT_CLIENT_ID = "42a60a84-599d-44b2-a7c6-b00cdef1d6a2";
    public static final String DEFAULT_SCOPE = "XboxLive.signin XboxLive.offline_access";
    public static final String TOKEN_CLIENT_ID = "00000000402b5328";
    public static final String TOKEN_SCOPE = "service::user.auth.xboxlive.com::MBI_SSL";
    public static final String DESKTOP_CLIENT_ID = "000000004C12AE6F";
    public static final String DESKTOP_REDIRECT_URI = "https://login.live.com/oauth20_desktop.srf";

    public static String CLIENT_ID = DEFAULT_CLIENT_ID;
    public static String SCOPE = DEFAULT_SCOPE;

    private static final int PORT = 25575;
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30L))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private MicrosoftAuth() {
    }

    public static String newState() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static URI getMSAuthLink(String state) {
        String query = "client_id=" + encode(CLIENT_ID)
                + "&response_type=code"
                + "&redirect_uri=" + encode(callbackUri())
                + "&scope=" + encode(SCOPE)
                + "&state=" + encode(state)
                + "&prompt=select_account";
        return URI.create("https://login.live.com/oauth20_authorize.srf?" + query);
    }

    public static CompletableFuture<String> acquireMSAuthCode(String state, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            HttpServer server = null;
            try {
                server = HttpServer.create(new InetSocketAddress(PORT), 0);
                CountDownLatch latch = new CountDownLatch(1);
                AtomicReference<String> authCode = new AtomicReference<>();
                AtomicReference<String> errorMsg = new AtomicReference<>();

                server.createContext("/callback", exchange -> {
                    Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
                    if (!state.equals(query.get("state"))) {
                        errorMsg.set("State mismatch. Please retry Microsoft login.");
                    } else if (query.containsKey("code")) {
                        authCode.set(query.get("code"));
                    } else if (query.containsKey("error")) {
                        errorMsg.set(query.get("error") + ": " + query.getOrDefault("error_description", ""));
                    }

                    byte[] response = callbackHtml();
                    exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
                    exchange.sendResponseHeaders(200, response.length);
                    exchange.getResponseBody().write(response);
                    exchange.getResponseBody().close();
                    latch.countDown();
                });

                server.start();
                latch.await();
                String code = authCode.get();
                if (isBlank(code)) {
                    throw new IllegalStateException(Optional.ofNullable(errorMsg.get())
                            .orElse("There was no auth code or error description present."));
                }
                return code;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CancellationException("Microsoft auth code acquisition was cancelled.");
            } catch (Exception e) {
                throw new CompletionException("Unable to acquire Microsoft auth code.", e);
            } finally {
                if (server != null) {
                    server.stop(2);
                }
            }
        }, executor);
    }

    public static CompletableFuture<Map<String, String>> acquireMSAccessTokens(String authCode, Executor executor) {
        return CompletableFuture.supplyAsync(() -> requestMicrosoftTokens(Map.of(
                "client_id", CLIENT_ID,
                "grant_type", "authorization_code",
                "code", authCode,
                "redirect_uri", callbackUri()
        )), executor);
    }

    public static CompletableFuture<Map<String, String>> refreshMSAccessTokens(String refreshToken, Executor executor) {
        Map<String, String> form = new HashMap<>();
        form.put("client_id", CLIENT_ID);
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", refreshToken);
        if (TOKEN_CLIENT_ID.equals(CLIENT_ID)) {
            form.put("scope", SCOPE);
        } else if (DESKTOP_CLIENT_ID.equals(CLIENT_ID)) {
            form.put("redirect_uri", DESKTOP_REDIRECT_URI);
            form.put("scope", SCOPE);
        } else {
            form.put("redirect_uri", callbackUri());
        }
        return CompletableFuture.supplyAsync(() -> requestMicrosoftTokens(form), executor);
    }

    public static CompletableFuture<String> acquireRefreshTokenWithCredentials(String email, String password, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(30L))
                        .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
                LegacyPreAuth preAuth = legacyPreAuth(client);

                HttpRequest loginRequest = HttpRequest.newBuilder(URI.create(preAuth.urlPost()))
                        .timeout(Duration.ofSeconds(30L))
                        .header("User-Agent", legacyUserAgent())
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(encodeForm(Map.of(
                                "login", email,
                                "loginfmt", email,
                                "passwd", password,
                                "PPFT", preAuth.ppft()
                        ))))
                        .build();
                HttpResponse<String> response = client.send(loginRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() >= 300 && response.statusCode() < 400) {
                    String location = response.headers().firstValue("Location")
                            .orElseThrow(() -> new IllegalStateException("Microsoft login did not return a redirect."));
                    String fragment = location.contains("#") ? location.substring(location.indexOf('#') + 1) : "";
                    String refreshToken = parseQuery(fragment).get("refresh_token");
                    if (!isBlank(refreshToken)) {
                        return refreshToken;
                    }
                    throw new IllegalStateException("Microsoft login did not return a refresh token.");
                }

                String html = response.body() == null ? "" : response.body();
                String lower = html.toLowerCase();
                if (lower.contains("help us protect your account")) {
                    throw new IllegalStateException("This account requires 2FA/MFA or extra verification.");
                }
                if (lower.contains("sign in to") || lower.contains("password")) {
                    throw new IllegalStateException("Wrong email or password.");
                }
                throw new IllegalStateException("Unexpected Microsoft login response: " + response.statusCode());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CancellationException("Microsoft credential login was cancelled.");
            } catch (Exception e) {
                throw new CompletionException("Unable to login with email and password.", e);
            }
        }, executor);
    }

    public static CompletableFuture<String> acquireXboxAccessToken(String accessToken, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            JsonObject properties = new JsonObject();
            properties.addProperty("AuthMethod", "RPS");
            properties.addProperty("SiteName", "user.auth.xboxlive.com");
            properties.addProperty("RpsTicket", xboxRpsTicket(accessToken));

            JsonObject body = new JsonObject();
            body.add("Properties", properties);
            body.addProperty("RelyingParty", "http://auth.xboxlive.com");
            body.addProperty("TokenType", "JWT");

            JsonObject json = postJson("https://user.auth.xboxlive.com/user/authenticate", body);
            return requireString(json, "Token", xboxError(json, "There was no Xbox access token present."));
        }, executor);
    }

    public static CompletableFuture<Map<String, String>> acquireXboxXstsToken(String accessToken, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            JsonArray userTokens = new JsonArray();
            userTokens.add(accessToken);

            JsonObject properties = new JsonObject();
            properties.addProperty("SandboxId", "RETAIL");
            properties.add("UserTokens", userTokens);

            JsonObject body = new JsonObject();
            body.add("Properties", properties);
            body.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
            body.addProperty("TokenType", "JWT");

            JsonObject json = postJson("https://xsts.auth.xboxlive.com/xsts/authorize", body);
            String token = requireString(json, "Token", xboxError(json, "There was no Xbox XSTS token present."));
            String userHash = json.getAsJsonObject("DisplayClaims")
                    .getAsJsonArray("xui")
                    .get(0)
                    .getAsJsonObject()
                    .get("uhs")
                    .getAsString();
            Map<String, String> result = new HashMap<>();
            result.put("Token", token);
            result.put("uhs", userHash);
            return result;
        }, executor);
    }

    public static CompletableFuture<String> acquireMCAccessToken(String xstsToken, String userHash, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            JsonObject body = new JsonObject();
            body.addProperty("identityToken", "XBL3.0 x=" + userHash + ";" + xstsToken);
            JsonObject json = postJson("https://api.minecraftservices.com/authentication/login_with_xbox", body);
            return requireString(json, "access_token", serviceError(json, "There was no Minecraft access token present."));
        }, executor);
    }

    public static CompletableFuture<Session> login(String mcToken, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.minecraftservices.com/minecraft/profile"))
                    .timeout(Duration.ofSeconds(30L))
                    .header("Authorization", "Bearer " + mcToken)
                    .GET()
                    .build();
            JsonObject json = sendJson(request);
            String id = requireString(json, "id", serviceError(json, "There was no Minecraft profile present."));
            String name = requireString(json, "name", "Minecraft profile did not include a username.");
            return new Session(
                    name,
                    parseUndashedUuid(id),
                    mcToken,
                    Optional.empty(),
                    Optional.empty()
            );
        }, executor);
    }

    private static Map<String, String> requestMicrosoftTokens(Map<String, String> form) {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://login.live.com/oauth20_token.srf"))
                .timeout(Duration.ofSeconds(30L))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encodeForm(form)))
                .build();
        JsonObject json = sendJson(request);
        String accessToken = requireString(json, "access_token", serviceError(json, "There was no Microsoft access token present."));
        String refreshToken = requireString(json, "refresh_token", serviceError(json, "There was no Microsoft refresh token present."));
        Map<String, String> result = new HashMap<>();
        result.put("access_token", accessToken);
        result.put("refresh_token", refreshToken);
        return result;
    }

    private static LegacyPreAuth legacyPreAuth(HttpClient client) {
        String authorizeUrl = "https://login.live.com/oauth20_authorize.srf?"
                + "client_id=" + encode(DESKTOP_CLIENT_ID)
                + "&redirect_uri=" + encode(DESKTOP_REDIRECT_URI)
                + "&scope=" + encode(TOKEN_SCOPE)
                + "&display=touch"
                + "&response_type=token"
                + "&locale=en";
        HttpRequest request = HttpRequest.newBuilder(URI.create(authorizeUrl))
                .timeout(Duration.ofSeconds(30L))
                .header("User-Agent", legacyUserAgent())
                .GET()
                .build();
        try {
            HttpResponse<String> response = sendFollowingRedirects(client, request, 5);
            String html = response.body() == null ? "" : response.body();
            String ppft = match(html, "sFTTag[\\'\\\"]\\s*:\\s*[\\'\\\"].*?value=\\\\?[\\'\\\"](.*?)\\\\?[\\'\\\"]");
            String urlPost = match(html, "urlPost[\\'\\\"]\\s*:\\s*[\\'\\\"](.+?)[\\'\\\"]");
            if (isBlank(ppft) || isBlank(urlPost)) {
                throw new IllegalStateException("Could not read Microsoft PPFT login fields.");
            }
            return new LegacyPreAuth(ppft, decodeJsString(urlPost));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Microsoft pre-auth was cancelled.");
        } catch (Exception e) {
            throw new CompletionException("Unable to acquire Microsoft pre-auth fields.", e);
        }
    }

    private static HttpResponse<String> sendFollowingRedirects(HttpClient client, HttpRequest request, int limit) throws Exception {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int remaining = limit;
        while (remaining-- > 0 && response.statusCode() >= 300 && response.statusCode() < 400) {
            String location = response.headers().firstValue("Location").orElse("");
            if (location.isBlank()) {
                break;
            }
            URI next = request.uri().resolve(location);
            request = HttpRequest.newBuilder(next)
                    .timeout(Duration.ofSeconds(30L))
                    .header("User-Agent", legacyUserAgent())
                    .GET()
                    .build();
            response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }
        return response;
    }

    private static JsonObject postJson(String url, JsonObject body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30L))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        return sendJson(request);
    }

    private static JsonObject sendJson(HttpRequest request) {
        try {
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String body = response.body() == null || response.body().isBlank() ? "{}" : response.body();
            return JsonParser.parseString(body).getAsJsonObject();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CancellationException("HTTP request was cancelled.");
        } catch (Exception e) {
            throw new CompletionException("HTTP request failed.", e);
        }
    }

    private static String requireString(JsonObject json, String key, String error) {
        JsonElement value = json.get(key);
        if (value != null && !value.isJsonNull()) {
            String text = value.getAsString();
            if (!isBlank(text)) {
                return text;
            }
        }
        throw new CompletionException(error, null);
    }

    private static String serviceError(JsonObject json, String fallback) {
        if (json.has("error")) {
            String message = json.get("error").getAsString();
            if (json.has("error_description")) {
                message += ": " + json.get("error_description").getAsString();
            } else if (json.has("errorMessage")) {
                message += ": " + json.get("errorMessage").getAsString();
            }
            return message;
        }
        return fallback;
    }

    private static String xboxError(JsonObject json, String fallback) {
        if (json.has("XErr")) {
            return json.get("XErr").getAsString() + ": " + json.get("Message").getAsString();
        }
        return serviceError(json, fallback);
    }

    private static UUID parseUndashedUuid(String id) {
        String value = id.replace("-", "");
        if (value.length() == 32) {
            value = value.substring(0, 8) + "-"
                    + value.substring(8, 12) + "-"
                    + value.substring(12, 16) + "-"
                    + value.substring(16, 20) + "-"
                    + value.substring(20);
        }
        return UUID.fromString(value);
    }

    private static byte[] callbackHtml() {
        try (InputStream stream = MicrosoftAuth.class.getResourceAsStream("/callback.html")) {
            return stream == null
                    ? "<html><body>You can close this tab and return to Minecraft.</body></html>".getBytes(StandardCharsets.UTF_8)
                    : stream.readAllBytes();
        } catch (Exception ignored) {
            return new byte[0];
        }
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> result = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return result;
        }
        for (String part : rawQuery.split("&")) {
            int split = part.indexOf('=');
            String key = split >= 0 ? part.substring(0, split) : part;
            String value = split >= 0 ? part.substring(split + 1) : "";
            result.put(decode(key), decode(value));
        }
        return result;
    }

    private static String encodeForm(Map<String, String> form) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : form.entrySet()) {
            if (!builder.isEmpty()) {
                builder.append('&');
            }
            builder.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
        }
        return builder.toString();
    }

    private static String callbackUri() {
        return "http://localhost:" + PORT + "/callback";
    }

    private static String xboxRpsTicket(String accessToken) {
        if (TOKEN_SCOPE.equals(SCOPE) || TOKEN_CLIENT_ID.equals(CLIENT_ID) || DESKTOP_CLIENT_ID.equals(CLIENT_ID)) {
            return accessToken;
        }
        return "d=" + accessToken;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String match(String text, String regex) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.DOTALL).matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String decodeJsString(String value) {
        return value.replace("\\/", "/")
                .replace("\\u0026", "&")
                .replace("\\u003a", ":")
                .replace("\\u003A", ":")
                .replace("&amp;", "&");
    }

    private static String legacyUserAgent() {
        return "Mozilla/5.0 (XboxReplay; XboxLiveAuth/3.0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/71.0.3578.98 Safari/537.36";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record LegacyPreAuth(String ppft, String urlPost) {
    }
}
