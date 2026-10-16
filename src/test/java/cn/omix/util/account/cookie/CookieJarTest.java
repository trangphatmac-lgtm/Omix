package cn.omix.util.account.cookie;

import cn.omix.security.SafeStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CookieJarTest {
    private static final URI LOGIN = URI.create("https://login.live.com/oauth20_authorize.srf");
    @TempDir Path temporary;

    @Test void importsHeaderPrefixNewlinesAndEqualsInValuesWithoutLeakingToOtherHosts() {
        CookieJar jar = CookieJar.parse("Cookie: JSH=one==; JSHP=two\r\n__Host-MSAAUTH=three");
        assertEquals(3, jar.size());
        assertTrue(jar.hasRequiredAuthCookies());
        assertEquals("JSH=one==; JSHP=two; __Host-MSAAUTH=three", jar.buildCookieHeader(LOGIN));
        assertEquals("JSHP=two; JSH=one==; __Host-MSAAUTH=three",
                jar.buildCookieHeader(LOGIN, List.of("JSHP", "JSH")));
        assertEquals("", jar.buildCookieHeader(URI.create("https://www.minecraft.net/")));
        assertEquals("", jar.buildCookieHeader(URI.create("https://sub.login.live.com/")));
        assertEquals("", jar.buildCookieHeader(URI.create("http://login.live.com/")));
    }

    @Test void importsNetscapeHttpOnlyRowsAndSkipsExpiredDisabledAndUnrelatedCookies() throws Exception {
        Path file = temporary.resolve("cookies.txt");
        Files.writeString(file, "\uFEFF# Netscape HTTP Cookie File\n"
                + "#HttpOnly_.live.com\tTRUE\t/\tTRUE\t0\t__Host-MSAAUTH\tauth\n"
                + "login.live.com\tFALSE\t/\tTRUE\t0\tJSH\tone\n"
                + ".live.com\tTRUE\t/\tTRUE\t1\tJSHP\texpired\n"
                + ".live.com\tTRUE\t/\tTRUE\t0\tDisabledCookie\tDisabled\n"
                + ".example.com\tTRUE\t/\tTRUE\t0\tother\tsecret\n"
                + "invalid\trow\n");
        CookieJar jar = CookieAuth.parseFile(file.toFile());
        assertEquals(2, jar.size());
        assertEquals("__Host-MSAAUTH=auth; JSH=one", jar.buildCookieHeader(LOGIN));
        assertEquals("__Host-MSAAUTH=auth", jar.buildCookieHeader(URI.create("https://account.live.com/")));
    }

    @Test void importsJsonArrayOrWrapperAndIsolatesMalformedRows() {
        String rows = """
                [null, {"name":"broken","value":{}},
                 {"host":".live.com","name":"JSH","value":"valid","expirationDate":4102444800},
                 {"domain":".live.com","name":"JSHP","value":"expired","expirationDate":1},
                 {"domain":"login.live.com.evil.test","name":"private","value":"secret"},
                 {"domain":"notlive.com","name":"private","value":"secret"}]
                """;
        for (String input : List.of(rows, "{\"cookies\":" + rows + "}")) {
            CookieJar jar = CookieJar.parse(input);
            assertEquals(1, jar.size());
            assertEquals("JSH=valid", jar.buildCookieHeader(LOGIN));
        }
    }

    @Test void structuredExportsNeverFallBackToHeaderParsing() {
        for (String invalid : List.of("{broken=secret}", "[JSH=secret", "{\"cookies\":null}",
                "# Netscape HTTP Cookie File\n.live.com\tTRUE\t/\tTRUE\t1\tJSH\tsecret=1")) {
            assertTrue(CookieJar.parse(invalid).isEmpty(), invalid);
        }
    }

    @Test void matchesPathsAtBoundariesAndPreservesSameNameOnDifferentPaths() {
        CookieJar jar = CookieJar.parse("""
                [{"domain":".live.com","path":"/","name":"JSH","value":"root"},
                 {"domain":".live.com","path":"/account","name":"JSH","value":"account"}]
                """);
        assertEquals("JSH=root", jar.buildCookieHeader(URI.create("https://login.live.com/accounting")));
        assertEquals("JSH=account; JSH=root", jar.buildCookieHeader(URI.create("https://login.live.com/account/settings")));
    }

    @Test void responseCookiesRotateDeleteExpireAndRejectUnrelatedDomainAttributes() {
        CookieJar jar = CookieJar.parse("JSH=old; JSHP=remove");
        jar.mergeResponseCookies(LOGIN, List.of("JSH=new; Path=/; Secure; HttpOnly",
                "JSHP=deleted; Path=/; Max-Age=0", "expired=old; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/",
                "foreign=secret; Domain=minecraft.net; Path=/", "other=bad; Domain=evil.test; Path=/"));
        assertEquals(1, jar.size());
        assertEquals("JSH=new", jar.buildCookieHeader(LOGIN));
        assertEquals("", jar.buildCookieHeader(URI.create("https://sub.login.live.com/")));
        assertEquals("", jar.buildCookieHeader(URI.create("https://www.minecraft.net/")));
    }

    @Test void serializationRoundTripsThroughExistingAccountCredentialStorage() {
        CookieJar jar = CookieJar.parse("""
                [{"domain":".live.com","path":"/","name":"JSH","value":"sample-secret","secure":true,
                  "hostOnly":false,"httpOnly":true,"expirationDate":4102444800},
                 {"domain":"login.live.com","path":"/account","name":"local","value":"host","hostOnly":true}]
                """);
        String stored = SafeStorage.encrypt(jar.serialize());
        assertFalse(stored.contains("sample-secret"));
        CookieJar restored = CookieJar.deserialize(SafeStorage.decrypt(stored));
        assertEquals(jar.serialize(), restored.serialize());
        assertEquals(jar.buildCookieHeader(LOGIN), restored.buildCookieHeader(LOGIN));
        assertEquals("JSH=sample-secret", restored.buildCookieHeader(URI.create("https://account.live.com/account")));
    }

    @Test void readsReferenceVersionTwoWithDomainlessCookiesScopedToMicrosoft() {
        CookieJar jar = CookieJar.deserialize("""
                {"v":2,"cookies":[
                  {"domain":".live.com","path":"/","name":"JSH","value":"domain","secure":true},
                  {"domain":"","path":"/","name":"JSHP","value":"header","secure":true}]}
                """);
        assertEquals("JSH=domain; JSHP=header", jar.buildCookieHeader(LOGIN));
        assertEquals("JSH=domain", jar.buildCookieHeader(URI.create("https://account.live.com/")));
        assertEquals("", jar.buildCookieHeader(URI.create("https://www.minecraft.net/")));
    }

    @Test void authCookieNamesMustActuallyMatchMicrosoftLogin() {
        CookieJar jar = CookieJar.parse("""
                [{"domain":"minecraft.net","name":"JSH","value":"wrong-host"},
                 {"domain":"login.live.com","path":"/unrelated","name":"JSHP","value":"wrong-path"}]
                """);
        assertFalse(jar.hasRequiredAuthCookies());
        assertThrows(IllegalArgumentException.class, () -> CookieAuth.verify(jar));
    }

    @Test void disallowsLookalikeHostsAndInsecureRedirects() {
        for (String uri : List.of("https://login.live.com.evil.test/", "https://notlive.com/",
                "http://login.live.com/", "https://login.live.com:8443/", "https://user@login.live.com/")) {
            assertFalse(CookieJar.isAuthUri(URI.create(uri)), uri);
        }
        assertTrue(CookieJar.isAuthUri(LOGIN));
        assertTrue(CookieJar.isAuthUri(URI.create("https://sisu.xboxlive.com/connect/oauth/XboxLive")));
    }
}
