package cn.omix.util.world.anticheat.raven;

import java.util.List;

public final class RavenItem {
    public final String name, type;
    public final int meta, count, useTicks;
    public final boolean isBlock;
    private final int efficiency;
    public RavenItem(String name, String type, int count, int useTicks, int efficiency) {
        this.name = name; this.type = type; this.count = count; this.useTicks = useTicks;
        this.isBlock = type.startsWith("Block");
        this.meta = 0; this.efficiency = efficiency;
    }
    public List<Object[]> getEnchantments() { return List.<Object[]>of(new Object[]{"efficiency", efficiency}); }
}
