package cn.omix.util.script;

import cn.omix.module.value.Value;
import cn.omix.module.value.impl.*;
import com.google.gson.*;
import java.awt.Color;
import java.util.*;

/** Validated, detached access to existing module settings; setters preserve native mode listeners. */
public final class ScriptModuleSettings {
    private ScriptModuleSettings() {}
    public static Value find(List<Value> values, String name) {
        return values.stream().filter(value -> value.getName().equalsIgnoreCase(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown setting: " + name));
    }
    public static JsonElement read(Value value) {
        return switch (value) {
            case BoolValue v -> new JsonPrimitive(v.getValue());
            case NumberValue v -> new JsonPrimitive(v.getValue());
            case ModeValue v -> new JsonPrimitive(v.getValue());
            case TextValue v -> new JsonPrimitive(v.getValue());
            case KeyValue v -> new JsonPrimitive(v.getValue());
            case ColorValue v -> new JsonPrimitive(v.getValue().getRGB());
            case MultiBoolValue v -> { var result = new JsonObject(); v.getValues().forEach(child -> result.add(child.getName(), read(child))); yield result; }
            default -> throw new IllegalArgumentException("Unsupported setting: " + value.getName());
        };
    }
    public static JsonObject describe(Value value) {
        var result = new JsonObject(); result.addProperty("name", value.getName());
        result.addProperty("type", value.getClass().getSimpleName()); result.addProperty("visible", value.isVisible());
        if (value instanceof NumberValue number) {
            result.addProperty("min", number.getMin()); result.addProperty("max", number.getMax()); result.addProperty("step", number.getInc());
        }
        if (value instanceof ModeValue mode) { var modes = new JsonArray(); for (String choice : mode.getModes()) modes.add(choice); result.add("options", modes); }
        if (value instanceof MultiBoolValue multi) { var children = new JsonArray(); multi.getValues().forEach(child -> children.add(child.getName())); result.add("options", children); }
        if (value instanceof TextValue text) result.addProperty("sensitive", text.isSensitive());
        return result;
    }
    public static void write(Value value, Object supplied) {
        JsonElement input = json(supplied);
        switch (value) {
            case BoolValue v -> { require(input, "boolean"); v.setValue(input.getAsBoolean()); }
            case NumberValue v -> {
                require(input, "number"); double number = input.getAsDouble();
                float stored = (float) number;
                if (!Double.isFinite(number) || !Float.isFinite(stored) || stored < v.getMin() || stored > v.getMax()) throw new IllegalArgumentException("Setting " + v.getName() + " must be between " + v.getMin() + " and " + v.getMax());
                v.setValue(number);
            }
            case ModeValue v -> {
                require(input, "string"); String mode = input.getAsString();
                if (Arrays.stream(v.getModes()).noneMatch(mode::equalsIgnoreCase)) throw new IllegalArgumentException("Unknown mode: " + mode);
                v.setValue(mode);
            }
            case TextValue v -> { require(input, "string"); v.setValue(input.getAsString()); }
            case KeyValue v -> v.setValue(integer(input));
            case ColorValue v -> v.setValue(new Color(integer(input), true));
            case MultiBoolValue v -> {
                if (!input.isJsonObject()) throw new IllegalArgumentException("MultiBoolValue expects an object of boolean options");
                // Validate the entire patch before changing any child.
                Map<BoolValue, Boolean> patch = new LinkedHashMap<>();
                for (var entry : input.getAsJsonObject().entrySet()) {
                    var child = v.getValues().stream().filter(item -> item.getName().equalsIgnoreCase(entry.getKey())).findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("Unknown option: " + entry.getKey()));
                    require(entry.getValue(), "boolean");
                    if (patch.put(child, entry.getValue().getAsBoolean()) != null) throw new IllegalArgumentException("Duplicate option: " + entry.getKey());
                }
                patch.forEach(BoolValue::setValue);
            }
            default -> throw new IllegalArgumentException("Unsupported setting: " + value.getName());
        }
    }
    private static int integer(JsonElement value) {
        require(value, "number");
        try { return value.getAsBigDecimal().intValueExact(); }
        catch (ArithmeticException | NumberFormatException error) { throw new IllegalArgumentException("Expected a 32-bit integer", error); }
    }
    private static void require(JsonElement value, String type) {
        boolean valid = value.isJsonPrimitive() && switch (type) {
            case "boolean" -> value.getAsJsonPrimitive().isBoolean();
            case "number" -> value.getAsJsonPrimitive().isNumber();
            default -> value.getAsJsonPrimitive().isString();
        };
        if (!valid) throw new IllegalArgumentException("Expected " + type + " setting value");
    }
    private static JsonElement json(Object value) {
        if (value instanceof JsonElement element) return element.deepCopy();
        if (value instanceof Boolean bool) return new JsonPrimitive(bool);
        if (value instanceof Number number) return new JsonPrimitive(number);
        if (value instanceof String text) return new JsonPrimitive(text);
        if (value instanceof Color color) return new JsonPrimitive(color.getRGB());
        if (value instanceof Map<?, ?> map) {
            var result = new JsonObject();
            for (var entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key) || !(entry.getValue() instanceof Boolean bool)) throw new IllegalArgumentException("Option map requires string keys and boolean values");
                result.addProperty(key, bool);
            }
            return result;
        }
        throw new IllegalArgumentException("Expected boolean, number, string, Color, boolean option map or JSON value");
    }
}
