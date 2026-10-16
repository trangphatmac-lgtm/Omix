package cn.omix.util.account.cookie;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;

final class MinecraftNetAuth {
    private static final String[] LOGIN_URLS = {
            "https://www.minecraft.net/msaproxy/login/signin?returnUrl=https%3A%2F%2Fwww.minecraft.net%2Fen-us%2Fprofile",
            "https://www.minecraft.net/en-us/login",
            "https://login.live.com/oauth20_authorize.srf?client_id=000000004C12AE8F&redirect_uri=https%3A%2F%2Fwww.minecraft.net%2Flogin&response_type=code&scope=XboxLive.Signin%20XboxLive.offline_access&prompt=none",
            "https://login.live.com/oauth20_authorize.srf?client_id=00000000402b5328&redirect_uri=https%3A%2F%2Flogin.live.com%2Foauth20_desktop.srf&response_type=code&scope=service%3A%3Auser.auth.xboxlive.com%3A%3AMBI_SSL&prompt=none",
            "https://www.minecraft.net/en-us/profile",
            "https://www.minecraft.net/en-us/msaprofile/mygames/editprofile"
    };

    private MinecraftNetAuth() {
    }

    static String loginForMinecraftToken(CookieJar jar, Set<String> rejectedTokens) throws IOException {
        CookieHttpClient client = new CookieHttpClient(jar);
        IOException lastError = null;
        for (String url : LOGIN_URLS) {
            CookieHttpClient.checkInterrupted();
            try {
                client.followRedirects(url, 20);
                String token = extractMinecraftAccessToken(jar, rejectedTokens);
                if (token != null) return token;
            } catch (IOException e) {
                lastError = e;
            }
        }
        if (lastError != null) throw lastError;
        return null;
    }

    static String extractMinecraftAccessToken(CookieJar jar) {
        return extractMinecraftAccessToken(jar, Set.of());
    }

    static String extractMinecraftAccessToken(CookieJar jar, Set<String> rejectedTokens) {
        String bearer = decode(jar.findMinecraftNetValue("bearer_token"));
        if (looksLikeJwt(bearer) && !rejectedTokens.contains(bearer)) return bearer;
        String access = decode(jar.findMinecraftNetValue("access_token"));
        if (looksLikeJwt(access) && !rejectedTokens.contains(access)) return access;
        if (access == null) return null;
        try {
            JsonObject root = JsonParser.parseString(access).getAsJsonObject();
            if (root.has("user") && root.get("user").isJsonObject()) {
                String token = token(root.getAsJsonObject("user"));
                if (token != null && !rejectedTokens.contains(token)) return token;
            }
            String token = token(root);
            return rejectedTokens.contains(token) ? null : token;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String token(JsonObject object) {
        JsonElement value = object.get("accessToken");
        if (value == null || value.isJsonNull()) return null;
        String token = value.getAsString();
        return looksLikeJwt(token) ? token : null;
    }

    private static String decode(String value) {
        if (value == null) return null;
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return value;
        }
    }

    private static boolean looksLikeJwt(String value) {
        return value != null && value.startsWith("eyJ") && value.split("\\.").length == 3;
    }
}
