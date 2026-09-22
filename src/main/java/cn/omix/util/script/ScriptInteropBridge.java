package cn.omix.util.script;

import im.webui.interop.*;
import io.netty.handler.codec.http.HttpResponseStatus;
import com.google.gson.*;
import java.util.concurrent.TimeUnit;

/** The authenticated WebUI uses the same schema and service as both Agent transports. */
public final class ScriptInteropBridge {
    public ScriptInteropBridge(InteropServer server) {
        server.getRoutes().get("/api/v1/scripts/activePage", request -> InteropResponse.json(HttpResponseStatus.OK, ScriptWebPages.active()));
        server.getRoutes().get("/api/v1/scripts/page", request -> {
            try {
                var page = ScriptWebPages.get(request.query().get("id").getFirst(), Long.parseLong(request.query().get("generation").getFirst()));
                return InteropResponse.text(HttpResponseStatus.OK, ScriptWebPages.html(page), "text/html; charset=UTF-8");
            } catch (Exception error) { return InteropResponse.text(HttpResponseStatus.NOT_FOUND, "Page is unavailable"); }
        });
        server.getRoutes().post("/api/v1/scripts/pageMessage", request -> {
            try {
                JsonObject body = request.body();
                var value = net.minecraft.client.MinecraftClient.getInstance().submit(() -> ScriptWebPages.get(body.get("id").getAsString(), body.get("generation").getAsLong()).message(body.get("message"))).get(10, TimeUnit.SECONDS);
                JsonObject result = new JsonObject(); result.add("value", value); return InteropResponse.json(HttpResponseStatus.OK, result);
            } catch (Exception error) {
                JsonObject result = new JsonObject(); result.addProperty("error", "Page callback unavailable: " + error.getMessage());
                return InteropResponse.json(HttpResponseStatus.BAD_REQUEST, result);
            }
        });
        server.getRoutes().post("/api/v1/scripts/openAi", request -> {
            net.minecraft.client.MinecraftClient.getInstance().execute(() -> im.webui.WebUiRuntime.getInstance().openAiScreen());
            return InteropResponse.json(HttpResponseStatus.OK, new JsonObject());
        });
        server.getRoutes().post("/api/v1/scripts/call", request -> {
            try {
                String name = request.body().get("name").getAsString();
                JsonObject arguments = request.body().getAsJsonObject("arguments");
                JsonElement value = ScriptTools.execute(name, arguments).get(20, TimeUnit.SECONDS);
                JsonObject result = new JsonObject(); result.add("value", value);
                return InteropResponse.json(HttpResponseStatus.OK, result);
            } catch (Exception error) {
                Throwable cause = error; while (cause.getCause() != null) cause = cause.getCause();
                JsonObject result = new JsonObject(); result.addProperty("error", cause.getMessage());
                return InteropResponse.json(HttpResponseStatus.BAD_REQUEST, result);
            }
        });
    }
}
