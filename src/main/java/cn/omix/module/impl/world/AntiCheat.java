package cn.omix.module.impl.world;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.TickEvent;
import cn.omix.event.impl.WorldEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.util.Util;
import cn.omix.util.world.adjustdetect.AdjustHackerDetector;
import net.minecraft.network.packet.Packet;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.util.UUID;

/** Minecraft 1.21 adapter for Adjust's HackerDetector and its checks. */
public final class AntiCheat extends Module {
    private final BoolValue showAllDetections = new BoolValue("Show All Detections", true);
    private final BoolValue ignoreFriendly = new BoolValue("Ignore Friendly", true);
    private final AdjustHackerDetector detector = new AdjustHackerDetector();

    public AntiCheat() { super("AntiCheat", Category.World); }
    @Override public void onEnable() { detector.clear(); }
    @Override public void onDisable() { detector.clear(); }
    @EventTarget public void onWorld(WorldEvent event) { detector.clear(); }

    /** Called on the client thread after a server packet has been applied. */
    public void observePacket(Packet<?> packet) {
        if (isNativeBehaviorActive()) detector.enqueue(packet, false);
    }

    @EventTarget public void onTick(TickEvent event) {
        if (!isNativeBehaviorActive() || mc.world == null || mc.player == null || mc.isInSingleplayer()) {
            detector.clear();
            return;
        }
        detector.tick(mc, player -> !ignoreFriendly.getValue()
                        || !instance.getFriendManager().isFriend(player.getGameProfile().name())
                        && !mc.player.isTeammate(player),
                (player, check, count, threshold, cheater) -> {
                    if (!showAllDetections.getValue() && !cheater) return;
                    Text message = Text.empty()
                            .append(Text.literal("Anti-Cheat ").formatted(Formatting.BOLD))
                            .append(Text.literal(player.getGameProfile().name()).formatted(cheater ? Formatting.RED : Formatting.WHITE))
                            .append(Text.literal(" failed check ").formatted(Formatting.GRAY))
                            .append(Text.literal(check).formatted(Formatting.WHITE))
                            .append(Text.literal(" x" + count + " (" + threshold + ")").formatted(Formatting.GRAY));
                    Util.logToChat(message);
                });
    }

    /** Kept for AutoL's Only Cheaters setting. Reaching an Adjust threshold maps to 100. */
    public int getSuspicionScore(UUID player) {
        return isNativeBehaviorActive() ? detector.getSuspicionScore(player) : 0;
    }
}
