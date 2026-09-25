package cn.omix.util.sigma;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SigmaStorageTest {
    @TempDir Path temporary;

    @Test void waypointsRoundTripWithoutLosingIdentityOrNegativeCoordinates() throws Exception {
        Path path = temporary.resolve("world/waypoints.json");
        var points = List.of(SigmaWaypoint.surface("家 / café", -257, -1, -11491585),
                new SigmaWaypoint(UUID.randomUUID(), "Unspawn", 9, -48.5, 0, SigmaColors.UNSPAWN, false));
        SigmaWaypointStore.write(path, points);
        assertEquals(points, SigmaWaypointStore.read(path));
        SigmaWaypointStore.write(path, List.of(points.getFirst()));
        assertEquals(List.of(points.getFirst()), SigmaWaypointStore.read(path));
        try (var files = Files.list(path.getParent())) { assertEquals(1, files.count()); }
    }

    @Test void sourceWaypointJsonImportsAndMalformedRowsAreIsolated() throws Exception {
        Path path = temporary.resolve("waypoints.json");
        Files.writeString(path, "{\"waypoints\":[{\"name\":\"Home\",\"x\":-16,\"z\":9,\"color\":-2565928},{\"name\":\"broken\"}]}");
        var points = SigmaWaypointStore.read(path);
        assertEquals(1, points.size()); assertTrue(points.getFirst().surface()); assertEquals(64, points.getFirst().y());
        assertThrows(IllegalArgumentException.class, () -> SigmaWaypoint.surface("", 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> SigmaWaypoint.surface("bad", Integer.MIN_VALUE, 0, 0));
    }

    @Test void worldAndDimensionKeysAreDistinctAndSafeFilenames() {
        String first = SigmaWaypointStore.key("server/../../a:25565", "minecraft:overworld");
        assertTrue(first.matches("[0-9a-f]{64}"));
        assertNotEquals(first, SigmaWaypointStore.key("server/../../a:25565", "minecraft:the_nether"));
        assertNotEquals(first, SigmaWaypointStore.key("local/../../a:25565", "minecraft:overworld"));
        assertEquals(first, SigmaWaypointStore.key("server/../../a:25565", "minecraft:overworld"));
    }

    @Test void regionCodecRetainsOrientationAndKnownTiles() throws Exception {
        SigmaMapRegion region = new SigmaMapRegion();
        int[] pixels = new int[256]; Arrays.fill(pixels, 0xff225599);
        pixels[0] = 0xffff0000; pixels[15] = 0xff00ff00; pixels[240] = 0xff0000ff;
        region.putChunk(15, 0, pixels);
        long revision = region.revision(); region.putChunk(15, 0, pixels);
        assertEquals(revision, region.revision(), "Unchanged terrain must not trigger another texture upload");
        Path file = temporary.resolve("-1_0.jmap"); region.write(file);
        SigmaMapRegion restored = SigmaMapRegion.read(file);
        assertTrue(restored.known(15, 0)); assertFalse(restored.known(0, 0));
        assertEquals(0xffff0000, restored.color(240, 0));
        assertEquals(0xff00ff00, restored.color(255, 0));
        assertEquals(0xff0000ff, restored.color(240, 15));
        assertEquals(SigmaMapRegion.UNKNOWN, restored.color(0, 0));
        Arrays.fill(pixels, 0xffffff00);
        restored.putChunk(15, 0, pixels); restored.mergeMissing(region);
        assertEquals(0xffffff00, restored.color(240, 0), "Disk data must not overwrite a newly sampled tile");
    }

    @Test void truncatedRegionsFailRatherThanBecomingValidEmptyData() throws Exception {
        Path file = temporary.resolve("broken.jmap"); Files.write(file, new byte[]{1, 2, 3});
        assertThrows(java.io.IOException.class, () -> SigmaMapRegion.read(file));
    }
}
