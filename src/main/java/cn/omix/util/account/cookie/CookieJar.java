package cn.omix.util.account.cookie;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.Cookie;
import okhttp3.HttpUrl;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Browser-export parsing and a domain/path-aware cookie store for account login. */
public final class CookieJar {
    private static final List<String> AUTH_DOMAINS = List.of(
            "live.com", "microsoftonline.com", "microsoft.com", "xboxlive.com", "minecraft.net", "mojang.com");
    private final List<Cookie> cookies = new ArrayList<>();

    public static CookieJar parse(String content) {
        CookieJar jar = new CookieJar();
        if (content == null || content.isBlank()) {
            return jar;
        }
        String text = content.strip();
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1).strip();
        }
        // Do not reinterpret malformed/expired structured exports as a raw Cookie header.
        if (text.startsWith("[") || text.startsWith("{")) {
            jar.readJson(text);
        } else if (text.contains("\t") || text.startsWith("#")) {
            jar.readNetscape(text);
        } else {
            jar.readHeader(text);
        }
        return jar;
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public int size() {
        discardExpired();
        return cookies.size();
    }

    public String buildCookieHeader(URI uri) {
        return buildCookieHeader(uri, null);
    }

    public String buildCookieHeader(URI uri, List<String> preferredOrder) {
        if (!isAuthUri(uri)) {
            return "";
        }
        discardExpired();
        HttpUrl url = HttpUrl.get(uri);
        Comparator<Cookie> order = Comparator.comparingInt(cookie -> {
            int index = preferredOrder == null ? -1 : preferredOrder.indexOf(cookie.name());
            return index < 0 ? Integer.MAX_VALUE : index;
        });
        return cookies.stream().filter(cookie -> cookie.matches(url))
                .sorted(order.thenComparing(Comparator.comparingInt((Cookie cookie) -> cookie.path().length()).reversed()))
                .map(cookie -> cookie.name() + "=" + cookie.value()).collect(Collectors.joining("; "));
    }

    public void mergeResponseCookies(URI uri, List<String> headers) {
        if (!isAuthUri(uri)) {
            return;
        }
        HttpUrl url = HttpUrl.get(uri);
        for (String header : headers) {
            Cookie cookie = Cookie.parse(url, header);
            if (cookie != null) {
                put(cookie);
            }
        }
    }

    public boolean hasRequiredAuthCookies() {
        HttpUrl login = HttpUrl.get("https://login.live.com/oauth20_authorize.srf");
        discardExpired();
        return cookies.stream().anyMatch(cookie -> cookie.matches(login) && switch (cookie.name()) {
            case "__Host-MSAAUTH", "__Host-MSAAUTHP", "JSH", "JSHP" -> true;
            default -> false;
        });
    }

    public String findMinecraftNetValue(String name) {
        discardExpired();
        for (int i = cookies.size() - 1; i >= 0; i--) {
            Cookie cookie = cookies.get(i);
            if (name.equals(cookie.name()) && domainMatches(cookie.domain(), "minecraft.net")) {
                return cookie.value();
            }
        }
        return null;
    }

    public String serialize() {
        discardExpired();
        JsonObject root = new JsonObject();
        root.addProperty("v", 3);
        JsonArray array = new JsonArray();
        for (Cookie cookie : cookies) {
            JsonObject entry = new JsonObject();
            entry.addProperty("domain", cookie.domain());
            entry.addProperty("path", cookie.path());
            entry.addProperty("name", cookie.name());
            entry.addProperty("value", cookie.value());
            entry.addProperty("secure", cookie.secure());
            entry.addProperty("hostOnly", cookie.hostOnly());
            entry.addProperty("httpOnly", cookie.httpOnly());
            if (cookie.persistent()) {
                entry.addProperty("expirationDate", cookie.expiresAt() / 1000.0);
            }
            array.add(entry);
        }
        root.add("cookies", array);
        return root.toString();
    }

    public static CookieJar deserialize(String serialized) {
        return parse(serialized);
    }

    static boolean isAuthUri(URI uri) {
        return uri != null && "https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null
                && (uri.getPort() == -1 || uri.getPort() == 443) && isRelevantDomain(uri.getHost());
    }

    static boolean isRelevantDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return false;
        }
        String normalized = domain.toLowerCase(Locale.ROOT);
        if (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        String host = normalized;
        return AUTH_DOMAINS.stream().anyMatch(root -> domainMatches(host, root));
    }

    private static boolean domainMatches(String host, String domain) {
        return host.equals(domain) || host.endsWith("." + domain);
    }

    private void put(Cookie cookie) {
        if (!isRelevantDomain(cookie.domain())) {
            return;
        }
        cookies.removeIf(old -> old.name().equals(cookie.name()) && old.domain().equals(cookie.domain())
                && old.path().equals(cookie.path()));
        if (!cookie.value().isEmpty() && !"Disabled".equalsIgnoreCase(cookie.value())
                && cookie.expiresAt() > System.currentTimeMillis()) {
            cookies.add(cookie);
        }
    }

    private void discardExpired() {
        long now = System.currentTimeMillis();
        cookies.removeIf(cookie -> cookie.expiresAt() <= now);
    }

    private void readJson(String text) {
        JsonArray array;
        boolean legacy = false;
        try {
            JsonElement root = JsonParser.parseString(text);
            array = root.isJsonArray() ? root.getAsJsonArray() : root.getAsJsonObject().getAsJsonArray("cookies");
            legacy = root.isJsonObject() && root.getAsJsonObject().has("v")
                    && root.getAsJsonObject().get("v").getAsInt() == 2;
        } catch (RuntimeException ignored) {
            return;
        }
        if (array == null) {
            return;
        }
        for (JsonElement element : array) {
            try {
                JsonObject entry = element.getAsJsonObject();
                String domain = string(entry, "domain", string(entry, "host", "login.live.com"));
                boolean hostOnly = bool(entry, "hostOnly", !domain.startsWith("."));
                // v2 reference exports normalized all explicit domains as domain cookies.
                if (legacy && !entry.has("hostOnly")) {
                    hostOnly = false;
                }
                double expires = entry.has("expirationDate") && !entry.get("expirationDate").isJsonNull()
                        ? entry.get("expirationDate").getAsDouble() : 0;
                add(domain, string(entry, "path", "/"), string(entry, "name", ""), string(entry, "value", ""),
                        bool(entry, "secure", true), hostOnly, bool(entry, "httpOnly", false), expires);
            } catch (RuntimeException ignored) {
                // A malformed row should not discard the other valid cookies in the export.
            }
        }
    }

    private void readNetscape(String text) {
        for (String line : text.split("\\R")) {
            line = line.strip();
            boolean httpOnly = line.startsWith("#HttpOnly_");
            if (httpOnly) {
                line = line.substring("#HttpOnly_".length());
            } else if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\t", 7);
            if (parts.length != 7) {
                continue;
            }
            try {
                add(parts[0], parts[2], parts[5], parts[6], Boolean.parseBoolean(parts[3]),
                        !Boolean.parseBoolean(parts[1]), httpOnly, Double.parseDouble(parts[4]));
            } catch (RuntimeException ignored) {
                // Skip malformed export rows.
            }
        }
    }

    private void readHeader(String text) {
        if (text.regionMatches(true, 0, "Cookie:", 0, 7)) {
            text = text.substring(7).strip();
        }
        for (String segment : text.split("[;\\r\\n]+")) {
            int equals = segment.indexOf('=');
            if (equals > 0) {
                // Raw headers carry no domain. Scope them to the Microsoft login host.
                add("login.live.com", "/", segment.substring(0, equals), segment.substring(equals + 1),
                        true, true, false, 0);
            }
        }
    }

    private void add(String domain, String path, String name, String value, boolean secure,
                     boolean hostOnly, boolean httpOnly, double expirationSeconds) {
        if (domain == null || domain.isBlank()) {
            domain = "login.live.com";
            hostOnly = true;
        }
        if (!isRelevantDomain(domain) || !Double.isFinite(expirationSeconds)) {
            return;
        }
        try {
            String host = domain.startsWith(".") ? domain.substring(1) : domain;
            Cookie.Builder builder = new Cookie.Builder().name(name.trim()).value(value.trim())
                    .path(path.startsWith("/") ? path : "/");
            if (hostOnly) {
                builder.hostOnlyDomain(host);
            } else {
                builder.domain(host);
            }
            if (secure) builder.secure();
            if (httpOnly) builder.httpOnly();
            if (expirationSeconds != 0) builder.expiresAt((long) (expirationSeconds * 1000));
            put(builder.build());
        } catch (IllegalArgumentException ignored) {
            // Invalid names, values and domains cannot enter an HTTP request header.
        }
    }

    private static String string(JsonObject object, String key, String fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsString();
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsBoolean();
    }
}
