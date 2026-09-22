package cn.omix.util.script;

import cn.omix.ui.hud.Drag;
import cn.omix.event.impl.Render2DEvent;
import cn.omix.script.api.HudHandle;
import net.minecraft.client.gui.DrawContext;
import java.util.function.BiConsumer;

public final class ScriptHud extends Drag {
    private final HudHandle handle;
    private final BiConsumer<DrawContext, HudHandle> draw;
    public ScriptHud(String id, String name, HudHandle handle, BiConsumer<DrawContext, HudHandle> draw) {
        super(name); setId(id); this.handle = handle; this.draw = draw;
        handle.on(Render2DEvent.class, event -> render(event.getContext()));
        handle.on(cn.omix.event.impl.ChatScreenEvent.class, event -> onChatGUI(event.getMouseX(), event.getMouseY(),
                org.lwjgl.glfw.GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS));
    }
    @Override public void onEnable() { handle.activate(); }
    @Override public void onDisable() { handle.deactivate(); }
    @Override public void render(DrawContext context) { if (handle.active()) { updatePos(); handle.invoke("renderHud", () -> draw.accept(context, handle)); } }
    public float x() { return renderX; }
    public float y() { return renderY; }
}
