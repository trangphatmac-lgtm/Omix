package ai.backend;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** The same UTF-8 reference shipped in /docs, loaded once for Agent requests. */
final class AiClientReference {
    static final List<String> DOCUMENTS = List.of(
            "README.md", "commands.md",
            "modules/combat.md", "modules/exploits.md", "modules/move.md",
            "modules/player.md", "modules/render.md", "modules/world.md", "ai-tools.md"
    );
    private static final String RESOURCE_ROOT = "/assets/omix/ai/reference/";

    private AiClientReference() {
    }

    static String promptContext() {
        return Cached.TEXT;
    }

    private static final class Cached {
        private static final String TEXT = load();
    }

    private static String load() {
        StringBuilder text = new StringBuilder("Omix Client source reference (defaults are not live configuration):\n");
        for (String document : DOCUMENTS) {
            try (InputStream stream = AiClientReference.class.getResourceAsStream(RESOURCE_ROOT + document)) {
                if (stream == null) {
                    throw new IllegalStateException("Missing bundled AI reference: " + document);
                }
                text.append("\n--- docs/").append(document).append(" ---\n")
                        .append(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot read bundled AI reference: " + document, exception);
            }
        }
        return text.toString();
    }
}
