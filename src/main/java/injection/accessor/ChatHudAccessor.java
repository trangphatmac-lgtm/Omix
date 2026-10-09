package injection.accessor;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(ChatHud.class)
public interface ChatHudAccessor {
    @Accessor("messages")
    List<ChatHudLine> omix$getMessages();

    @Accessor("visibleMessages") List<ChatHudLine.Visible> omix$getVisibleMessages();
    @Accessor("scrolledLines") int omix$getScrolledLines();
    @Accessor("scrolledLines") void omix$setScrolledLines(int lines);
    @Accessor("hasUnreadNewMessages") boolean omix$getHasUnreadNewMessages();
    @Accessor("hasUnreadNewMessages") void omix$setHasUnreadNewMessages(boolean unread);
    @Invoker("getVisibleLineCount") int omix$getVisibleLineCount();

    @Invoker("refresh")
    void omix$refresh();
}
