package cn.omix.util.script;

import com.google.gson.*;
import java.util.*;

/** Bounded text-only history; never retains script classes or game objects. */
public final class ScriptLog {
    public record Entry(long sequence, long time, String script, long generation, String level, String message, int line, String callback) {}
    private final ArrayDeque<Entry> entries = new ArrayDeque<>();
    private long sequence;
    public synchronized void add(String script, long generation, String level, Object message, int line) {
        add(script, generation, level, message, line, "");
    }
    public synchronized void add(String script, long generation, String level, Object message, int line, String callback) {
        String text = String.valueOf(message);
        if (text.length() > 8192) text = text.substring(0, 8192) + "…";
        entries.addLast(new Entry(++sequence, System.currentTimeMillis(), script, generation, level, text, line, callback));
        while (entries.size() > 1024) entries.removeFirst();
    }
    public synchronized JsonObject read(String script, long after, int limit) {
        if (limit < 1 || limit > 200) throw new IllegalArgumentException("Log limit must be 1–200.");
        var result = new JsonObject(); var array = new JsonArray(); var gson = new Gson(); long cursor = after;
        for (Entry entry : entries) {
            if (entry.sequence <= after) continue;
            cursor = entry.sequence;
            if (script == null || script.isBlank() || script.equals(entry.script)) array.add(gson.toJsonTree(entry));
            if (array.size() >= limit) break;
        }
        result.add("entries", array); result.addProperty("nextCursor", cursor);
        result.addProperty("missed", entries.isEmpty() ? 0 : Math.max(0, entries.getFirst().sequence - after - 1));
        return result;
    }
}
