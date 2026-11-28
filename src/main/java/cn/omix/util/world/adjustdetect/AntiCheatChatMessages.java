package cn.omix.util.world.adjustdetect;

import cn.omix.util.Util;
import cn.omix.util.translation.TranslationChatAnchor;
import injection.accessor.ChatHudAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Keeps only the latest AntiCheat chat entry for each player. Called on the client thread. */
public final class AntiCheatChatMessages {
    private final Map<UUID, Text> latestByPlayer = new HashMap<>();

    public void show(MinecraftClient client, UUID player, Text message) {
        if (client.player == null || client.inGameHud == null) return;

        var chatHud = client.inGameHud.getChatHud();
        ChatHudAccessor hud = (ChatHudAccessor) chatHud;
        var messages = hud.omix$getMessages();
        // ChatHud evicts old entries; do not retain messages that are no longer visible in its history.
        Set<Text> retained = Collections.newSetFromMap(new IdentityHashMap<>());
        messages.forEach(line -> retained.add(line.content()));
        latestByPlayer.values().removeIf(previous -> !retained.contains(previous));

        Text previous = latestByPlayer.get(player);
        TranslationChatAnchor anchor = previous == null ? null : TranslationChatAnchor.capture(hud);
        boolean removed = previous != null && messages.removeIf(line -> line.content() == previous);

        Util.logToChat(message);
        if (!messages.isEmpty()) latestByPlayer.put(player, messages.getFirst().content());

        if (removed) {
            hud.omix$setScrolledLines(0);
            hud.omix$refresh();
            anchor.restore(hud);
        }
    }
}
