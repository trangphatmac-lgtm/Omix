package cn.omix.util.script;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;

/** Documentation and tool schemas are the same artifacts used by the human UI and MCP. */
public final class ScriptReference {
    private ScriptReference() {}
    public static String read(String path) throws IOException {
        if (path == null || path.isBlank()) path = "README.md";
        if (!path.matches("[A-Za-z0-9_./-]+") || path.contains("..") || path.startsWith("/")) throw new IllegalArgumentException("Invalid reference path");
        try (InputStream input = ScriptReference.class.getResourceAsStream("/assets/omix/script/reference/" + path)) {
            if (input == null) throw new FileNotFoundException("Unknown script reference: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    public static JsonArray tools() {
        try { return JsonParser.parseString(read("tools.json")).getAsJsonArray(); }
        catch (IOException error) { throw new IllegalStateException("Missing script tools schema", error); }
    }
    public static JsonArray search(String query, int limit) throws IOException {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("API limit must be 1–100");
        JsonArray index = JsonParser.parseString(read("api.json")).getAsJsonArray();
        String term = query.toLowerCase(Locale.ROOT); JsonArray result = new JsonArray();
        for (JsonElement entry : index) {
            if (entry.toString().toLowerCase(Locale.ROOT).contains(term)) result.add(entry);
            if (result.size() == limit) break;
        }
        return result;
    }
    public static List<String> templates() throws IOException {
        return new Gson().fromJson(read("examples/index.json"), new com.google.gson.reflect.TypeToken<List<String>>(){}.getType());
    }
}
