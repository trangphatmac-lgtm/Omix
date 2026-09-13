package ai.backend;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AiClientReferenceTest {
    @Test
    void bundlesEveryDocumentVerbatimWithoutTruncation() throws Exception {
        String reference = AiClientReference.promptContext();
        try (var documents = Files.walk(Path.of("docs"))) {
            assertEquals(AiClientReference.DOCUMENTS.stream().sorted().toList(), documents
                    .filter(path -> path.toString().endsWith(".md"))
                    .map(path -> Path.of("docs").relativize(path).toString()).sorted().toList());
        }
        for (String document : AiClientReference.DOCUMENTS) {
            String source = Files.readString(Path.of("docs", document));
            try (var stream = AiClientReference.class.getResourceAsStream("/assets/omix/ai/reference/" + document)) {
                assertNotNull(stream, document);
                assertEquals(source, new String(stream.readAllBytes(), StandardCharsets.UTF_8), document);
            }
            assertTrue(reference.contains(source), "Entire document must reach Agent: " + document);
        }
        assertSame(reference, AiClientReference.promptContext(), "Reference should be cached");
    }

    @Test
    void injectsReferenceOnlyIntoAgentAndPreservesHistory() {
        var history = List.of(AiMessage.user("earlier question"), AiMessage.assistant("earlier answer", "", List.of()));
        var tools = new AiToolSnapshot(new JsonArray(), "Current tool context");
        JsonArray agent = OpenAiCompatibleProvider.buildMessages(null, "new question", AiChatMode.AGENT, history, tools);
        assertEquals(4, agent.size());
        assertEquals("system", agent.get(0).getAsJsonObject().get("role").getAsString());
        String system = agent.get(0).getAsJsonObject().get("content").getAsString();
        assertTrue(system.contains(AiClientReference.promptContext()));
        assertTrue(system.contains("Current tool context"));
        assertEquals(history.get(0).toJson(), agent.get(1));
        assertEquals(AiMessage.user("new question").toJson(), agent.get(3));

        JsonArray chat = OpenAiCompatibleProvider.buildMessages(null, "new question", AiChatMode.CHAT, history, tools);
        assertEquals(3, chat.size());
        assertEquals(history.get(0).toJson(), chat.get(0));
        assertEquals(history.get(1).toJson(), chat.get(1));
        assertEquals(AiMessage.user("new question").toJson(), chat.get(2));
    }

    @Test
    void documentsRegisteredCommandEntrypointsAndSwitchOptions() throws Exception {
        Path commands = Path.of("src/main/java/cn/omix/command");
        String documentation = Files.readString(Path.of("docs/commands.md"));
        String normalized = documentation.toLowerCase(java.util.Locale.ROOT).replaceAll("[-_]", "");
        var registrations = java.util.regex.Pattern.compile("new (\\w+Command)\\(")
                .matcher(Files.readString(commands.resolve("CommandManager.java")));
        while (registrations.find()) {
            String name = registrations.group(1);
            String source = Files.readString(commands.resolve("impl/" + name + ".java"));
            var usage = java.util.regex.Pattern.compile("super\\(\\s*\"([^\"]+)\"").matcher(source);
            if (usage.find()) {
                var roots = java.util.regex.Pattern.compile("\\.([a-zA-Z]+)").matcher(usage.group(1));
                while (roots.find()) {
                    assertTrue(documentation.toLowerCase(java.util.Locale.ROOT).contains("." + roots.group(1).toLowerCase(java.util.Locale.ROOT)), name);
                }
            }
            // These string switch labels dispatch user-facing subcommands/options.
            var cases = java.util.regex.Pattern.compile("case ((?:\"[^\"]+\"\\s*,?\\s*)+) ->").matcher(source);
            while (cases.find()) {
                var keys = java.util.regex.Pattern.compile("\"([^\"]+)\"").matcher(cases.group(1));
                while (keys.find()) {
                    String key = keys.group(1).toLowerCase(java.util.Locale.ROOT).replaceAll("[-_]", "");
                    assertTrue(normalized.contains(key), name + ": missing option " + keys.group(1));
                }
            }
        }
    }

    @Test
    void documentsEveryRegisteredToolAndEachParameterInItsOwnSection() throws Exception {
        String document = Files.readString(Path.of("docs/ai-tools.md"));
        var snapshot = MinecraftCommandToolExecutor.buildSnapshot(List.of("/help"), List.of(".toggle"));
        for (var definition : snapshot.definitions()) {
            JsonObject function = definition.getAsJsonObject().getAsJsonObject("function");
            String name = function.get("name").getAsString();
            String heading = "## " + name + "\n";
            int start = document.indexOf(heading);
            assertTrue(start >= 0, "Missing tool description: " + name);
            int end = document.indexOf("\n## ", start + heading.length());
            String section = document.substring(start, end < 0 ? document.length() : end);
            for (String parameter : function.getAsJsonObject("parameters").getAsJsonObject("properties").keySet()) {
                assertTrue(section.contains("`" + parameter + "`"), name + ": missing parameter " + parameter);
            }
        }
    }
}
