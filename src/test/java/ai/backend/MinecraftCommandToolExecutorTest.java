package ai.backend;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftCommandToolExecutorTest {
    @Test
    void exposesAllCommandAndReadOnlyTools() {
        AiToolSnapshot snapshot = MinecraftCommandToolExecutor.buildSnapshot(
                List.of("/help"),
                List.of(".toggle")
        );
        Set<String> names = new HashSet<>();
        for (var definition : snapshot.definitions()) {
            JsonObject function = definition.getAsJsonObject().getAsJsonObject("function");
            names.add(function.get("name").getAsString());
        }

        assertEquals(Set.of(
                "run_minecraft_command",
                "run_client_command",
                "run_baritone_command",
                "getinventory",
                "getnearbyblock",
                "getlookingblock",
                "getspecificblock",
                "getallconfig"
        ), names);

        JsonObject nearby = findTool(snapshot.definitions(), "getnearbyblock");
        JsonObject range = nearby.getAsJsonObject("function")
                .getAsJsonObject("parameters")
                .getAsJsonObject("properties")
                .getAsJsonObject("range");
        assertEquals(3, range.get("minimum").getAsInt());
        assertEquals(10, range.get("maximum").getAsInt());
    }

    @Test
    void parsesAbsoluteAndRelativeBlockPositions() {
        assertEquals(
                new BlockPos(100, 64, -20),
                MinecraftCommandToolExecutor.parseBlockPosition("100 64 -20", 12.5, 63.0, 8.0)
        );
        assertEquals(
                new BlockPos(12, 64, 5),
                MinecraftCommandToolExecutor.parseBlockPosition("~ ~1 ~-2.25", 12.75, 63.2, 8.0)
        );
        assertEquals(
                new BlockPos(-1, 70, 4),
                MinecraftCommandToolExecutor.parseBlockPosition("~, ~, ~", -0.1, 70.9, 4.9)
        );
    }

    @Test
    void rejectsMalformedPositions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MinecraftCommandToolExecutor.parseBlockPosition("1 2", 0, 0, 0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> MinecraftCommandToolExecutor.parseBlockPosition("~nope 2 3", 0, 0, 0)
        );
    }

    @Test
    void rejectsNearbyBlockRangesOutsideThreeThroughTen() {
        MinecraftCommandToolExecutor executor = new MinecraftCommandToolExecutor();

        String tooSmall = executor.execute(
                new AiToolCall("range-small", "getnearbyblock", "{\"range\":2}")
        ).join();
        String tooLarge = executor.execute(
                new AiToolCall("range-large", "getnearbyblock", "{\"range\":11}")
        ).join();
        String fractional = executor.execute(
                new AiToolCall("range-fractional", "getnearbyblock", "{\"range\":3.5}")
        ).join();

        assertTrue(tooSmall.contains("range must be an integer from 3 through 10"));
        assertTrue(tooLarge.contains("range must be an integer from 3 through 10"));
        assertTrue(fractional.contains("range must be an integer from 3 through 10"));
    }

    private static JsonObject findTool(JsonArray tools, String name) {
        for (var definition : tools) {
            JsonObject tool = definition.getAsJsonObject();
            if (name.equals(tool.getAsJsonObject("function").get("name").getAsString())) {
                return tool;
            }
        }
        throw new AssertionError("Missing tool: " + name);
    }
}
