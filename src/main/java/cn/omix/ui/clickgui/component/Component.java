package cn.omix.ui.clickgui.component;

import cn.omix.module.value.Value;
import cn.omix.ui.clickgui.ModuleButton;
import cn.omix.util.IMinecraft;
import lombok.Getter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;

@Getter
public abstract class Component implements IMinecraft {
    protected final ModuleButton parent;
    private final Value value;
    protected float x, y, width, height;

    public Component(ModuleButton parent, Value value) {
        this.parent = parent;
        this.value = value;
    }

    public void render(DrawContext context, float x, float y, float width, int mouseX, int mouseY, float globalAlpha) {
        this.x = x;
        this.y = y;
        this.width = width;
    }

    public void mouseClicked(double mouseX, double mouseY, int button) {}
    public void mouseReleased(double mouseX, double mouseY, int button) {}
    public void focusLost() {}
    public boolean keyPressed(KeyInput input) { return false; }
    public boolean charTyped(CharInput input) { return false; }

    protected boolean hovered(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}
