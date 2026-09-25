package cn.omix.util.sigma;

import java.util.ArrayList;
import java.util.List;

/** Geometry independent of Minecraft and the graphics context. */
public final class SigmaGeometry {
    private static final Point3[][] WAYPOINT_FACES = buildWaypointFaces();
    private static final Point3[] CIRCLE = buildCircle();
    public record Point3(double x, double y, double z) {}
    private SigmaGeometry() {}

    /** Same translate-Z, rotate-X(-37), rotate-X(180 for the lower half), rotate-Y chain as ClickTP.rotationThingy. */
    private static Point3[][] buildWaypointFaces() {
        Point3[][] faces = new Point3[8][3];
        double tilt = Math.toRadians(-37);
        for (int half = 0; half < 2; half++) for (int face = 0; face < 4; face++) for (int vertex = 0; vertex < 3; vertex++) {
            double x = vertex == 0 ? 0 : vertex == 1 ? -.3 : .3;
            double sourceY = vertex == 0 ? .4985 : 0;
            double y = sourceY * Math.cos(tilt), z = .3 + sourceY * Math.sin(tilt);
            if (half == 1) { y = -y; z = -z; }
            double rotation = face * Math.PI / 2;
            faces[half * 4 + face][vertex] = new Point3(x * Math.cos(rotation) + z * Math.sin(rotation), y, -x * Math.sin(rotation) + z * Math.cos(rotation));
        }
        return faces;
    }

    public static Point3 waypointVertex(int half, int face, int vertex) { return WAYPOINT_FACES[half * 4 + face][vertex]; }
    private static Point3[] buildCircle() {
        Point3[] result = new Point3[361];
        for (int i = 0; i <= 360; i++) { double angle = Math.PI * 2 * i / 360; result[i] = new Point3(Math.cos(angle), Math.sin(angle), 0); }
        return result;
    }
    public static Point3 circleVertex(int index) { return CIRCLE[index]; }

    /** Cube vertices use bit 0 = x, bit 1 = y, bit 2 = z. */
    public static List<int[]> silhouetteEdges(double minX, double minY, double minZ,
                                               double maxX, double maxY, double maxZ,
                                               double cameraX, double cameraY, double cameraZ) {
        boolean[][] front = {{cameraX < minX, cameraX > maxX}, {cameraY < minY, cameraY > maxY}, {cameraZ < minZ, cameraZ > maxZ}};
        List<int[]> result = new ArrayList<>(6);
        for (int axis = 0; axis < 3; axis++) {
            int a = (axis + 1) % 3, b = (axis + 2) % 3;
            for (int sideA = 0; sideA < 2; sideA++) {
                for (int sideB = 0; sideB < 2; sideB++) {
                    if (front[a][sideA] == front[b][sideB]) continue;
                    int start = sideA << a | sideB << b;
                    result.add(new int[]{start, start | 1 << axis});
                }
            }
        }
        return result;
    }

    public static float magnification(double squaredDistance, float minimum) {
        return (float) Math.max(minimum, Math.sqrt(Math.max(0, squaredDistance) / 30));
    }

    public static float wrapDegrees(float yaw) {
        return (yaw % 360 + 360) % 360;
    }
}
