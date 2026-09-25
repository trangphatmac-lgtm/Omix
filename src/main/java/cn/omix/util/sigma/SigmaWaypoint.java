package cn.omix.util.sigma;

import java.util.UUID;

public record SigmaWaypoint(UUID id, String name, int x, double y, int z, int color, boolean surface) {
    public SigmaWaypoint {
        if (id == null || name == null || name.isBlank() || name.length() > 128 || !Double.isFinite(y)
                || Math.abs((long) x) > 30_000_000 || Math.abs((long) z) > 30_000_000) throw new IllegalArgumentException("Invalid waypoint");
        color |= 0xff000000;
    }

    public static SigmaWaypoint surface(String name, int x, int z, int color) {
        return new SigmaWaypoint(UUID.randomUUID(), name.strip(), x, 64, z, color, true);
    }
}
