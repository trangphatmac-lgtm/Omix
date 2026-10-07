package cn.omix.util.player.bed;

import net.minecraft.util.math.Vec3d;

/** Main-thread spawn protection from Samsara's Whitelist, independent of BedAura's toggle. */
public final class BedAuraWhitelist {
    public static final BedAuraWhitelist INSTANCE = new BedAuraWhitelist();
    private static final String START_MESSAGE = "Protect your bed and destroy the enemy beds.";
    private static final double PROTECTION_DISTANCE_SQUARED = 600;

    private boolean awaitingSpawn;
    private Vec3d spawn;

    public void onGameMessage(String message) {
        if (message != null && message.contains(START_MESSAGE)) awaitingSpawn = true;
    }

    /** Receives the applied position so relative teleport coordinates have already been resolved. */
    public void onPositionApplied(Vec3d position) {
        if (!awaitingSpawn || position == null) return;
        spawn = position;
        awaitingSpawn = false;
    }

    public boolean isProtected(Vec3d playerPosition) {
        return spawn != null && playerPosition != null
                && playerPosition.squaredDistanceTo(spawn) < PROTECTION_DISTANCE_SQUARED;
    }

    public void clear() {
        awaitingSpawn = false;
        spawn = null;
    }
}
