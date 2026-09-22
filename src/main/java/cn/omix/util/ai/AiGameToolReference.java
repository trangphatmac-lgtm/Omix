package cn.omix.util.ai;

import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Build-time export of the exact game tool declarations used by Harness and the bridge. */
public final class AiGameToolReference {
    private AiGameToolReference() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected output directory");
        Path directory = Path.of(args[0]);
        Files.createDirectories(directory);
        var snapshot = MinecraftCommandToolExecutor.buildSnapshot(List.of(), List.of());
        write(directory.resolve("game-tools.json"), new GsonBuilder().setPrettyPrinting()
                .disableHtmlEscaping().create().toJson(snapshot.definitions()) + "\n");
    }

    private static void write(Path path, String content) throws Exception {
        if (!Files.exists(path) || !Files.readString(path, StandardCharsets.UTF_8).equals(content))
            Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
