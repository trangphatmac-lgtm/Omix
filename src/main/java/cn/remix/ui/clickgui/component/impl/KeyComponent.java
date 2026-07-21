package cn.remix.ui.clickgui.component.impl;

import cn.remix.module.value.impl.KeyValue;
import cn.remix.ui.clickgui.ModuleButton;
import cn.remix.ui.clickgui.component.Component;
import cn.remix.util.animation.Easing;
import cn.remix.util.animation.EasingAnimation;
import cn.remix.util.misc.KeyUtil;
import cn.remix.util.render.ColorUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;

public final class KeyComponent extends Component {
    private final EasingAnimation visibleAnimation = new EasingAnimation(Easing.EASE_OUT_CUBIC, 200);
    private boolean binding;

    public KeyComponent(ModuleButton parent, KeyValue value) {
        super(parent, value);
    }

    @Override
    public float getHeight() {
        if (!getValue().isVisible()) focusLost();
        visibleAnimation.run(getValue().isVisible() && parent.isExtended() ? 1.0F : 0.0F);
        return 14.0F * visibleAnimation.getValue().floatValue();
    }

    @Override
    public void render(DrawContext context, float x, float y, float width, int mouseX, int mouseY, float globalAlpha) {
        super.render(context, x, y, width, mouseX, mouseY, globalAlpha);
        float progress = visibleAnimation.getValue().floatValue();
        this.height = 14.0F * progress;
        float finalProgress = progress * globalAlpha;
        if (finalProgress < 0.01F) return;

        KeyValue keyValue = (KeyValue) getValue();
        var font = instance.getFontManager().getFont(16);
        int alpha = MathHelper.clamp((int) (255.0F * finalProgress), 0, 255);
        float textY = y + (14.0F - font.getHeight()) / 2.0F + 1.0F;
        String keyName = binding ? "Press key..." : KeyUtil.getKeyName(keyValue.getValue());

        font.drawString(context, keyValue.getName(), x + 4.0F, textY,
                new Color(204, 204, 204, alpha).getRGB());
        font.drawString(context, keyName, x + width - 4.0F - font.getStringWidth(keyName), textY,
                ColorUtil.applyAlpha(parent.getModulePanel().getAccent(), alpha));
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hovered(mouseX, mouseY) && visibleAnimation.getValue().floatValue() > 0.8F) {
            binding = true;
        } else if (!hovered(mouseX, mouseY)) {
            focusLost();
        }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (!binding || !getValue().isVisible()) return false;

        int key = input.key();
        if (key == GLFW.GLFW_KEY_ESCAPE
                || key == GLFW.GLFW_KEY_BACKSPACE
                || key == GLFW.GLFW_KEY_DELETE) {
            key = 0;
        }

        ((KeyValue) getValue()).setValue(Math.max(key, 0));
        binding = false;
        return true;
    }

    @Override
    public void focusLost() {
        binding = false;
    }
}
