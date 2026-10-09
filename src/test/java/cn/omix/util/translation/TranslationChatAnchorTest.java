package cn.omix.util.translation;

import injection.accessor.ChatHudAccessor;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TranslationChatAnchorTest {
    static final class Hud implements ChatHudAccessor {
        List<ChatHudLine> messages = new ArrayList<>(); List<ChatHudLine.Visible> lines = new ArrayList<>();
        int scroll; boolean unread;
        public List<ChatHudLine> omix$getMessages() { return messages; }
        public List<ChatHudLine.Visible> omix$getVisibleMessages() { return lines; }
        public int omix$getScrolledLines() { return scroll; }
        public void omix$setScrolledLines(int value) { scroll = value; }
        public boolean omix$getHasUnreadNewMessages() { return unread; }
        public void omix$setHasUnreadNewMessages(boolean value) { unread = value; }
        public int omix$getVisibleLineCount() { return 2; }
        public void omix$refresh() {}
        void wrap(int... sizes) {
            lines.clear();
            for (int size : sizes) for (int i = 0; i < size; i++) lines.add(new ChatHudLine.Visible(1, Text.literal("display").asOrderedText(), null, i == 0));
        }
    }
    @Test void keepsOriginalMessageAndOffsetAfterNewerMessagesExpandOrContract() {
        var hud = new Hud(); Text first = Text.literal("first"), older = Text.literal("older");
        hud.messages.add(new ChatHudLine(1, first, null, null)); hud.messages.add(new ChatHudLine(1, older, null, null));
        hud.wrap(2, 3); hud.scroll = 3; hud.unread = true;
        var anchor = TranslationChatAnchor.capture(hud); hud.wrap(4, 3); anchor.restore(hud);
        assertEquals(5, hud.scroll); assertTrue(hud.unread);
        assertSame(older, hud.messages.get(1).content());
        hud.wrap(1, 2); anchor.restore(hud); assertEquals(1, hud.scroll);
    }
    @Test void bottomOfChatRemainsAtBottomAndDoesNotMarkTranslationsUnread() {
        var hud = new Hud(); hud.wrap(2, 3); hud.scroll = 0;
        var anchor = TranslationChatAnchor.capture(hud); hud.wrap(4, 5); anchor.restore(hud);
        assertEquals(0, hud.scroll); assertFalse(hud.unread);
    }
}
