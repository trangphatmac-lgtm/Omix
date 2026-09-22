package cn.omix.util.script;

import com.google.gson.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public final class ScriptScreenshot {
    private ScriptScreenshot() {}
    public static CompletableFuture<JsonElement> capture(Path directory) {
        var result = new CompletableFuture<JsonElement>();
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> {
            if (result.isCancelled()) return;
            try {
                ScreenshotRecorder.takeScreenshot(mc.getFramebuffer(), image -> {
                    Thread.ofVirtual().name("Omix-Script-Screenshot").start(() -> {
                        try (image) {
                            if (result.isCancelled()) return;
                            Files.createDirectories(directory);
                            Path output = directory.resolve(UUID.randomUUID() + ".png"); image.writeTo(output);
                            if (Files.size(output) > 8 * 1024 * 1024) { Files.delete(output); throw new IllegalStateException("Screenshot exceeds 8 MiB; reduce the game window size."); }
                            JsonObject value = new JsonObject(); value.addProperty("path", output.toAbsolutePath().toString());
                            value.addProperty("mimeType", "image/png"); value.addProperty("data", Base64.getEncoder().encodeToString(Files.readAllBytes(output)));
                            try (var files = Files.list(directory)) {
                                var paths = files.filter(file -> file.toString().endsWith(".png")).sorted(Comparator.comparingLong(file -> file.toFile().lastModified())).toList();
                                for (int i = 0; i < paths.size() - 20; i++) Files.deleteIfExists(paths.get(i));
                            }
                            result.complete(value);
                        } catch (Exception error) { result.completeExceptionally(error); }
                    });
                });
            } catch (Exception error) { result.completeExceptionally(error); }
        });
        return result.orTimeout(15, TimeUnit.SECONDS);
    }
}
