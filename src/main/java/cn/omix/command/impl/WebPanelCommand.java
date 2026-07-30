package cn.omix.command.impl;

import cn.omix.command.Command;
import cn.omix.util.Util;
import im.webui.WebUiRuntime;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.net.URI;

public final class WebPanelCommand extends Command {

    public WebPanelCommand() {
        super(".webpanel", "webpanel");
    }

    @Override
    public void execute(String[] arguments) {
        try {
            String url = WebUiRuntime.getInstance().getWebPanelUrl();
            MutableText link = Text.literal(url).styled(style -> style
                    .withColor(Formatting.AQUA)
                    .withUnderline(true)
                    .withClickEvent(new ClickEvent.OpenUrl(URI.create(url))));
            Util.logToChat(Text.empty()
                    .append(Text.literal("WebPanel: ").formatted(Formatting.WHITE))
                    .append(link));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            Util.logToChat("&cWebPanel is unavailable: " + exception.getMessage());
        }
    }
}
