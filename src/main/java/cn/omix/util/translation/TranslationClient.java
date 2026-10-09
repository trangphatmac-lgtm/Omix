package cn.omix.util.translation;

import cn.omix.util.ai.HarnessRuntime;
import com.google.gson.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Only connects to the endpoint discovered from the current Harness child. */
public final class TranslationClient {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final HarnessRuntime.TranslationEndpoint endpoint;
    public TranslationClient(HarnessRuntime.TranslationEndpoint endpoint) { this.endpoint = endpoint; }
    public CompletableFuture<Map<String, List<String>>> models() {
        return map(request("/v1/models", null), json -> {
            Map<String, List<String>> result = new LinkedHashMap<>();
            for (JsonElement element : json.getAsJsonArray("providers")) {
                JsonObject provider = element.getAsJsonObject(); var models = new ArrayList<String>();
                for (JsonElement model : provider.getAsJsonArray("models")) models.add(model.getAsJsonObject().get("id").getAsString());
                result.put(provider.get("id").getAsString(), List.copyOf(models));
            }
            return Collections.unmodifiableMap(result);
        });
    }
    public CompletableFuture<Map<String, String>> translate(TranslationQueue.Context context, List<TranslationQueue.Item> items) {
        JsonObject body = new JsonObject(); body.addProperty("provider", context.provider()); body.addProperty("model", context.model());
        body.addProperty("target", context.target()); body.add("items", new Gson().toJsonTree(items));
        return map(request("/v1/translate", body), json -> {
            Map<String, String> result = new HashMap<>();
            for (JsonElement element : json.getAsJsonArray("items")) {
                JsonObject item = element.getAsJsonObject();
                if (result.put(item.get("id").getAsString(), item.get("text").getAsString()) != null)
                    throw new IllegalArgumentException("Duplicate translation result");
            }
            return result;
        });
    }
    private CompletableFuture<JsonObject> request(String path, JsonObject body) {
        var builder = HttpRequest.newBuilder(endpoint.uri().resolve(path)).timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + endpoint.token()).header("Content-Type", "application/json");
        if (body == null) builder.GET(); else builder.POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        return map(http.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString()), response -> {
            if (response.statusCode() != 200) throw new IllegalStateException("Translation HTTP " + response.statusCode());
            if (response.body().length() > 2_000_000) throw new IllegalStateException("Translation response too large");
            return JsonParser.parseString(response.body()).getAsJsonObject();
        });
    }
    /** thenApply alone does not propagate cancellation to the HTTP exchange. */
    private static <T, R> CompletableFuture<R> map(CompletableFuture<T> source, java.util.function.Function<T, R> convert) {
        CompletableFuture<R> result = new CompletableFuture<>();
        source.whenComplete((value, error) -> {
            if (error != null) result.completeExceptionally(error);
            else try { result.complete(convert.apply(value)); } catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        result.whenComplete((value, error) -> { if (result.isCancelled()) source.cancel(true); });
        return result;
    }
}
