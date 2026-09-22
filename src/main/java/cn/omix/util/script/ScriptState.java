package cn.omix.util.script;

import cn.omix.module.Module;
import cn.omix.module.value.Value;
import cn.omix.module.value.impl.*;
import cn.omix.ui.hud.Drag;
import com.google.gson.*;
import java.util.*;

/** Detached state used for generation replacement, without retaining ClassLoaders in config. */
public final class ScriptState {
    private ScriptState() {}
    public static JsonObject capture(Module module) {
        JsonObject result = new JsonObject(); result.addProperty("enabled", module.isEnabled());
        result.addProperty("key", module.getKey()); result.addProperty("hidden", module.isHidden());
        JsonObject values = new JsonObject();
        for (Value value : module.getValues()) {
            switch (value) {
                case BoolValue v -> values.addProperty(v.getName(), v.getValue());
                case NumberValue v -> values.addProperty(v.getName(), v.getValue());
                case ModeValue v -> values.addProperty(v.getName(), v.getValue());
                case KeyValue v -> values.addProperty(v.getName(), v.getValue());
                case TextValue v -> values.addProperty(v.getName(), v.getValue());
                case ColorValue v -> values.addProperty(v.getName(), v.getValue().getRGB());
                case MultiBoolValue v -> { JsonObject children = new JsonObject(); v.getValues().forEach(child -> children.addProperty(child.getName(), child.getValue())); values.add(v.getName(), children); }
                default -> {}
            }
        }
        result.add("values", values);
        if (module instanceof Drag drag) { result.addProperty("percentX", drag.percentX); result.addProperty("percentY", drag.percentY); }
        return result;
    }
    public static void restore(Module module, JsonObject state) {
        if (state == null) return;
        module.setKey(state.get("key").getAsInt()); module.setHidden(state.get("hidden").getAsBoolean());
        JsonObject values = state.getAsJsonObject("values");
        // Modes first so visibility and selected behavior are established before activation.
        List<Value> ordered = new ArrayList<>(module.getValues()); ordered.sort(Comparator.comparing(value -> !(value instanceof ModeValue)));
        for (Value value : ordered) {
            JsonElement element = values.get(value.getName()); if (element == null) continue;
            try {
                switch (value) {
                    case BoolValue v -> v.setValue(element.getAsBoolean());
                    case NumberValue v -> v.setValue(element.getAsDouble());
                    case ModeValue v -> v.setValue(element.getAsString());
                    case KeyValue v -> v.setValue(element.getAsInt());
                    case TextValue v -> v.setValue(element.getAsString());
                    case ColorValue v -> v.setValue(new java.awt.Color(element.getAsInt(), true));
                    case MultiBoolValue v -> { for (var child : v.getValues()) if (element.getAsJsonObject().has(child.getName())) child.setValue(element.getAsJsonObject().get(child.getName()).getAsBoolean()); }
                    default -> {}
                }
            } catch (IllegalStateException | NumberFormatException ignored) { /* An incompatible setting keeps its new default. */ }
        }
        if (module instanceof Drag drag && state.has("percentX")) { drag.percentX = state.get("percentX").getAsFloat(); drag.percentY = state.get("percentY").getAsFloat(); }
        module.setEnabled(!module.isHoldToUse() && state.get("enabled").getAsBoolean());
    }
}
