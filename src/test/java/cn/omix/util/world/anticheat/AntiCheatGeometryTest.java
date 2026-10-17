package cn.omix.util.world.anticheat;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AntiCheatGeometryTest {
    @Test void bedPartnerIsNotMistakenForExposedDefense() {
        BlockPos foot = new BlockPos(0, 64, 0), head = foot.east();
        Set<BlockPos> walls = new HashSet<>();
        for (BlockPos part : new BlockPos[]{foot, head}) {
            for (Direction direction : Direction.values()) walls.add(part.offset(direction));
        }
        walls.remove(foot);
        walls.remove(head);
        assertFalse(AntiCheatGeometry.enclosed(foot, null, walls::contains));
        assertTrue(AntiCheatGeometry.enclosed(foot, head, walls::contains));
        assertTrue(AntiCheatGeometry.enclosed(head, foot, walls::contains));
        walls.remove(head.up());
        assertFalse(AntiCheatGeometry.enclosed(head, foot, walls::contains));
    }

    @Test void normalBlockRequiresCoverageOnAllSixFacesIncludingBottom() {
        BlockPos block = new BlockPos(0, 64, 0);
        Set<BlockPos> walls = new HashSet<>();
        for (Direction direction : Direction.values()) walls.add(block.offset(direction));
        assertTrue(AntiCheatGeometry.enclosed(block, null, walls::contains));
        walls.remove(block.down());
        assertFalse(AntiCheatGeometry.enclosed(block, null, walls::contains));
    }
}
