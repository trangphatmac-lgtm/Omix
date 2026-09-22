package cn.omix.script.api;

import cn.omix.util.script.ScriptFailures;
import cn.omix.util.script.ScriptToolSchema;
import com.google.gson.*;
import java.util.Objects;
import java.util.function.Function;

/** A synchronous AI tool owned by one script generation. Configure only in onLoad. */
public final class ToolHandle {
    private final ScriptContext context;
    private final String name;
    private final String description;
    private final ScriptToolSchema parameters;
    private final Function<JsonObject, JsonElement> callback;
    private boolean requiresWorld = true;
    private boolean faulted;

    public ToolHandle(ScriptContext context, String id, String description, JsonObject parameters,
                      Function<JsonObject, JsonElement> callback) {
        if (id == null || !id.matches("[A-Za-z][A-Za-z0-9_]{0,56}")) throw new IllegalArgumentException("Tool id must match [A-Za-z][A-Za-z0-9_]{0,56}");
        if (description == null || description.isBlank() || description.length() > 4096) throw new IllegalArgumentException("Tool description must contain 1–4096 characters");
        this.context = Objects.requireNonNull(context);
        context.qualify(id);
        this.name = "custom_" + id;
        this.description = description;
        this.parameters = new ScriptToolSchema(parameters);
        this.callback = Objects.requireNonNull(callback);
    }
    public String name() { return name; }
    public boolean requiresWorld() { return requiresWorld; }
    public ToolHandle requiresWorld(boolean value) { context.ensurePreparing(); requiresWorld = value; return this; }
    public boolean available() { return context.active() && !faulted; }
    public JsonObject definition() {
        JsonObject function = new JsonObject();
        function.addProperty("name", name);
        function.addProperty("description", description + "\n[Omix script: " + context.id() + ", generation: " + context.generation()
                + ", requires world: " + requiresWorld + "]");
        function.add("parameters", parameters.json());
        JsonObject definition = new JsonObject(); definition.addProperty("type", "function"); definition.add("function", function);
        return definition;
    }
    /** Bridge dispatch only, on the client thread. Input errors do not disable the callback. */
    public JsonObject invoke(JsonObject arguments, boolean inWorld) {
        context.requireActive();
        if (faulted) throw new IllegalStateException("Tool " + name + " is disabled after a callback error; reload its script");
        if (requiresWorld && !inWorld) throw new IllegalStateException("Enter a world before calling " + name);
        parameters.validate(arguments);
        try {
            JsonElement value = callback.apply(arguments.deepCopy());
            context.requireActive();
            JsonObject result = new JsonObject();
            result.addProperty("tool", name); result.addProperty("script", context.id()); result.addProperty("generation", context.generation());
            result.add("value", value == null ? JsonNull.INSTANCE : value.deepCopy());
            if (result.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 262144) throw new IllegalStateException("Tool result exceeds 256 KiB; return a bounded summary");
            return result;
        } catch (Throwable error) {
            ScriptFailures.rethrowFatal(error);
            faulted = true;
            context.error("tool:" + name, error);
            throw new IllegalStateException("Tool " + name + " failed (script " + context.id() + ", generation " + context.generation()
                    + "); callback disabled: " + error.getMessage(), error);
        }
    }
}
