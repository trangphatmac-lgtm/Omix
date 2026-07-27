package cn.omix.ui.clickgui;

import cn.omix.module.Module;
import cn.omix.module.value.Value;
import cn.omix.module.value.impl.*;
import cn.omix.ui.clickgui.component.Component;
import cn.omix.ui.clickgui.component.impl.*;
import cn.omix.ui.clickgui.panel.impl.ModulePanel;
import cn.omix.util.IMinecraft;
import cn.omix.util.animation.Easing;
import cn.omix.util.animation.EasingAnimation;
import cn.omix.util.misc.KeyUtil;
import cn.omix.util.render.ColorUtil;
import cn.omix.util.render.Render2D;
import lombok.Getter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

@Getter
public final class ModuleButton implements IMinecraft {
    public static final float height = 16;
    private final ModulePanel modulePanel;
    private final Module module;
    private final List<Component> components = new ArrayList<>();
    private final Set<Value> componentValues = Collections.newSetFromMap(new IdentityHashMap<>());
    private final EasingAnimation openAnimation = new EasingAnimation(Easing.EASE_OUT_CUBIC, 200);
    private final EasingAnimation toggleAnimation = new EasingAnimation(Easing.EASE_OUT_CUBIC, 150);
    private final EasingAnimation hoverAnimation = new EasingAnimation(Easing.EASE_OUT_CUBIC, 150);
    private boolean extended, binding;
    private float renderHeight = height;

    public ModuleButton(ModulePanel modulePanel, Module module) {
        this.modulePanel = modulePanel;
        this.module = module;
        syncComponents();
    }

    private void syncComponents() {
        for (Value value : module.getValues()) {
            if (componentValues.contains(value)) continue;

            Component component = null;
            if (value instanceof BoolValue bool) component = new BoolComponent(this, bool);
            else if (value instanceof NumberValue num) component = new NumberComponent(this, num);
            else if (value instanceof ModeValue mode) component = new ModeComponent(this, mode);
            else if (value instanceof MultiBoolValue multi) component = new MultiBoolComponent(this, multi);
            else if (value instanceof ColorValue color) component = new ColorComponent(this, color);
            else if (value instanceof TextValue text) component = new TextComponent(this, text);
            else if (value instanceof KeyValue key) component = new KeyComponent(this, key);

            if (component != null) {
                components.add(component);
                componentValues.add(value);
            }
        }
    }

    public float render(DrawContext context, float x, float y, float width, int mouseX, int mouseY, float globalAlpha) {
        syncComponents();
        var font = instance.getFontManager().getBoldFont(16);
        boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        int alphaInt = (int) (255 * globalAlpha);

        hoverAnimation.run(hovered ? 1 : 0);
        int bg = ColorUtil.interpolate(new Color(34, 34, 38).getRGB(), new Color(42, 42, 48).getRGB(), hoverAnimation.getValue().floatValue());
        Render2D.drawRect(context, x, y, width, height, ColorUtil.applyAlpha(bg, alphaInt));

        toggleAnimation.run(module.isEnabled() ? 1 : 0);
        float fontY = y + (height - font.getHeight()) / 2.0f + 0.5f;
        int txtColor = ColorUtil.interpolate(new Color(170, 170, 170).getRGB(), Color.WHITE.getRGB(), toggleAnimation.getValue().floatValue());

        font.drawString(context, binding ? "Bind: " + KeyUtil.getKeyName(module.getKey()) : module.getName(), x + 7, fontY, ColorUtil.applyAlpha(txtColor, alphaInt));
        if (!components.isEmpty()) font.drawString(context, extended ? "-" : "+", x + width - 9, fontY, ColorUtil.applyAlpha(new Color(136, 136, 136).getRGB(), alphaInt));

        float totalHeight = 0;
        for (Component c : components) totalHeight += c.getHeight();

        openAnimation.run(extended ? 1 : 0);
        float animHeight = openAnimation.getValue().floatValue() * totalHeight;

        if (animHeight > 0.5f) {
            Render2D.beginScissor(context, x, y + height, width, animHeight);
            float offset = 0;
            for (Component c : components) {
                if (c.getHeight() < 0.01f && !c.getValue().isVisible()) continue;
                c.render(context, x, y + height + offset, width, mouseX, mouseY, globalAlpha);
                offset += c.getHeight();
            }
            Render2D.endScissor(context);
        }
        return this.renderHeight = height + animHeight;
    }

    public void mouseClicked(double mouseX, double mouseY, int button, float x, float y, float width) {
        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
            clearComponentFocus();
            if (button == 0) module.toggle();
            else if (button == 1) extended = !extended;
            else if (button == 2) binding = true;
            return;
        }
        if (extended && openAnimation.getValue().floatValue() > 0.5f) {
            for (Component c : components) {
                if (c.getHeight() >= 0.01f || c.getValue().isVisible()) c.mouseClicked(mouseX, mouseY, button);
            }
        }
    }

    public void mouseReleased(double mouseX, double mouseY, int button) {
        components.forEach(c -> c.mouseReleased(mouseX, mouseY, button));
    }

    public boolean keyTyped(KeyInput input) {
        for (Component component : components) {
            if (component.keyPressed(input)) return true;
        }

        if (binding) {
            module.setKey(input.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE ? -1 : input.key());
            binding = false;
            return true;
        }
        return false;
    }

    public boolean charTyped(CharInput input) {
        for (Component component : components) {
            if (component.charTyped(input)) return true;
        }
        return false;
    }

    public void clearComponentFocus() {
        components.forEach(Component::focusLost);
    }
}
