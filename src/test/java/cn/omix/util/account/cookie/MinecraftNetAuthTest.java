package cn.omix.util.account.cookie;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MinecraftNetAuthTest {
    private static final String TOKEN = "eyJhbGciOiJub25lIn0.sample.signature";

    @Test void extractsBearerAndRawAccessTokens() {
        for (String name : List.of("bearer_token", "access_token")) {
            CookieJar jar = cookie(".minecraft.net", name, TOKEN);
            assertEquals(TOKEN, MinecraftNetAuth.extractMinecraftAccessToken(jar));
            assertDoesNotThrow(() -> CookieAuth.verify(jar));
        }
    }

    @Test void extractsEncodedNestedAndTopLevelAccessTokens() {
        for (String json : List.of("{\"user\":{\"accessToken\":\"" + TOKEN + "\"}}",
                "{\"accessToken\":\"" + TOKEN + "\"}")) {
            CookieJar jar = cookie("www.minecraft.net", "access_token", URLEncoder.encode(json, StandardCharsets.UTF_8));
            assertEquals(TOKEN, MinecraftNetAuth.extractMinecraftAccessToken(jar));
        }
    }

    @Test void ignoresLookalikeDomainsAndMalformedTokenPayloads() {
        assertNull(MinecraftNetAuth.extractMinecraftAccessToken(cookie("minecraft.net.evil.test", "bearer_token", TOKEN)));
        for (String value : List.of("%broken", "null", "{\"accessToken\":null}", "eyJ.only-two")) {
            assertNull(MinecraftNetAuth.extractMinecraftAccessToken(cookie(".minecraft.net", "access_token", value)));
        }
    }

    @Test void rejectedBearerDoesNotHideNewAccessTokenOrEndFallbackEarly() {
        CookieJar jar = cookie(".minecraft.net", "bearer_token", TOKEN);
        String renewed = "eyJhbGciOiJub25lIn0.renewed.signature";
        jar.mergeResponseCookies(java.net.URI.create("https://www.minecraft.net/"),
                List.of("access_token=" + renewed + "; Path=/; Secure"));
        assertEquals(renewed, MinecraftNetAuth.extractMinecraftAccessToken(jar, Set.of(TOKEN)));
        assertNull(MinecraftNetAuth.extractMinecraftAccessToken(jar, Set.of(TOKEN, renewed)));
    }

    @Test void rejectsEmptyOrUnrelatedCookiesBeforeNetworking() {
        assertThrows(IllegalArgumentException.class, () -> CookieAuth.verify(CookieJar.parse("")));
        assertThrows(IllegalArgumentException.class, () -> CookieAuth.verify(CookieJar.parse("analytics=sample")));
        assertDoesNotThrow(() -> CookieAuth.verify(CookieJar.parse("__Host-MSAAUTHP=sample")));
    }

    private static CookieJar cookie(String domain, String name, String value) {
        JsonObject entry = new JsonObject();
        entry.addProperty("domain", domain);
        entry.addProperty("name", name);
        entry.addProperty("value", value);
        JsonArray array = new JsonArray();
        array.add(entry);
        return CookieJar.parse(array.toString());
    }
}
