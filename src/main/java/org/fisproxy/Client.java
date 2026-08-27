package org.fisproxy;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.fisproxy.internal.Hmac;
import org.fisproxy.internal.Json;

/**
 * User-token client for start / status / change-ip / stop.
 *
 * <p>Create the token on the signed-in API page. Pass it as Bearer; this client
 * does not implement a browser login flow.
 */
public class Client implements AutoCloseable {
    public static final String DEFAULT_BASE_URL = "https://api.fisproxy.org";
    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final int ADMISSION_REFRESH_SKEW_SECONDS = 5;
    private static final Set<String> REFRESHABLE_ADMISSION_CODES = Set.of(
            "ADMISSION_EXPIRED",
            "ADMISSION_PROCESS_EXPIRED",
            "ADMISSION_INVALID",
            "EDGE_ADMISSION_EXPIRED",
            "REQUEST_TIMESTAMP_INVALID");
    private static final Set<String> IDENTITY_TERMINATION_CODES = Set.of(
            "USER_BANNED",
            "ACCOUNT_PERMANENTLY_BANNED",
            "SESSION_EXPIRED",
            "SESSION_REVOKED",
            "TOKEN_REVOKED");
    private static final Set<String> UNSIGNED_PATHS = Set.of("/api/v1/auth/admission");

    private final String token;
    private final String baseUrl;
    private final String clientId;
    private final Duration timeout;
    private final String userAgent;
    private final Transport transport;
    private final HttpClient httpClient;
    private final Object lock = new Object();
    private Admission admission;

    public Client(String token) {
        this(token, ClientOptions.defaults());
    }

    public Client(String token, ClientOptions options) {
        if (options == null) {
            options = ClientOptions.defaults();
        }
        String cleaned = token == null ? "" : token.strip();
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("token is required");
        }
        String generated = (options.clientId() == null || options.clientId().isBlank())
                ? UUID.randomUUID().toString().replace("-", "")
                : options.clientId();
        if (!Hmac.validClientId(generated)) {
            throw new IllegalArgumentException("client_id must match [A-Za-z0-9._~:-]{1,96}");
        }
        String base = options.baseUrl() == null || options.baseUrl().isBlank()
                ? DEFAULT_BASE_URL
                : options.baseUrl();
        this.token = cleaned;
        this.baseUrl = stripTrailingSlash(base);
        this.clientId = generated;
        this.timeout = options.timeout() == null ? DEFAULT_TIMEOUT : options.timeout();
        this.userAgent = options.userAgent() == null || options.userAgent().isBlank()
                ? "fisproxy-java/" + Version.VERSION
                : options.userAgent();
        this.transport = options.transport();
        this.httpClient = this.transport == null
                ? HttpClient.newBuilder().connectTimeout(this.timeout).followRedirects(HttpClient.Redirect.NEVER).build()
                : null;
    }

    public static Client fromEnv() {
        return fromEnv(System.getenv(), ClientOptions.defaults());
    }

    public static Client fromEnv(ClientOptions options) {
        return fromEnv(System.getenv(), options);
    }

    static Client fromEnv(Map<String, String> env, ClientOptions options) {
        if (options == null) {
            options = ClientOptions.defaults();
        }
        String token = trimToNull(env == null ? null : env.get("FISPROXY_API_TOKEN"));
        if (token == null) {
            throw new IllegalArgumentException("FISPROXY_API_TOKEN is required");
        }
        String base = firstNonBlank(options.baseUrl(), env == null ? null : env.get("FISPROXY_API_BASE"), DEFAULT_BASE_URL);
        String clientId = firstNonBlank(options.clientId(), env == null ? null : env.get("FISPROXY_CLIENT_ID"), null);
        return new Client(token, ClientOptions.builder()
                .baseUrl(base)
                .clientId(clientId)
                .timeout(options.timeout())
                .userAgent(options.userAgent())
                .transport(options.transport())
                .build());
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String clientId() {
        return clientId;
    }

    @Override
    public void close() {
        synchronized (lock) {
            admission = null;
        }
    }

    public UserProfile me() {
        return UserProfile.fromResponse(request("GET", "/api/v1/me"));
    }

    public List<Map<String, Object>> services() {
        Map<String, Object> payload = request("GET", "/api/v1/me/services");
        Object items = payload.get("services");
        if (!(items instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add(Json.freeze(Json.asObject(map)));
            }
        }
        return List.copyOf(result);
    }

    public SessionStatus status() {
        return SessionStatus.fromResponse(request("GET", "/api/v1/sessions/status"));
    }

    public List<Map<String, Object>> entrances() {
        return entrances(null);
    }

    public List<Map<String, Object>> entrances(String serviceId) {
        Map<String, Object> query = new LinkedHashMap<>();
        if (serviceId != null) {
            query.put("serviceId", serviceId);
        }
        Map<String, Object> payload = request("GET", "/api/v1/me/entrances", RequestOptions.builder().query(query).build());
        Object items = payload.get("entrances");
        if (!(items instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add(Json.freeze(Json.asObject(map)));
            }
        }
        return List.copyOf(result);
    }

    public Operation start() {
        return start(StartOptions.defaults());
    }

    public Operation start(StartOptions options) {
        if (options == null) {
            options = StartOptions.defaults();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        if (options.serviceId() != null) {
            body.put("serviceId", options.serviceId());
        }
        if (options.target() != null) {
            body.put("target", options.target());
        }
        if (options.autoNfa() != null) {
            body.put("autoNfa", options.autoNfa());
        }
        if (options.nfaItemId() != null) {
            body.put("nfaItemId", options.nfaItemId());
        }
        if (options.reuseNfaItemId() != null) {
            body.put("reuseNfaItemId", options.reuseNfaItemId());
        }
        if (options.nfaSource() != null) {
            body.put("nfaSource", options.nfaSource());
        }
        if (options.nfaSku() != null) {
            body.put("nfaSku", options.nfaSku());
        }
        if (options.tryPreviousNfa() != null) {
            body.put("tryPreviousNfa", options.tryPreviousNfa());
        }
        String idempotency = options.idempotencyKey() == null || options.idempotencyKey().isBlank()
                ? newIdempotencyKey()
                : options.idempotencyKey();
        Operation operation = operationFromPayload(request("POST", "/api/v1/sessions/start", RequestOptions.builder()
                .jsonBody(body)
                .idempotencyKey(idempotency)
                .build()));
        if (options.shouldWait()) {
            return waitOperation(operation.id(), WaitOptions.builder()
                    .timeoutSeconds(options.timeoutSeconds())
                    .intervalSeconds(options.intervalSeconds())
                    .build());
        }
        return operation;
    }

    public Operation changeIp() {
        return changeIp(ChangeIpOptions.defaults());
    }

    public Operation changeIp(ChangeIpOptions options) {
        if (options == null) {
            options = ChangeIpOptions.defaults();
        }
        String idempotency = options.idempotencyKey() == null || options.idempotencyKey().isBlank()
                ? newIdempotencyKey()
                : options.idempotencyKey();
        Operation operation = operationFromPayload(request("POST", "/api/v1/sessions/change-ip", RequestOptions.builder()
                .jsonBody(Map.of())
                .idempotencyKey(idempotency)
                .build()));
        if (!options.shouldWait()) {
            return operation;
        }
        Operation finished = waitOperation(operation.id(), WaitOptions.builder()
                .timeoutSeconds(options.timeoutSeconds())
                .intervalSeconds(options.intervalSeconds())
                .build());
        if (options.waitRouteAck() && finished.succeeded() && !finished.routeAcked()) {
            return waitOperation(operation.id(), WaitOptions.builder()
                    .timeoutSeconds(options.timeoutSeconds())
                    .intervalSeconds(options.intervalSeconds())
                    .until(item -> item.terminal() && (item.routeAcked() || !item.succeeded()))
                    .build());
        }
        return finished;
    }

    public StopResult stop() {
        return StopResult.fromResponse(request("POST", "/api/v1/sessions/stop", RequestOptions.builder()
                .jsonBody(Map.of())
                .build()));
    }

    public Operation getOperation(String operationId) {
        Map<String, Object> payload = request("GET", "/api/v1/operations/" + Hmac.quote(operationId, ""));
        return operationFromPayload(payload);
    }

    public List<Operation> listOperations() {
        return listOperations(ListOperationsOptions.defaults());
    }

    public List<Operation> listOperations(ListOperationsOptions options) {
        if (options == null) {
            options = ListOperationsOptions.defaults();
        }
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("status", options.status());
        query.put("kind", options.kind());
        query.put("limit", options.limit() == null ? null : String.valueOf(options.limit()));
        Map<String, Object> payload = request("GET", "/api/v1/operations", RequestOptions.builder().query(query).build());
        Object items = payload.get("operations");
        if (!(items instanceof List<?> list)) {
            return List.of();
        }
        List<Operation> result = new ArrayList<>();
        for (Object item : list) {
            result.add(Operation.fromMapping(item));
        }
        return List.copyOf(result);
    }

    public Operation cancelOperation(String operationId) {
        Map<String, Object> payload = request(
                "POST",
                "/api/v1/operations/" + Hmac.quote(operationId, "") + "/cancel",
                RequestOptions.builder().jsonBody(Map.of()).build());
        return operationFromPayload(payload);
    }

    public Operation waitOperation(String operationId) {
        return waitOperation(operationId, WaitOptions.defaults());
    }

    public Operation waitOperation(String operationId, WaitOptions options) {
        if (options == null) {
            options = WaitOptions.defaults();
        }
        long deadline = System.nanoTime() + secondsToNanos(options.timeoutSeconds());
        Operation last = null;
        Predicate<Operation> predicate = options.until() == null ? Operation::terminal : options.until();
        while (System.nanoTime() < deadline) {
            last = getOperation(operationId);
            if (predicate.test(last)) {
                if ("failed".equals(last.status()) || "canceled".equals(last.status())) {
                    throw new OperationFailedException(last);
                }
                return last;
            }
            sleepSeconds(Math.max(options.intervalSeconds(), 0.05));
        }
        throw new OperationTimeoutException(operationId, last);
    }

    public Map<String, Object> request(String method, String path) {
        return request(method, path, RequestOptions.none());
    }

    public Map<String, Object> request(String method, String path, RequestOptions options) {
        if (options == null) {
            options = RequestOptions.none();
        }
        if (path == null || !path.startsWith("/") || path.startsWith("//") || path.contains("\\")) {
            throw new IllegalArgumentException("path must be an absolute URL path");
        }
        String target = Hmac.withQuery(path, options.query());
        String methodUpper = method.toUpperCase();
        byte[] body;
        String contentType;
        if (options.jsonBody() == null) {
            body = new byte[0];
            contentType = "";
        } else {
            body = Json.dumps(new LinkedHashMap<>(options.jsonBody()));
            contentType = "application/json";
        }
        String pathname = path.split("\\?", 2)[0];
        boolean shouldSign = options.sign() == null ? !UNSIGNED_PATHS.contains(pathname) : options.sign();
        String idempotency = options.idempotencyKey() == null ? "" : options.idempotencyKey();
        return send(methodUpper, target, body, contentType, idempotency, shouldSign, true);
    }

    private Map<String, Object> send(
            String method,
            String target,
            byte[] body,
            String contentType,
            String idempotencyKey,
            boolean sign,
            boolean allowRefresh) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        headers.put("Authorization", "Bearer " + token);
        headers.put("User-Agent", userAgent);
        headers.put("X-FP-Content-Length", Integer.toString(body.length));
        headers.put("X-FP-Content-SHA256", Hmac.sha256B64Url(body));
        if (!contentType.isEmpty()) {
            headers.put("Content-Type", contentType);
        }
        if (!idempotencyKey.isEmpty()) {
            headers.put("Idempotency-Key", idempotencyKey);
        }
        if (sign) {
            synchronized (lock) {
                Admission current = ensureAdmission();
                headers.putAll(signatureHeaders(current, method, target, body, contentType, idempotencyKey));
            }
        }
        RawResponse response = doTransport(method, baseUrl + target, headers, body.length == 0 ? null : body);
        Object payload = decodePayload(response);
        if (sign && allowRefresh && isRefreshableAdmissionFailure(response.status(), payload)) {
            synchronized (lock) {
                admission = null;
            }
            return send(method, target, body, contentType, idempotencyKey, sign, false);
        }
        if (response.status() >= 400 || isErrorPayload(payload)) {
            throw errorFromResponse(response.status(), payload);
        }
        if (!(payload instanceof Map<?, ?>)) {
            throw new FisProxyException(response.status(), Map.of(
                    "errorCode", "INVALID_RESPONSE",
                    "message", "expected a JSON object"));
        }
        return Json.asObject(payload);
    }

    private Admission ensureAdmission() {
        long now = System.currentTimeMillis() / 1000L;
        Admission current = admission;
        if (current != null && current.expiresAt > now + ADMISSION_REFRESH_SKEW_SECONDS) {
            return current;
        }
        return exchangeAdmission();
    }

    private Admission exchangeAdmission() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("clientId", clientId);
        Map<String, Object> payload = send(
                "POST",
                "/api/v1/auth/admission",
                Json.dumps(body),
                "application/json",
                "",
                false,
                false);
        Object admissionToken = payload.get("admission");
        Object requestKey = payload.get("requestKey");
        Object subject = payload.get("subject");
        Object admissionId = payload.get("admissionId");
        Long expiresAt = Hmac.asLong(payload.get("expiresAt"));
        Long serverTime = Hmac.asLong(payload.get("serverTime"));
        Object serverEpoch = payload.get("serverEpoch");
        if (!Boolean.TRUE.equals(payload.get("ok"))
                || !(admissionToken instanceof String admissionText)
                || !(requestKey instanceof String requestKeyText)
                || !(subject instanceof String subjectText)
                || !(admissionId instanceof String admissionIdText)
                || expiresAt == null
                || serverTime == null
                || !(serverEpoch instanceof String serverEpochText)) {
            throw new AdmissionException(500, Map.of(
                    "errorCode", "INVALID_ADMISSION",
                    "message", "invalid admission exchange response"));
        }
        Map<String, Object> parsed;
        try {
            parsed = Hmac.parseAdmissionPayload(admissionText);
        } catch (IllegalArgumentException exception) {
            throw new AdmissionException(500, Map.of(
                    "errorCode", "INVALID_ADMISSION",
                    "message", "invalid admission exchange response"));
        }
        if (!subjectText.equals(parsed.get("subject"))
                || !admissionIdText.equals(parsed.get("admissionId"))
                || !clientId.equals(parsed.get("clientId"))
                || !expiresAt.equals(Hmac.asLong(parsed.get("expiresAt")))) {
            throw new AdmissionException(500, Map.of(
                    "errorCode", "INVALID_ADMISSION",
                    "message", "admission payload mismatch"));
        }
        Admission credential = new Admission(
                admissionText,
                requestKeyText,
                subjectText,
                admissionIdText,
                expiresAt,
                serverTime,
                serverEpochText,
                parsed,
                serverTime - System.currentTimeMillis(),
                0L);
        this.admission = credential;
        return credential;
    }

    private Map<String, String> signatureHeaders(
            Admission current,
            String method,
            String target,
            byte[] body,
            String contentType,
            String idempotencyKey) {
        String sequence = Hmac.formatSequence(current.sequence);
        current.sequence += 1;
        String timestamp = Long.toString(Math.round((double) (System.currentTimeMillis() + current.clockOffsetMs)));
        if (timestamp.length() != 13) {
            throw new AdmissionException(400, Map.of(
                    "errorCode", "REQUEST_TIMESTAMP_INVALID",
                    "message", "signed timestamp must be 13-digit unix milliseconds"));
        }
        String contentLength = Integer.toString(body.length);
        String bodySha256 = Hmac.sha256B64Url(body);
        String canonical = Hmac.canonicalRequest(
                String.valueOf(current.payload.get("aud")),
                String.valueOf(current.payload.get("subject")),
                String.valueOf(current.payload.get("credentialId")),
                String.valueOf(current.payload.get("admissionId")),
                String.valueOf(current.payload.get("clientId")),
                sequence,
                timestamp,
                method,
                target,
                contentType,
                contentLength,
                idempotencyKey,
                bodySha256);
        String signature = Hmac.signRequest(current.requestKey, canonical);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-FP-Admission", current.admission);
        headers.put("X-FP-Subject", current.subject);
        headers.put("X-FP-Timestamp", timestamp);
        headers.put("X-FP-Client", String.valueOf(current.payload.get("clientId")));
        headers.put("X-FP-Sequence", sequence);
        headers.put("X-FP-Content-Length", contentLength);
        headers.put("X-FP-Content-SHA256", bodySha256);
        headers.put("X-FP-Signature", signature);
        return headers;
    }

    private RawResponse doTransport(String method, String url, Map<String, String> headers, byte[] body) {
        if (transport != null) {
            return transport.exchange(method, url, headers, body);
        }
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(timeout);
            HttpRequest.BodyPublisher publisher = (body == null || body.length == 0)
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofByteArray(body);
            builder.method(method, publisher);
            for (Map.Entry<String, String> header : headers.entrySet()) {
                builder.header(header.getKey(), header.getValue());
            }
            HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            Map<String, String> responseHeaders = new LinkedHashMap<>();
            response.headers().map().forEach((name, values) -> {
                if (!values.isEmpty()) {
                    responseHeaders.put(name, values.get(0));
                }
            });
            return new RawResponse(response.statusCode(), responseHeaders, response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TransportException(0, Map.of(
                    "errorCode", "TRANSPORT_ERROR",
                    "message", "interrupted"));
        } catch (IOException exception) {
            String message = exception.getMessage() == null ? exception.toString() : exception.getMessage();
            throw new TransportException(0, Map.of(
                    "errorCode", "TRANSPORT_ERROR",
                    "message", message));
        }
    }

    private static Object decodePayload(RawResponse response) {
        byte[] body = response.bodyRaw();
        if (body.length == 0) {
            return Map.of();
        }
        String text = new String(body, StandardCharsets.UTF_8);
        try {
            return Json.parse(text);
        } catch (RuntimeException exception) {
            String snippet = text.length() > 300 ? text.substring(0, 300) : text;
            return Map.of(
                    "errorCode", "INVALID_RESPONSE",
                    "message", snippet.isEmpty() ? "HTTP " + response.status() : snippet);
        }
    }

    private static boolean isErrorPayload(Object payload) {
        if (!(payload instanceof Map<?, ?> map)) {
            return false;
        }
        if (Boolean.TRUE.equals(map.get("ok"))) {
            return false;
        }
        return Boolean.FALSE.equals(map.get("ok")) || Boolean.FALSE.equals(map.get("success"));
    }

    private static boolean isRefreshableAdmissionFailure(int status, Object payload) {
        if (status != 401 && status != 403) {
            return false;
        }
        return REFRESHABLE_ADMISSION_CODES.contains(FisProxyException.errorCodeOf(payload));
    }

    private static Operation operationFromPayload(Map<String, Object> payload) {
        Object operation = payload.get("operation");
        return Operation.fromMapping(operation != null ? operation : payload);
    }

    private static FisProxyException errorFromResponse(int status, Object payload) {
        Map<String, Object> data = payload instanceof Map<?, ?> map
                ? Json.asObject(map)
                : Map.of("message", String.valueOf(payload));
        String code = FisProxyException.errorCodeOf(data);
        if (IDENTITY_TERMINATION_CODES.contains(code) || (status == 401 && (code.isEmpty() || "UNAUTHORIZED".equals(code)))) {
            return new AuthException(status, data);
        }
        if (REFRESHABLE_ADMISSION_CODES.contains(code) || code.startsWith("ADMISSION_") || code.startsWith("REQUEST_")) {
            return new AdmissionException(status, data);
        }
        if (status == 409 || "OPERATION_CONFLICT".equals(code)) {
            return new ConflictException(status, data);
        }
        return new FisProxyException(status, data, FisProxyException.errorMessageOf(data, "HTTP " + status));
    }

    private static String newIdempotencyKey() {
        return UUID.randomUUID().toString();
    }

    private static String stripTrailingSlash(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    private static String firstNonBlank(String first, String second, String fallback) {
        String a = trimToNull(first);
        if (a != null) {
            return a;
        }
        String b = trimToNull(second);
        if (b != null) {
            return b;
        }
        return fallback;
    }

    private static long secondsToNanos(double seconds) {
        return (long) (seconds * 1_000_000_000L);
    }

    private static void sleepSeconds(double seconds) {
        try {
            long millis = Math.max(1L, Math.round(seconds * 1000.0));
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TransportException(0, Map.of(
                    "errorCode", "TRANSPORT_ERROR",
                    "message", "interrupted"));
        }
    }

    private static final class Admission {
        private final String admission;
        private final String requestKey;
        private final String subject;
        private final String admissionId;
        private final long expiresAt;
        private final long serverTime;
        private final String serverEpoch;
        private final Map<String, Object> payload;
        private final long clockOffsetMs;
        private long sequence;

        private Admission(
                String admission,
                String requestKey,
                String subject,
                String admissionId,
                long expiresAt,
                long serverTime,
                String serverEpoch,
                Map<String, Object> payload,
                long clockOffsetMs,
                long sequence) {
            this.admission = admission;
            this.requestKey = requestKey;
            this.subject = subject;
            this.admissionId = admissionId;
            this.expiresAt = expiresAt;
            this.serverTime = serverTime;
            this.serverEpoch = serverEpoch;
            this.payload = payload;
            this.clockOffsetMs = clockOffsetMs;
            this.sequence = sequence;
        }
    }
}

