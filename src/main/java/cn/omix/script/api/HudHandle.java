package cn.omix.script.api;

import cn.omix.util.script.ScriptHud;
import net.minecraft.client.gui.DrawContext;
import java.util.function.BiConsumer;

public final class HudHandle extends ModuleHandle {
    public HudHandle(ScriptContext context, String id, String name, BiConsumer<DrawContext, HudHandle> draw) {
        super(context, id); module = new ScriptHud(this.id, name, this, draw);
    }
    public float x() { return ((ScriptHud) module).x(); }
    public float y() { return ((ScriptHud) module).y(); }
    public void size(float width, float height) {
        if (!Float.isFinite(width) || !Float.isFinite(height) || width < 0 || height < 0) throw new IllegalArgumentException("Invalid HUD size");
        ((ScriptHud) module).width = width; ((ScriptHud) module).height = height;
    }
    public void position(float x, float y) { ((ScriptHud) module).percentX = x; ((ScriptHud) module).percentY = y; }
}
