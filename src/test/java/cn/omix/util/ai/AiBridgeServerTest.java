package cn.omix.util.ai;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.http.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class AiBridgeServerTest {
    @Test void requiresNodeAuthenticationAndReturnsTypedDomainResults() throws Exception {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var sessions = new GameToolSessions(Runnable::run, () -> 7, new GameToolSessions.Tools() {
            public CompletableFuture<JsonElement> execute(String name, JsonObject args) {
                calls.incrementAndGet();
                return CompletableFuture.completedFuture(JsonParser.parseString("{\"status\":\"awaiting_sync\"}"));
            }
            public void reset() { }
        });
        try (var server = new AiBridgeServer(sessions, () -> CompletableFuture.completedFuture(new JsonObject()), () -> "reference")) {
            var http = HttpClient.newHttpClient();
            var url = URI.create(server.endpoint() + "/v1/snapshot");
            assertEquals(403, http.send(HttpRequest.newBuilder(url).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(403, http.send(HttpRequest.newBuilder(url).header("Authorization", "Bearer " + server.token())
                    .header("Origin", "https://example.org").GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
            String body = "{\"id\":\"call1\",\"agentId\":\"agent\",\"worldEpoch\":7,\"name\":\"getcontainer\",\"arguments\":{}}";
            var request = HttpRequest.newBuilder(URI.create(server.endpoint()+"/v1/calls"))
                    .header("Authorization", "Bearer " + server.token()).POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            var json = JsonParser.parseString(response.body()).getAsJsonObject();
            assertTrue(json.get("ok").getAsBoolean());
            assertEquals("awaiting_sync", json.getAsJsonObject("value").get("status").getAsString());
            assertFalse(response.headers().firstValue("Access-Control-Allow-Origin").isPresent());
            http.send(request, HttpResponse.BodyHandlers.ofString()); assertEquals(1, calls.get());
        }
    }
    @Test void v2RequiresIssuedLeasesAndSeparatesCompetingAgents() throws Exception {
        var sessions = new GameToolSessions(Runnable::run, () -> 0, new GameToolSessions.Tools() {
            public CompletableFuture<JsonElement> execute(String name, JsonObject args) { return CompletableFuture.completedFuture(new JsonPrimitive("done")); }
            public void reset() { }
        });
        try (var server = new AiBridgeServer(sessions, () -> CompletableFuture.completedFuture(new JsonObject()), () -> "reference")) {
            var http=HttpClient.newHttpClient();
            java.util.function.BiFunction<String,String,JsonObject> post=(path,body)->{
                try { return JsonParser.parseString(http.send(HttpRequest.newBuilder(URI.create(server.endpoint()+path)).header("Authorization","Bearer "+server.token()).POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString()).body()).getAsJsonObject(); }
                catch(Exception error){throw new RuntimeException(error);}
            };
            String a=post.apply("/v2/sessions","{}").get("agentId").getAsString(), b=post.apply("/v2/sessions","{}").get("agentId").getAsString();
            java.util.function.BiFunction<String,String,String> body=(id,agent)->"{\"id\":\""+id+"\",\"agentId\":\""+agent+"\",\"worldEpoch\":0,\"name\":\"game\",\"arguments\":{}}";
            assertFalse(post.apply("/v2/calls",body.apply("0","unissued")).get("ok").getAsBoolean());
            assertTrue(post.apply("/v2/calls",body.apply("1",a)).get("ok").getAsBoolean());
            assertFalse(post.apply("/v2/calls",body.apply("2",b)).get("ok").getAsBoolean());
            assertTrue(post.apply("/v2/agents/"+a+"/heartbeat","{}").get("ok").getAsBoolean());
            post.apply("/v2/agents/"+a+"/release","{}");
            assertFalse(post.apply("/v2/calls",body.apply("3",a)).get("ok").getAsBoolean());
            assertTrue(post.apply("/v2/calls",body.apply("2",b)).get("ok").getAsBoolean());
        }
    }
    @Test void archivePathsAndLogsRejectEscapesAndSecrets() throws Exception {
        var root = java.nio.file.Path.of("/tmp/harness");
        assertThrows(java.io.IOException.class, () -> HarnessBundle.safePath(root, "../outside"));
        assertThrows(java.io.IOException.class, () -> HarnessBundle.safePath(root, "C:\\outside"));
        assertEquals(root.resolve("plugin/a.js"), HarnessBundle.safePath(root, "plugin/a.js"));
        assertFalse(HarnessRuntime.redact("http://127.0.0.1:4/?token=secret apiKey=key").contains("secret"));
    }
}
