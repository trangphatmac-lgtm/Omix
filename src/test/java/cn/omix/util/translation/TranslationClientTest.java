package cn.omix.util.translation;

import cn.omix.util.ai.HarnessRuntime;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TranslationClientTest {
    @Test
    void sendsAiReplyThroughTheCurrentHarnessEndpoint() throws Exception {
        var payload = new AtomicReference<JsonObject>();
        var authorization = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat-reply", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            payload.set(JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject());
            byte[] response = "{\"text\":\"手速跟上再说。\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
        try {
            var client = new TranslationClient(new HarnessRuntime.TranslationEndpoint(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "fixture-token", 1));
            assertEquals("手速跟上再说。", client.reply("ProviderID", "ModelID", "Player_1", "g?\"你好")
                    .get(5, TimeUnit.SECONDS));
            assertEquals("Bearer fixture-token", authorization.get());
            assertEquals("ProviderID", payload.get().get("provider").getAsString());
            assertEquals("ModelID", payload.get().get("model").getAsString());
            assertEquals("Player_1", payload.get().get("sender").getAsString());
            assertEquals("g?\"你好", payload.get().get("message").getAsString());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsEmptyRepliesAndHttpFailures() throws Exception {
        var status = new java.util.concurrent.atomic.AtomicInteger(200);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat-reply", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] response = "{\"text\":\" \"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
        try {
            var client = new TranslationClient(new HarnessRuntime.TranslationEndpoint(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "fixture-token", 1));
            var empty = assertThrows(ExecutionException.class,
                    () -> client.reply("provider", "model", "Player", "挂").get(5, TimeUnit.SECONDS));
            assertInstanceOf(IllegalArgumentException.class, empty.getCause());
            status.set(502);
            var failed = assertThrows(ExecutionException.class,
                    () -> client.reply("provider", "model", "Player", "挂").get(5, TimeUnit.SECONDS));
            assertEquals("Harness HTTP 502", failed.getCause().getMessage());
        } finally {
            server.stop(0);
        }
    }
}
