package im.webui.screen;

import im.webui.WebUiRuntime;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public final class WebUiScreen extends Screen {
    private final Screen parent;
    private final WebScreenType type;

    public WebUiScreen(Screen parent, WebScreenType type) {
        super(Text.literal("Remix WebUI — " + type.routeName()));
        this.parent = parent;
        this.type = type;
    }

    public WebScreenType getType() {
        return type;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        WebUiRuntime.getInstance().render(context);
    }

    @Override
    public void close() {
        WebUiRuntime.getInstance().closeTestScreen();
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
