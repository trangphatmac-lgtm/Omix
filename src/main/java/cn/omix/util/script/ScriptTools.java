package cn.omix.util.script;

import cn.omix.Client;
import cn.omix.script.ScriptManager;
import com.google.gson.*;
import net.minecraft.client.MinecraftClient;
import java.util.*;
import java.util.concurrent.*;

/** Transport-neutral implementation. Never expose a second independent evaluator/file writer. */
public final class ScriptTools {
    private ScriptTools() {}
    public static boolean supports(String name) { return ScriptReference.tools().asList().stream().anyMatch(tool -> tool.getAsJsonObject().getAsJsonObject("function").get("name").getAsString().equals(name)); }
    public static void validate(String name, JsonObject args) {
        JsonObject function = ScriptReference.tools().asList().stream().map(JsonElement::getAsJsonObject).map(tool -> tool.getAsJsonObject("function"))
                .filter(tool -> tool.get("name").getAsString().equals(name)).findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown script tool"));
        JsonObject schema = function.getAsJsonObject("parameters"), properties = schema.getAsJsonObject("properties");
        for (JsonElement required : schema.getAsJsonArray("required")) if (!args.has(required.getAsString())) throw new IllegalArgumentException("Missing " + required.getAsString());
        for (var entry : args.entrySet()) {
            if (!properties.has(entry.getKey()) || !entry.getValue().isJsonPrimitive()) throw new IllegalArgumentException("Invalid argument: " + entry.getKey());
            JsonObject property = properties.getAsJsonObject(entry.getKey()); JsonPrimitive value = entry.getValue().getAsJsonPrimitive();
            if (property.get("type").getAsString().equals("string")) {
                if (!value.isString()) throw new IllegalArgumentException("Expected string: " + entry.getKey());
                if (property.has("enum") && property.getAsJsonArray("enum").asList().stream().noneMatch(value::equals)) throw new IllegalArgumentException("Invalid " + entry.getKey());
            } else {
                if (!value.isNumber()) throw new IllegalArgumentException("Expected integer: " + entry.getKey());
                long number = value.getAsBigDecimal().longValueExact();
                if ((property.has("minimum") && number < property.get("minimum").getAsLong()) || (property.has("maximum") && number > property.get("maximum").getAsLong())) throw new IllegalArgumentException("Out of range: " + entry.getKey());
            }
        }
    }
    public static CompletableFuture<JsonElement> execute(String name, JsonObject args) {
        try { validate(name, args); }
        catch (Exception error) { return CompletableFuture.failedFuture(error); }
        if (name.equals("script_screenshot")) return ScriptScreenshot.capture(Client.instance.getScriptManager().files().root().resolve(".screenshots"));
        return MinecraftClient.getInstance().submit(() -> {
            try { return call(name, args); }
            catch (Exception error) { throw new CompletionException(error); }
        });
    }
    public static JsonElement call(String name, JsonObject args) throws Exception {
        validate(name, args);
        ScriptManager manager = Client.instance.getScriptManager();
        if (manager == null) throw new IllegalStateException("Script service unavailable");
        String id = string(args, "id", ""); Gson gson = new Gson();
        return switch (name) {
            case "script_status" -> manager.status();
            case "script_read" -> gson.toJsonTree(manager.files().read(id));
            case "script_write" -> gson.toJsonTree(manager.files().write(id, string(args, "source", ""), string(args, "expectedHash", "")));
            case "script_delete" -> { manager.files().delete(id, string(args, "expectedHash", "")); yield ok("source_deleted"); }
            case "script_templates" -> gson.toJsonTree(ScriptReference.templates());
            case "script_create" -> {
                String template = string(args, "template", "");
                if (!ScriptReference.templates().contains(template)) throw new IllegalArgumentException("Unknown template");
                yield gson.toJsonTree(manager.files().write(id, ScriptReference.read("examples/" + template + ".java"), ""));
            }
            case "script_action" -> {
                String action = string(args, "action", "");
                if (action.equals("unload")) { manager.unload(id); yield ok("unloaded"); }
                yield gson.toJsonTree(manager.submit(id, action));
            }
            case "script_job" -> gson.toJsonTree(manager.job(string(args, "jobId", "")));
            case "script_cancel" -> { manager.cancel(string(args, "jobId", "")); yield ok("cancel_requested"); }
            case "script_logs" -> manager.log().read(id, number(args, "after", 0), (int) number(args, "limit", 50));
            case "script_api" -> ScriptReference.search(string(args, "query", ""), (int) number(args, "limit", 30));
            case "script_reference" -> new JsonPrimitive(ScriptReference.read(string(args, "path", "README.md")));
            case "script_evaluate" -> gson.toJsonTree(manager.evaluate(string(args, "source", "")));
            case "script_screenshot" -> throw new IllegalStateException("Use asynchronous screenshot execution");
            default -> throw new IllegalArgumentException("Unknown script tool: " + name);
        };
    }
    private static JsonObject ok(String state) { JsonObject result = new JsonObject(); result.addProperty("state", state); return result; }
    private static String string(JsonObject args, String name, String fallback) { return args.has(name) ? args.get(name).getAsString() : fallback; }
    private static long number(JsonObject args, String name, long fallback) { return args.has(name) ? args.get(name).getAsLong() : fallback; }
}
