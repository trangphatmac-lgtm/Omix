package cn.omix.util.script;

import com.google.gson.*;
import java.util.*;

/** Deliberately bounded JSON Schema subset shared by all script tool transports. */
public final class ScriptToolSchema {
    private static final Set<String> COMMON = Set.of("type", "description", "enum");
    private final JsonObject schema;

    public ScriptToolSchema(JsonObject source) {
        if (source == null || source.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 32768) throw invalid("$", "schema exceeds 32 KiB or is missing");
        schema = source.deepCopy();
        checkSchema(schema, "$", 0);
        if (!schema.get("type").getAsString().equals("object")) throw invalid("$", "parameters must be an object");
    }
    public JsonObject json() { return schema.deepCopy(); }
    public void validate(JsonObject arguments) { validate(schema, arguments, "$", 0); }
    private static IllegalArgumentException invalid(String path, String message) {
        return new IllegalArgumentException("Tool parameters " + path + ": " + message);
    }
    private static String string(JsonElement value, String path) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw invalid(path, "expected a string");
        return value.getAsString();
    }
    private static void checkSchema(JsonObject schema, String path, int depth) {
        if (depth > 16) throw invalid(path, "schema nesting exceeds 16");
        String type = string(schema.get("type"), path + ".type");
        Set<String> allowed = new HashSet<>(COMMON);
        switch (type) {
            case "object" -> allowed.addAll(Set.of("properties", "required", "additionalProperties"));
            case "array" -> allowed.addAll(Set.of("items", "minItems", "maxItems"));
            case "string" -> allowed.addAll(Set.of("minLength", "maxLength"));
            case "integer", "number" -> allowed.addAll(Set.of("minimum", "maximum"));
            case "boolean", "null" -> { }
            default -> throw invalid(path, "unsupported type: " + type);
        }
        for (String key : schema.keySet()) if (!allowed.contains(key)) throw invalid(path, "unsupported schema keyword: " + key);
        if (schema.has("description")) string(schema.get("description"), path + ".description");
        if (schema.has("enum") && (!schema.get("enum").isJsonArray() || schema.getAsJsonArray("enum").isEmpty())) throw invalid(path, "enum must be a nonempty array");
        for (String key : List.of("minimum", "maximum", "minLength", "maxLength", "minItems", "maxItems")) {
            if (!schema.has(key)) continue;
            JsonElement value = schema.get(key);
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw invalid(path, key + " must be a number");
            try {
                var number = value.getAsBigDecimal();
                if (!key.equals("minimum") && !key.equals("maximum")) {
                    if (number.intValueExact() < 0) throw invalid(path, key + " must be nonnegative");
                }
            } catch (ArithmeticException | NumberFormatException error) { throw invalid(path, "invalid " + key); }
        }
        for (String[] pair : List.of(new String[]{"minimum", "maximum"}, new String[]{"minLength", "maxLength"}, new String[]{"minItems", "maxItems"}))
            if (schema.has(pair[0]) && schema.has(pair[1]) && schema.get(pair[0]).getAsBigDecimal().compareTo(schema.get(pair[1]).getAsBigDecimal()) > 0)
                throw invalid(path, pair[0] + " exceeds " + pair[1]);
        if (type.equals("object")) {
            if (!schema.has("properties")) schema.add("properties", new JsonObject());
            if (!schema.get("properties").isJsonObject()) throw invalid(path, "properties must be an object");
            for (var entry : schema.getAsJsonObject("properties").entrySet()) {
                if (!entry.getValue().isJsonObject()) throw invalid(path, "property schema must be an object");
                checkSchema(entry.getValue().getAsJsonObject(), path + "." + entry.getKey(), depth + 1);
            }
            if (!schema.has("additionalProperties")) schema.addProperty("additionalProperties", false);
            var additional = schema.get("additionalProperties");
            if (!additional.isJsonPrimitive() || !additional.getAsJsonPrimitive().isBoolean()) throw invalid(path, "additionalProperties must be boolean");
            if (schema.has("required")) {
                if (!schema.get("required").isJsonArray()) throw invalid(path, "required must be an array");
                Set<String> required = new HashSet<>();
                for (var entry : schema.getAsJsonArray("required")) {
                    String key = string(entry, path + ".required");
                    if (!required.add(key) || !schema.getAsJsonObject("properties").has(key)) throw invalid(path, "duplicate or undeclared required property: " + key);
                }
            }
        }
        if (type.equals("array")) {
            if (!schema.has("items") || !schema.get("items").isJsonObject()) throw invalid(path, "array requires an items schema");
            checkSchema(schema.getAsJsonObject("items"), path + "[]", depth + 1);
        }
    }
    private static void validate(JsonObject schema, JsonElement value, String path, int depth) {
        if (value == null || depth > 32) throw invalid(path, "missing value or excessive nesting");
        String type = schema.get("type").getAsString();
        boolean matches = switch (type) {
            case "object" -> value.isJsonObject();
            case "array" -> value.isJsonArray();
            case "string" -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
            case "number", "integer" -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber();
            case "boolean" -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean();
            case "null" -> value.isJsonNull();
            default -> false;
        };
        if (!matches) throw invalid(path, "expected " + type);
        if (schema.has("enum") && !schema.getAsJsonArray("enum").contains(value)) throw invalid(path, "value is outside enum");
        switch (type) {
            case "object" -> {
                JsonObject properties = schema.getAsJsonObject("properties");
                if (schema.has("required")) for (var key : schema.getAsJsonArray("required"))
                    if (!value.getAsJsonObject().has(key.getAsString())) throw invalid(path + "." + key.getAsString(), "required");
                for (var entry : value.getAsJsonObject().entrySet()) {
                    if (properties.has(entry.getKey())) validate(properties.getAsJsonObject(entry.getKey()), entry.getValue(), path + "." + entry.getKey(), depth + 1);
                    else if (!schema.get("additionalProperties").getAsBoolean()) throw invalid(path + "." + entry.getKey(), "unknown property");
                }
            }
            case "array" -> {
                size(schema, value.getAsJsonArray().size(), "minItems", "maxItems", path);
                int index = 0;
                for (var item : value.getAsJsonArray()) validate(schema.getAsJsonObject("items"), item, path + "[" + index++ + "]", depth + 1);
            }
            case "string" -> size(schema, value.getAsString().codePointCount(0, value.getAsString().length()), "minLength", "maxLength", path);
            case "integer", "number" -> {
                try {
                    var number = value.getAsBigDecimal();
                    if (type.equals("integer") && number.stripTrailingZeros().scale() > 0) throw invalid(path, "expected integer");
                    if (schema.has("minimum") && number.compareTo(schema.get("minimum").getAsBigDecimal()) < 0) throw invalid(path, "below minimum");
                    if (schema.has("maximum") && number.compareTo(schema.get("maximum").getAsBigDecimal()) > 0) throw invalid(path, "above maximum");
                } catch (NumberFormatException error) { throw invalid(path, "expected finite number"); }
            }
        }
    }
    private static void size(JsonObject schema, int size, String min, String max, String path) {
        if (schema.has(min) && size < schema.get(min).getAsInt()) throw invalid(path, "below " + min);
        if (schema.has(max) && size > schema.get(max).getAsInt()) throw invalid(path, "above " + max);
    }
}
