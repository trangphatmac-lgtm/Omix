package cn.omix.util.world.anticheat.raven;

/** Immutable vector with the field/accessor layout used by the source formulas. */
public final class RavenVec3 {
    public final double x, y, z;
    public RavenVec3(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
    public RavenVec3 offset(double x, double y, double z) { return new RavenVec3(this.x + x, this.y + y, this.z + z); }
    public double distanceTo(RavenVec3 other) {
        double dx = x - other.x, dy = y - other.y, dz = z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
