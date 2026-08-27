package org.fisproxy.internal;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** HMAC v2 helpers matching the Python SDK and user-session API. */
public final class Hmac {
    public static final String CANONICAL_PREFIX = "FISPROXY-REQUEST-V2";
    public static final String EMPTY_BODY_SHA256 = sha256B64Url(new byte[0]);
    private static final Pattern CLIENT_ID_RE = Pattern.compile("^[A-Za-z0-9._~:-]{1,96}$");
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private Hmac() {
    }

    public static String b64url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    public static byte[] b64urlDecode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    public static String sha256B64Url(byte[] body) {
        try {
            return b64url(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    public static boolean validClientId(String value) {
        return value != null && CLIENT_ID_RE.matcher(value).matches();
    }

    public static String normalizeContentType(String value) {
        String text = value.strip().toLowerCase(Locale.ROOT);
        text = text.replaceAll("\\s*;\\s*", ";");
        return text.replaceAll("\\s*=\\s*", "=");
    }

    public static String canonicalRequest(
            String aud,
            String subject,
            String credentialId,
            String admissionId,
            String clientId,
            String sequence,
            String timestamp,
            String method,
            String target,
            String contentType,
            String contentLength,
            String idempotencyKey,
            String bodySha256) {
        return String.join("\n",
                CANONICAL_PREFIX,
                aud,
                subject,
                credentialId,
                admissionId,
                clientId,
                sequence,
                timestamp,
                method,
                target,
                normalizeContentType(contentType),
                contentLength,
                idempotencyKey,
                bodySha256);
    }

    public static String signRequest(String requestKey, String canonical) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(b64urlDecode(requestKey), "HmacSHA256"));
            return b64url(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("HMAC-SHA256 is required", exception);
        }
    }

    public static Map<String, Object> parseAdmissionPayload(String token) {
        if (token == null) {
            throw new IllegalArgumentException("invalid admission token");
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || !"a2".equals(parts[0]) || parts[1].isEmpty() || parts[2].isEmpty()) {
            throw new IllegalArgumentException("invalid admission token");
        }
        Object parsed;
        try {
            parsed = Json.parse(new String(b64urlDecode(parts[1]), StandardCharsets.UTF_8));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("invalid admission payload", exception);
        }
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("invalid admission payload");
        }
        Map<String, Object> payload = Json.asObject(map);
        if (!Long.valueOf(2L).equals(asLong(payload.get("version")))) {
            throw new IllegalArgumentException("unsupported admission version");
        }
        for (String key : List.of(
                "version", "aud", "admissionId", "subject", "credentialId", "clientId", "serverEpoch", "expiresAt")) {
            if (!payload.containsKey(key)) {
                throw new IllegalArgumentException("invalid admission payload");
            }
        }
        return payload;
    }

    public static String formatSequence(long value) {
        if (value < 0) {
            throw new ArithmeticException("admission sequence exhausted");
        }
        return Long.toString(value);
    }

    public static String quote(String value, String extraSafe) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        StringBuilder result = new StringBuilder(bytes.length);
        for (byte raw : bytes) {
            int character = raw & 0xff;
            if (isUnreserved(character) || extraSafe.indexOf(character) >= 0) {
                result.append((char) character);
            } else {
                result.append('%');
                result.append(HEX[character >>> 4]);
                result.append(HEX[character & 0x0f]);
            }
        }
        return result.toString();
    }

    public static String withQuery(String path, Map<String, ?> query) {
        if (query == null || query.isEmpty()) {
            return path;
        }
        StringBuilder encoded = new StringBuilder();
        for (Map.Entry<String, ?> entry : query.entrySet()) {
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            if (encoded.length() > 0) {
                encoded.append('&');
            }
            encoded.append(quote(entry.getKey(), "/"));
            encoded.append('=');
            encoded.append(quote(String.valueOf(value), "/"));
        }
        if (encoded.length() == 0) {
            return path;
        }
        return path + "?" + encoded;
    }

    public static Long asLong(Object value) {
        if (value instanceof Boolean) {
            return null;
        }
        if (value instanceof Long number) {
            return number;
        }
        if (value instanceof Integer number) {
            return number.longValue();
        }
        if (value instanceof Short number) {
            return number.longValue();
        }
        if (value instanceof Byte number) {
            return number.longValue();
        }
        if (value instanceof java.math.BigDecimal number) {
            try {
                return number.stripTrailingZeros().longValueExact();
            } catch (ArithmeticException exception) {
                return null;
            }
        }
        if (value instanceof Double number && number == Math.rint(number) && !number.isInfinite() && !number.isNaN()) {
            return number.longValue();
        }
        if (value instanceof Float number && number == Math.rint(number) && !number.isInfinite() && !number.isNaN()) {
            return number.longValue();
        }
        return null;
    }

    public static Map<String, Object> linked(Map<String, ?> source) {
        return source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
    }

    private static boolean isUnreserved(int character) {
        return (character >= 'A' && character <= 'Z')
                || (character >= 'a' && character <= 'z')
                || (character >= '0' && character <= '9')
                || character == '-'
                || character == '.'
                || character == '_'
                || character == '~';
    }
}

