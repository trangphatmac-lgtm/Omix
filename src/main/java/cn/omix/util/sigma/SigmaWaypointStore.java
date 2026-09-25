package cn.omix.util.sigma;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** World/dimension-isolated JSON storage with atomic replacement. No game or graphics dependency. */
public final class SigmaWaypointStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private SigmaWaypointStore() {}

    public static String key(String server, String dimension) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest((server + "\n" + dimension).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    public static List<SigmaWaypoint> read(Path file) throws IOException {
        if (!Files.exists(file)) return List.of();
        if (Files.size(file) > 2_000_000) throw new IOException("Waypoint file exceeds size limit");
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray array = root.getAsJsonArray("waypoints");
            if (array == null) return List.of();
            List<SigmaWaypoint> result = new ArrayList<>();
            Set<UUID> ids = new HashSet<>();
            for (JsonElement element : array) {
                if (result.size() == 4096) break;
                try {
                    JsonObject point = element.getAsJsonObject();
                    UUID id = point.has("id") ? UUID.fromString(point.get("id").getAsString()) : UUID.randomUUID();
                    SigmaWaypoint waypoint = new SigmaWaypoint(id, point.get("name").getAsString(), point.get("x").getAsInt(),
                            point.has("y") ? point.get("y").getAsDouble() : 64, point.get("z").getAsInt(), point.get("color").getAsInt(),
                            !point.has("surface") || point.get("surface").getAsBoolean());
                    if (ids.add(id)) result.add(waypoint);
                } catch (RuntimeException ignored) { /* A malformed row must not hide the other markers. */ }
            }
            return List.copyOf(result);
        } catch (JsonParseException | IllegalStateException error) { throw new IOException("Invalid waypoint JSON", error); }
    }

    public static void write(Path file, List<SigmaWaypoint> points) throws IOException {
        if (points.size() > 4096) throw new IOException("Too many waypoints");
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.add("waypoints", GSON.toJsonTree(points));
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), "waypoints-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(root), StandardCharsets.UTF_8);
            try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
