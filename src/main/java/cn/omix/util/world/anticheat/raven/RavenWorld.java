package cn.omix.util.world.anticheat.raven;

import java.util.List;

public interface RavenWorld {
    final class Block {
        public final String name;
        public Block(String name) { this.name = name; }
    }
    Block getBlockAt(int x, int y, int z);
    List<String> getScoreboard();
    int bottomY();
}
