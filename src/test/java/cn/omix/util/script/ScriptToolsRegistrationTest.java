package cn.omix.util.script;

import cn.omix.script.api.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ScriptToolsRegistrationTest {
    @TempDir Path temp;
    private ScriptContext context(String id, long generation) {
        return new ScriptContext(id, generation, temp, new ScriptLog(), ScriptSource.wrap("Sample", ""));
    }
    private JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    private ToolHandle tool(ScriptContext context, String id, String result) {
        var tool = new ToolHandle(context, id, "Test tool", json("{\"type\":\"object\"}"), args -> new JsonPrimitive(result)).requiresWorld(false);
        context.add(tool); return tool;
    }
    @Test void prepareReplaceRollbackUnloadAndStaleHandle() {
        try (var old = context("old", 1); var next = context("old", 2); var conflict = context("other", 3)) {
            var first = tool(old, "lifecycle", "old"); old.prepared();
            assertFalse(first.available()); old.validate(null); old.install();
            assertEquals("old", ScriptToolRegistry.INSTANCE.execute(first.name(), new JsonObject(), false).get("value").getAsString());
            tool(conflict, "lifecycle", "conflict"); conflict.prepared();
            assertThrows(IllegalArgumentException.class, () -> conflict.validate(null));
            var second = tool(next, "lifecycle", "new"); next.prepared(); next.validate(old);
            old.detach(); assertThrows(IllegalStateException.class, () -> first.invoke(new JsonObject(), false));
            next.install();
            assertEquals(2, ScriptToolRegistry.INSTANCE.execute(second.name(), new JsonObject(), false).get("generation").getAsLong());
            // Simulate commit failure: detach new, restore old registrations and active scope.
            next.close(); old.install();
            assertEquals("old", ScriptToolRegistry.INSTANCE.execute(first.name(), new JsonObject(), false).get("value").getAsString());
            old.close();
            assertThrows(IllegalArgumentException.class, () -> ScriptToolRegistry.INSTANCE.execute(first.name(), new JsonObject(), false));
            assertThrows(IllegalStateException.class, () -> first.invoke(new JsonObject(), false));
        }
    }
    @Test void rejectedInputsDoNotFaultToolAndCallbackFailureDisablesOnlyOneFeature() {
        try (var ctx = context("failures", 1)) {
            var count = new AtomicInteger();
            var tool = new ToolHandle(ctx, "failure", "Test", json("{\"type\":\"object\",\"properties\":{\"x\":{\"type\":\"integer\"}},\"required\":[\"x\"]}"), args -> {
                count.incrementAndGet(); throw new IllegalStateException("boom");
            });
            ctx.add(tool); var other = tool(ctx, "healthy", "ok"); ctx.prepared(); ctx.install();
            assertThrows(IllegalStateException.class, () -> tool.invoke(json("{\"x\":1}"), false));
            assertThrows(IllegalArgumentException.class, () -> tool.invoke(json("{\"x\":\"1\"}"), true));
            assertEquals(0, count.get()); assertTrue(tool.available());
            assertThrows(IllegalStateException.class, () -> tool.invoke(json("{\"x\":1}"), true));
            assertEquals(1, count.get()); assertFalse(tool.available()); assertTrue(other.available());
            assertThrows(IllegalStateException.class, () -> tool.invoke(json("{\"x\":1}"), true));
            assertEquals(1, count.get());
            assertEquals(1, ScriptToolRegistry.INSTANCE.definitions().size());
        }
    }
    @Test void installFailureDoesNotRemoveAnotherScriptsTool() {
        try (var a = context("a", 1); var b = context("b", 2)) {
            var original = tool(a, "collision", "a"); a.prepared(); a.install();
            tool(b, "firstInstalled", "b"); tool(b, "collision", "b"); b.prepared();
            assertThrows(IllegalArgumentException.class, b::install);
            assertFalse(b.active());
            assertEquals("a", ScriptToolRegistry.INSTANCE.execute(original.name(), new JsonObject(), false).get("value").getAsString());
            assertThrows(IllegalArgumentException.class, () -> ScriptToolRegistry.INSTANCE.execute("custom_firstInstalled", new JsonObject(), false));
        }
    }
    @Test void schemaSupportsNestedObjectsArraysBoundsEnumsAndDefensiveCopies() {
        var schema = json("""
            {"type":"object","properties":{"rows":{"type":"array","minItems":1,"maxItems":2,
              "items":{"type":"object","properties":{"count":{"type":"integer","minimum":0,"maximum":3},
              "kind":{"type":"string","enum":["a","b"],"maxLength":1}},"required":["count","kind"]}}},"required":["rows"]}
            """);
        var compiled = new ScriptToolSchema(schema); schema.addProperty("type", "string");
        compiled.validate(json("{\"rows\":[{\"count\":2,\"kind\":\"a\"}]}"));
        for (String invalid : List.of("{}", "{\"rows\":[]}", "{\"rows\":[{\"count\":4,\"kind\":\"a\"}]}",
                "{\"rows\":[{\"count\":1.5,\"kind\":\"a\"}]}", "{\"rows\":[{\"count\":1,\"kind\":\"c\"}]}",
                "{\"rows\":[{\"count\":1,\"kind\":\"a\",\"extra\":true}]}"))
            assertThrows(IllegalArgumentException.class, () -> compiled.validate(json(invalid)), invalid);
        assertThrows(IllegalArgumentException.class, () -> new ScriptToolSchema(json("{\"type\":\"object\",\"$ref\":\"missing\"}")));
        assertThrows(IllegalArgumentException.class, () -> new ScriptToolSchema(json("{\"type\":\"object\",\"required\":[\"missing\"]}")));
        assertThrows(IllegalArgumentException.class, () -> new ScriptToolSchema(json("{\"type\":\"array\"}")));
    }
    @Test void multibyteOutputLimitFaultsCallbackBeforeTransport() {
        try (var ctx = context("bounded", 1)) {
            var tool = new ToolHandle(ctx, "bounded", "Test", json("{\"type\":\"object\"}"),
                    args -> new JsonPrimitive("界".repeat(90000))).requiresWorld(false);
            ctx.add(tool); ctx.prepared(); ctx.install();
            assertTrue(assertThrows(IllegalStateException.class, () -> tool.invoke(new JsonObject(), false)).getMessage().contains("256 KiB"));
            assertFalse(tool.available());
        }
    }
    @Test void metadataIsFrozenAfterPreparationAndOutputIsDetached() {
        try (var ctx = context("metadata", 9)) {
            JsonObject input = new JsonObject(), output = json("{\"ok\":true}");
            var tool = new ToolHandle(ctx, "detached", "Test", json("{\"type\":\"object\"}"), args -> { args.addProperty("changed", true); return output; }).requiresWorld(false);
            ctx.add(tool); ctx.prepared(); ctx.install();
            assertThrows(IllegalStateException.class, () -> tool.requiresWorld(true));
            var result = tool.invoke(input, false); output.addProperty("ok", false);
            assertTrue(result.getAsJsonObject("value").get("ok").getAsBoolean()); assertTrue(input.isEmpty());
            assertEquals("metadata", result.get("script").getAsString());
            assertThrows(IllegalStateException.class, () -> tool(ctx, "late", "oops"));
        }
    }
}
