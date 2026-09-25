package cn.omix.util.sigma;

import java.io.*;
import java.nio.file.*;
import java.util.Arrays;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/** A 16x16-chunk atlas, with a versioned, bounded disk representation. */
public final class SigmaMapRegion {
    public static final int SIZE = 256;
    public static final int UNKNOWN = -7687425;
    private static final int MAGIC = 0x534a4d31;
    private final int[] pixels;
    private final boolean[] known;
    private long revision;

    public SigmaMapRegion() {
        pixels = new int[SIZE * SIZE]; known = new boolean[256]; Arrays.fill(pixels, UNKNOWN);
    }
    private SigmaMapRegion(int[] pixels, boolean[] known, long revision) { this.pixels = pixels; this.known = known; this.revision = revision; }
    public long revision() { return revision; }
    public int color(int x, int z) { return pixels[z * SIZE + x]; }
    public boolean known(int x, int z) { return known[z * 16 + x]; }
    public SigmaMapRegion snapshot() { return new SigmaMapRegion(pixels.clone(), known.clone(), revision); }

    public void putChunk(int chunkX, int chunkZ, int[] colors) {
        if (colors.length != 256 || chunkX < 0 || chunkX > 15 || chunkZ < 0 || chunkZ > 15) throw new IllegalArgumentException("Invalid map tile");
        boolean changed = !known(chunkX, chunkZ);
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            int destination = (chunkZ * 16 + z) * SIZE + chunkX * 16 + x;
            int color = colors[z * 16 + x];
            changed |= pixels[destination] != color;
            pixels[destination] = color;
        }
        known[chunkZ * 16 + chunkX] = true;
        if (changed) revision++;
    }

    public void mergeMissing(SigmaMapRegion other) {
        int[] tile = new int[256];
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            if (known(x, z) || !other.known(x, z)) continue;
            for (int dz = 0; dz < 16; dz++) for (int dx = 0; dx < 16; dx++) tile[dz * 16 + dx] = other.color(x * 16 + dx, z * 16 + dz);
            putChunk(x, z, tile);
        }
    }

    public static SigmaMapRegion read(Path file) throws IOException {
        SigmaMapRegion region = new SigmaMapRegion();
        if (!Files.exists(file)) return region;
        try (var input = new DataInputStream(new InflaterInputStream(Files.newInputStream(file)))) {
            if (input.readInt() != MAGIC) throw new IOException("Unknown Jello map format");
            for (int i = 0; i < region.known.length; i++) region.known[i] = input.readBoolean();
            for (int i = 0; i < region.pixels.length; i++) region.pixels[i] = input.readInt();
            if (input.read() != -1) throw new IOException("Trailing map data");
        }
        return region;
    }

    public void write(Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), "region-", ".tmp");
        try {
            try (var output = new DataOutputStream(new DeflaterOutputStream(Files.newOutputStream(temporary)))) {
                output.writeInt(MAGIC);
                for (boolean present : known) output.writeBoolean(present);
                for (int color : pixels) output.writeInt(color);
            }
            try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
