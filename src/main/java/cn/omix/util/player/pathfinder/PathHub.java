package cn.omix.util.player.pathfinder;

import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;

public final class PathHub {
    private final Vec3d location;
    private final PathHub parent;
    private final double squaredDistance;
    private final double currentCost;
    private final double maxCost;
    private final long sequence;

    public PathHub(Vec3d location, PathHub parent, double squaredDistance,
                   double currentCost, double maxCost, long sequence) {
        this.location = location;
        this.parent = parent;
        this.squaredDistance = squaredDistance;
        this.currentCost = currentCost;
        this.maxCost = maxCost;
        this.sequence = sequence;
    }

    public Vec3d getLocation() {
        return location;
    }

    public ArrayList<Vec3d> getPathway() {
        ArrayList<Vec3d> pathway = new ArrayList<>();
        for (PathHub hub = this; hub != null; hub = hub.parent) {
            pathway.add(hub.location);
        }
        Collections.reverse(pathway);
        return pathway;
    }

    public double getSquaredDistance() {
        return squaredDistance;
    }

    public double getCurrentCost() {
        return currentCost;
    }

    public double getMaxCost() {
        return maxCost;
    }

    public long getSequence() {
        return sequence;
    }
}
