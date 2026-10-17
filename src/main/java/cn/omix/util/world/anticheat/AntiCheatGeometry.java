package cn.omix.util.world.anticheat;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.function.Predicate;

public final class AntiCheatGeometry {
    private AntiCheatGeometry() {}

    /** A bed's other half is part of the target, not a hole in its surrounding defenses. */
    public static boolean enclosed(BlockPos pos, BlockPos partner, Predicate<BlockPos> covered) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.offset(direction);
            if (!neighbor.equals(partner) && !covered.test(neighbor)) return false;
        }
        return true;
    }
}
