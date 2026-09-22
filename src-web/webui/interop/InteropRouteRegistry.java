package im.webui.interop;

import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class InteropRouteRegistry {
    private final Map<RouteKey, Function<InteropRequest, InteropResponse>> routes = new ConcurrentHashMap<>();

    public void register(
            String method,
            String path,
            Function<InteropRequest, InteropResponse> handler
    ) {
        RouteKey key = new RouteKey(method.toUpperCase(), normalize(path));
        if (routes.putIfAbsent(key, handler) != null) {
            throw new IllegalArgumentException("Interop route already registered: " + method + " " + path);
        }
    }

    public void get(String path, Function<InteropRequest, InteropResponse> handler) {
        register("GET", path, handler);
    }

    public void post(String path, Function<InteropRequest, InteropResponse> handler) {
        register("POST", path, handler);
    }

    public void put(String path, Function<InteropRequest, InteropResponse> handler) {
        register("PUT", path, handler);
    }

    public void delete(String path, Function<InteropRequest, InteropResponse> handler) {
        register("DELETE", path, handler);
    }

    public InteropResponse dispatch(InteropRequest request) {
        Function<InteropRequest, InteropResponse> handler = routes.get(
                new RouteKey(request.method().toUpperCase(), normalize(request.path()))
        );
        return handler == null
                ? InteropResponse.text(HttpResponseStatus.NOT_FOUND, "No route")
                : handler.apply(request);
    }

    public Map<String, String> describeRoutes() {
        return routes.keySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                key -> key.method + " " + key.path,
                ignored -> "registered"
        ));
    }

    private static String normalize(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String normalized = path.startsWith("/") ? path : "/" + path;
        return normalized.length() > 1 && normalized.endsWith("/")
                ? normalized.substring(0, normalized.length() - 1)
                : normalized;
    }

    private record RouteKey(String method, String path) {
    }
}
