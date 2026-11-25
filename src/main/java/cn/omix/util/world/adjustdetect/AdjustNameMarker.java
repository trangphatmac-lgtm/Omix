package cn.omix.util.world.adjustdetect;

import cn.omix.Client;
import cn.omix.module.impl.world.AntiCheat;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;

/** Display-only marker for players flagged by the active Adjust detector. */
public final class AdjustNameMarker {
    private AdjustNameMarker() {}

    public static boolean marked(UUID player) {
        if (player == null || Client.instance == null || Client.instance.getModuleManager() == null) return false;
        AntiCheat module = Client.instance.getModuleManager().getModule(AntiCheat.class);
        return module != null && module.shouldMarkName(player);
    }

    public static Text prefix(UUID player, Text name) {
        if (name == null || !marked(player)) return name;
        return Text.empty().append(Text.literal("! ").formatted(Formatting.RED, Formatting.BOLD)).append(name);
    }
}
