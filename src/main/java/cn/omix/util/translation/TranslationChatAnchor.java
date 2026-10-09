package cn.omix.util.translation;

import injection.accessor.ChatHudAccessor;
import net.minecraft.text.Text;

/** Keep the same message and intra-message line visible when asynchronous translations reflow chat. */
public record TranslationChatAnchor(Text message, int offset, int oldScroll, boolean unread) {
    public static TranslationChatAnchor capture(ChatHudAccessor hud) {
        int scroll = hud.omix$getScrolledLines(), group = -1, start = 0;
        var lines = hud.omix$getVisibleMessages();
        for (int i = 0; i <= scroll && i < lines.size(); i++) {
            if (lines.get(i).endOfEntry()) { group++; start = i; }
        }
        Text message = scroll > 0 && group >= 0 && group < hud.omix$getMessages().size() ? hud.omix$getMessages().get(group).content() : null;
        return new TranslationChatAnchor(message, scroll - start, scroll, hud.omix$getHasUnreadNewMessages());
    }
    public void restore(ChatHudAccessor hud) {
        int target = oldScroll;
        if (message != null) {
            int index = -1;
            for (int i = 0; i < hud.omix$getMessages().size(); i++) if (hud.omix$getMessages().get(i).content() == message) { index = i; break; }
            int group = -1; var lines = hud.omix$getVisibleMessages();
            for (int i = 0; i < lines.size(); i++) if (lines.get(i).endOfEntry()) {
                if (++group == index) {
                    int end = i + 1;
                    while (end < lines.size() && !lines.get(end).endOfEntry()) end++;
                    target = i + Math.min(offset, end - i - 1); break;
                }
            }
        }
        hud.omix$setScrolledLines(Math.clamp(target, 0, Math.max(0, hud.omix$getVisibleMessages().size() - hud.omix$getVisibleLineCount())));
        hud.omix$setHasUnreadNewMessages(unread);
    }
}
