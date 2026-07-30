package ai.backend;

import com.mojang.blaze3d.platform.GLX;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.util.Window;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

record AiGameContext(
        String username,
        String connectionMode,
        String serverAddress,
        String fps,
        String position,
        String blockPosition,
        String dimension,
        String facing,
        String biome,
        String gameVersion,
        String javaVersion,
        String operatingSystem,
        String cpu,
        String display,
        String gpu,
        String graphicsApi,
        String memory
) {
    private static final String UNAVAILABLE = "unavailable";
    private static final long MEBIBYTE = 1024L * 1024L;

    AiGameContext {
        username = clean(username, "Minecraft player");
        connectionMode = clean(connectionMode, UNAVAILABLE);
        serverAddress = clean(serverAddress, UNAVAILABLE);
        fps = clean(fps, UNAVAILABLE);
        position = clean(position, UNAVAILABLE);
        blockPosition = clean(blockPosition, UNAVAILABLE);
        dimension = clean(dimension, UNAVAILABLE);
        facing = clean(facing, UNAVAILABLE);
        biome = clean(biome, UNAVAILABLE);
        gameVersion = clean(gameVersion, UNAVAILABLE);
        javaVersion = clean(javaVersion, UNAVAILABLE);
        operatingSystem = clean(operatingSystem, UNAVAILABLE);
        cpu = clean(cpu, UNAVAILABLE);
        display = clean(display, UNAVAILABLE);
        gpu = clean(gpu, UNAVAILABLE);
        graphicsApi = clean(graphicsApi, UNAVAILABLE);
        memory = clean(memory, UNAVAILABLE);
    }

    static CompletableFuture<AiGameContext> capture(String username) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return CompletableFuture.completedFuture(unavailable(username));
        }
        if (client.isOnThread()) {
            return CompletableFuture.completedFuture(captureOnClientThread(client, username));
        }
        return client.submit(() -> captureOnClientThread(client, username));
    }

    String promptContext() {
        return """
                Current game context (captured when this request started):
                - Player name: %s
                - Connection mode: %s
                - Server address: %s
                - FPS: %s
                - Position (XYZ): %s
                - Block position: %s
                - Dimension: %s
                - Facing: %s
                - Biome: %s
                - Minecraft version: %s
                - Java: %s
                - Operating system: %s
                - CPU: %s
                - Display: %s
                - GPU: %s
                - Graphics API: %s
                - Memory: %s

                These values are a snapshot \
                and may change while you respond or after you use a game-control tool.
                """.formatted(
                username,
                connectionMode,
                serverAddress,
                fps,
                position,
                blockPosition,
                dimension,
                facing,
                biome,
                gameVersion,
                javaVersion,
                operatingSystem,
                cpu,
                display,
                gpu,
                graphicsApi,
                memory
        ).trim();
    }

    private static AiGameContext captureOnClientThread(MinecraftClient client, String username) {
        String connectionMode = "Not connected to a world";
        String serverAddress = "N/A";
        if (client.isInSingleplayer()) {
            connectionMode = "Singleplayer (integrated server)";
            serverAddress = "N/A (local integrated server)";
        } else {
            ServerInfo server = client.getCurrentServerEntry();
            if (server != null) {
                connectionMode = "Multiplayer server";
                serverAddress = server.address;
            }
        }

        String position = UNAVAILABLE;
        String blockPosition = UNAVAILABLE;
        String dimension = UNAVAILABLE;
        String facing = UNAVAILABLE;
        String biome = UNAVAILABLE;
        if (client.player != null) {
            position = String.format(
                    Locale.ROOT,
                    "%.3f / %.5f / %.3f",
                    client.player.getX(),
                    client.player.getY(),
                    client.player.getZ()
            );
            BlockPos blockPos = client.player.getBlockPos();
            blockPosition = blockPos.getX() + " / " + blockPos.getY() + " / " + blockPos.getZ();
            facing = String.format(
                    Locale.ROOT,
                    "%s (yaw %.1f / pitch %.1f)",
                    client.player.getHorizontalFacing().asString(),
                    client.player.getYaw(),
                    client.player.getPitch()
            );
            if (client.world != null) {
                dimension = client.world.getRegistryKey().getValue().toString();
                RegistryEntry<Biome> biomeEntry = client.world.getBiome(blockPos);
                biome = biomeEntry.getIdAsString();
            }
        }

        Window window = client.getWindow();
        String display = window == null
                ? UNAVAILABLE
                : window.getFramebufferWidth() + "x" + window.getFramebufferHeight();

        String gpu = UNAVAILABLE;
        String graphicsApi = UNAVAILABLE;
        GpuDevice device = RenderSystem.tryGetDevice();
        if (device != null) {
            display += " (" + clean(device.getVendor(), "unknown vendor") + ")";
            gpu = device.getRenderer();
            graphicsApi = clean(device.getBackendName(), "unknown backend")
                    + " " + clean(device.getVersion(), "unknown version");
        }

        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        String memory = (usedMemory / MEBIBYTE) + " MiB used / "
                + (runtime.maxMemory() / MEBIBYTE) + " MiB max";

        return new AiGameContext(
                username,
                connectionMode,
                serverAddress,
                Integer.toString(client.getCurrentFps()),
                position,
                blockPosition,
                dimension,
                facing,
                biome,
                client.getGameVersion(),
                System.getProperty("java.version"),
                System.getProperty("os.name") + " " + System.getProperty("os.version")
                        + " (" + System.getProperty("os.arch") + ")",
                GLX._getCpuInfo(),
                display,
                gpu,
                graphicsApi,
                memory
        );
    }

    static AiGameContext unavailable(String username) {
        return new AiGameContext(
                username,
                "Minecraft client unavailable",
                "N/A",
                UNAVAILABLE,
                UNAVAILABLE,
                UNAVAILABLE,
                UNAVAILABLE,
                UNAVAILABLE,
                UNAVAILABLE,
                UNAVAILABLE,
                System.getProperty("java.version"),
                System.getProperty("os.name") + " " + System.getProperty("os.version")
                        + " (" + System.getProperty("os.arch") + ")",
                UNAVAILABLE,
                UNAVAILABLE,
                UNAVAILABLE,
                UNAVAILABLE,
                UNAVAILABLE
        );
    }

    private static String clean(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.replace('\r', ' ').replace('\n', ' ').trim();
    }
}
