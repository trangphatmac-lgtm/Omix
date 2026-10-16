package cn.omix.util.account.cookie;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.ksyz.accountmanager.auth.MicrosoftAuth;
import net.minecraft.client.session.Session;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Cookie authentication only; the screen applies results on the Minecraft client thread. */
public final class CookieAuth {
    public static final String MARKER = "cookie";
    private static final Executor DIRECT = Runnable::run;
    private static final List<String> COOKIE_ORDER_JSHP = List.of(
            "__Host-MSAAUTH", "__Host-MSAAUTHP", "JSHP", "JSH",
            "MSPAuth", "MSPBack", "MSPProf", "MSPRequ", "MSPSoftVis", "MSPOK", "MSPShared", "MSPPre", "MSPCID",
            "MSPOAuthVis", "AMCSecAuth", "NAP", "ANON", "OParams", "PPLState", "WLSSC", "uaid", "pres", "LOpt");
    private static final List<String> COOKIE_ORDER_JSH = List.of(
            "__Host-MSAAUTH", "__Host-MSAAUTHP", "JSH", "JSHP",
            "MSPAuth", "MSPBack", "MSPProf", "MSPRequ", "MSPSoftVis", "MSPOK", "MSPShared", "MSPPre", "MSPCID",
            "MSPOAuthVis", "AMCSecAuth", "NAP", "ANON", "OParams", "PPLState", "WLSSC", "uaid", "pres", "LOpt");
    private static final String[] OAUTH_URLS = {
            "https://login.live.com/oauth20_authorize.srf?redirect_uri=https://sisu.xboxlive.com/connect/oauth/XboxLive&response_type=token&client_id=000000004420578E&scope=XboxLive.Signin%20XboxLive.offline_access&prompt=none",
            "https://login.live.com/oauth20_authorize.srf?client_id=00000000402b5328&redirect_uri=https%3A%2F%2Flogin.live.com%2Foauth20_desktop.srf&response_type=token&scope=service%3A%3Auser.auth.xboxlive.com%3A%3AMBI_SSL&prompt=none"
    };

    private CookieAuth() {
    }

    public static CookieJar parseFile(File file) throws IOException {
        return parseContent(Files.readString(file.toPath(), StandardCharsets.UTF_8));
    }

    public static CookieJar parseContent(String content) {
        return CookieJar.parse(content);
    }

    public static CompletableFuture<Result> login(CookieJar jar, Executor executor, Consumer<String> status) {
        return CompletableFuture.supplyAsync(() -> authenticate(jar, status), executor);
    }

    public static CompletableFuture<Result> relogin(String accessToken, String storedCookies,
                                                    Executor executor, Consumer<String> status) {
        return CompletableFuture.supplyAsync(() -> {
            CookieHttpClient.checkInterrupted();
            if (accessToken != null && !accessToken.isBlank()) {
                status.accept("Trying the stored Minecraft token...");
                Session cached = tryToken(accessToken);
                if (cached != null) return new Result(cached, storedCookies);
            }
            return authenticate(CookieJar.deserialize(storedCookies), status);
        }, executor);
    }

    static void verify(CookieJar jar) {
        if (jar == null || jar.isEmpty()) {
            throw new IllegalArgumentException("No usable Microsoft or Minecraft cookies found.");
        }
        if (!jar.hasRequiredAuthCookies() && MinecraftNetAuth.extractMinecraftAccessToken(jar) == null) {
            throw new IllegalArgumentException("Missing login cookies: __Host-MSAAUTH, __Host-MSAAUTHP, JSH or JSHP.");
        }
    }

    private static Result authenticate(CookieJar jar, Consumer<String> status) {
        CookieHttpClient.checkInterrupted();
        verify(jar);
        status.accept("Read " + jar.size() + " cookies. Checking Minecraft token...");
        Set<String> rejectedTokens = new HashSet<>();
        String directToken;
        while ((directToken = MinecraftNetAuth.extractMinecraftAccessToken(jar, rejectedTokens)) != null) {
            Session directSession = tryToken(directToken);
            if (directSession != null) return new Result(directSession, jar.serialize());
            rejectedTokens.add(directToken);
        }
        Exception lastError = null;
        if (jar.hasRequiredAuthCookies()) {
            try {
                status.accept("Authenticating with Microsoft cookies...");
                String msToken = acquireMicrosoftAccessToken(jar);
                if (msToken != null) return finishMicrosoftLogin(jar, msToken, status);
            } catch (IOException | CompletionException e) {
                CookieHttpClient.checkInterrupted();
                lastError = e;
            }
        }
        try {
            status.accept("Trying minecraft.net cookie login...");
            String mcToken = MinecraftNetAuth.loginForMinecraftToken(jar, rejectedTokens);
            if (mcToken != null) {
                CookieHttpClient.checkInterrupted();
                status.accept("Fetching Minecraft profile...");
                return new Result(MicrosoftAuth.login(mcToken, DIRECT).join(), jar.serialize());
            }
        } catch (IOException | CompletionException e) {
            CookieHttpClient.checkInterrupted();
            lastError = e;
        }
        // Endpoint errors may contain response URLs/tokens. Expose a credential-free message.
        String detail = lastError instanceof IOException ? " " + lastError.getMessage() : "";
        throw new CompletionException("Cookie login failed. Sign in to Microsoft/Minecraft and export fresh cookies."
                + detail, null);
    }

    private static Session tryToken(String token) {
        if (token == null || token.isBlank()) return null;
        CookieHttpClient.checkInterrupted();
        try {
            return MicrosoftAuth.login(token, DIRECT).join();
        } catch (CompletionException e) {
            CookieHttpClient.checkInterrupted();
            return null;
        }
    }

    private static String acquireMicrosoftAccessToken(CookieJar jar) throws IOException {
        CookieHttpClient client = new CookieHttpClient(jar);
        IOException lastError = null;
        for (String url : OAUTH_URLS) {
            for (List<String> order : List.of(COOKIE_ORDER_JSHP, COOKIE_ORDER_JSH)) {
                CookieHttpClient.checkInterrupted();
                try {
                    String token = client.followOAuthRedirects(url, 12, order);
                    if (token != null) return token;
                } catch (IOException e) {
                    lastError = e;
                }
            }
        }
        if (lastError != null) throw lastError;
        return null;
    }

    private static Result finishMicrosoftLogin(CookieJar jar, String msToken, Consumer<String> status) throws IOException {
        status.accept("Acquiring Xbox access token...");
        String xbox = acquireXboxToken(msToken);
        CookieHttpClient.checkInterrupted();
        status.accept("Acquiring Xbox XSTS token...");
        var xsts = MicrosoftAuth.acquireXboxXstsToken(xbox, DIRECT).join();
        CookieHttpClient.checkInterrupted();
        status.accept("Acquiring Minecraft access token...");
        String mcToken = MicrosoftAuth.acquireMCAccessToken(xsts.get("Token"), xsts.get("uhs"), DIRECT).join();
        CookieHttpClient.checkInterrupted();
        status.accept("Fetching Minecraft profile...");
        return new Result(MicrosoftAuth.login(mcToken, DIRECT).join(), jar.serialize());
    }

    private static String acquireXboxToken(String msToken) throws IOException {
        for (String prefix : List.of("t=", "d=", "")) {
            CookieHttpClient.checkInterrupted();
            JsonObject properties = new JsonObject();
            properties.addProperty("AuthMethod", "RPS");
            properties.addProperty("SiteName", "user.auth.xboxlive.com");
            properties.addProperty("RpsTicket", prefix + msToken);
            JsonObject body = new JsonObject();
            body.add("Properties", properties);
            body.addProperty("RelyingParty", "http://auth.xboxlive.com");
            body.addProperty("TokenType", "JWT");
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://user.auth.xboxlive.com/user/authenticate"))
                    .timeout(CookieHttpClient.TIMEOUT).header("Content-Type", "application/json")
                    .header("X-Xbl-Contract-Version", "0")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
            try {
                HttpResponse<String> response = CookieHttpClient.CLIENT.send(request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() != 200) continue;
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                if (json.has("Token") && !json.get("Token").isJsonNull()) return json.get("Token").getAsString();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CancellationException("Cookie login cancelled.");
            } catch (RuntimeException e) {
                throw new IOException("Invalid Xbox Live authentication response.");
            }
        }
        throw new IOException("Xbox Live authentication rejected the cookies.");
    }

    public record Result(Session session, String cookies) {
    }
}
