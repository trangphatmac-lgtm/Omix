package cn.omix.util.script;

import cn.omix.script.api.*;
import com.google.gson.*;
import java.util.*;

/** Client-thread registry. Prepared tools remain invisible until their generation activates. */
public final class ScriptToolRegistry {
    public static final ScriptToolRegistry INSTANCE = new ScriptToolRegistry();
    private final Map<String, ToolHandle> tools = new LinkedHashMap<>();

    public void validate(List<ToolHandle> proposed, List<ToolHandle> replaced) {
        Set<String> names = new HashSet<>();
        for (var tool : proposed) {
            var existing = tools.get(tool.name());
            if (!names.add(tool.name()) || existing != null && !replaced.contains(existing))
                throw new IllegalArgumentException("AI tool name already registered: " + tool.name());
        }
    }
    public Registration install(ToolHandle tool) {
        validate(List.of(tool), List.of());
        tools.put(tool.name(), tool);
        return () -> tools.remove(tool.name(), tool);
    }
    public JsonArray definitions() {
        JsonArray result = new JsonArray();
        for (var tool : tools.values()) if (tool.available()) result.add(tool.definition());
        return result;
    }
    public JsonObject execute(String name, JsonObject arguments, boolean inWorld) {
        var tool = tools.get(name);
        if (tool == null) throw new IllegalArgumentException("Unknown or unloaded script tool: " + name + "; refresh tools before retrying");
        return tool.invoke(arguments, inWorld);
    }
    public boolean requiresWorld(String name) {
        var tool = tools.get(name);
        return tool == null || tool.requiresWorld();
    }
}
