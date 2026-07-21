package cn.remix.ui.clickgui.component.impl;

import cn.remix.module.value.impl.TextValue;
import cn.remix.ui.clickgui.ModuleButton;
import cn.remix.ui.clickgui.component.Component;
import cn.remix.util.animation.Easing;
import cn.remix.util.animation.EasingAnimation;
import cn.remix.util.render.ColorUtil;
import cn.remix.util.render.Render2D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;

public final class TextComponent extends Component {
    private final EasingAnimation visibleAnimation = new EasingAnimation(Easing.EASE_OUT_CUBIC, 200);
    private String editingValue;
    private int cursorIndex;
    private boolean editing;
    private boolean selectedAll;

    public TextComponent(ModuleButton parent, TextValue value) {
        super(parent, value);
        editingValue = value.getValue();
        cursorIndex = editingValue.length();
    }

    @Override
    public float getHeight() {
        if (!getValue().isVisible()) focusLost();
        visibleAnimation.run(getValue().isVisible() && parent.isExtended() ? 1.0 : 0.0);
        return 32.0F * visibleAnimation.getValue().floatValue();
    }

    @Override
    public void render(DrawContext context, float x, float y, float width, int mouseX, int mouseY, float globalAlpha) {
        super.render(context, x, y, width, mouseX, mouseY, globalAlpha);
        float progress = visibleAnimation.getValue().floatValue();
        this.height = 32.0F * progress;
        float finalProgress = progress * globalAlpha;
        if (finalProgress < 0.01F) return;

        TextValue textValue = (TextValue) getValue();
        if (!editing && !editingValue.equals(textValue.getValue())) {
            editingValue = textValue.getValue();
            cursorIndex = editingValue.length();
        }

        var font = instance.getFontManager().getFont(16);
        int alpha = MathHelper.clamp((int) (255.0F * finalProgress), 0, 255);
        float fieldX = x + 4.0F;
        float fieldY = y + 14.0F;
        float fieldWidth = width - 8.0F;

        font.drawString(context, textValue.getName(), fieldX, y + 2.0F,
                new Color(204, 204, 204, alpha).getRGB());
        Render2D.drawRect(context, fieldX, fieldY, fieldWidth, 14.0F,
                ColorUtil.applyAlpha(new Color(35, 35, 40).getRGB(), alpha));

        String display = editingValue.isEmpty() && !editing ? "Enter text..." : editingValue;
        int displayColor = editingValue.isEmpty() && !editing
                ? new Color(130, 130, 135, alpha).getRGB()
                : new Color(225, 225, 225, alpha).getRGB();
        float textY = fieldY + (14.0F - font.getHeight()) / 2.0F + 0.5F;
        float availableWidth = fieldWidth - 6.0F;
        float prefixWidth = font.getStringWidth(editingValue.substring(0, Math.min(cursorIndex, editingValue.length())));
        float scrollOffset = editing ? Math.max(0.0F, prefixWidth - availableWidth + 1.0F) : 0.0F;
        float drawX = fieldX + 3.0F - scrollOffset;

        Render2D.beginScissor(context, fieldX + 1.0F, fieldY, fieldWidth - 2.0F, 14.0F);
        if (selectedAll && !editingValue.isEmpty()) {
            Render2D.drawRect(context, drawX - 1.0F, textY - 1.0F,
                    font.getStringWidth(editingValue) + 2.0F, font.getHeight() + 2.0F,
                    ColorUtil.applyAlpha(new Color(0, 100, 180).getRGB(), Math.round(alpha * 0.55F)));
        }
        font.drawString(context, display, drawX, textY, displayColor);

        if (editing && !selectedAll) {
            int cursorAlpha = Math.round(alpha * (float) Math.abs(Math.sin(System.currentTimeMillis() / 250.0)));
            Render2D.drawRect(context, drawX + prefixWidth, textY + 1.0F, 1.0F,
                    Math.max(1.0F, font.getHeight() - 2.0F), new Color(255, 255, 255, cursorAlpha).getRGB());
        }
        Render2D.endScissor(context);

        if (editing) {
            Render2D.drawRect(context, fieldX, fieldY + 13.0F, fieldWidth, 1.0F,
                    ColorUtil.applyAlpha(parent.getModulePanel().getAccent(), alpha));
        }
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            if (!hovered(mouseX, mouseY)) focusLost();
            return;
        }

        boolean shouldEdit = getValue().isVisible()
                && visibleAnimation.getValue().floatValue() > 0.8F
                && hovered(mouseX, mouseY);
        if (shouldEdit && !editing) {
            editingValue = ((TextValue) getValue()).getValue();
            cursorIndex = editingValue.length();
        }
        editing = shouldEdit;
        selectedAll = false;
    }

    @Override
    public void focusLost() {
        editing = false;
        selectedAll = false;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (!editing || !getValue().isVisible()) return false;

        int key = input.key();
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_ESCAPE) {
            editing = false;
            selectedAll = false;
            return true;
        }

        if (input.hasCtrl() && key == GLFW.GLFW_KEY_A) {
            selectedAll = !editingValue.isEmpty();
            cursorIndex = editingValue.length();
            return true;
        }

        if (input.hasCtrl() && key == GLFW.GLFW_KEY_V) {
            String clipboard = MinecraftClient.getInstance().keyboard.getClipboard();
            if (clipboard != null) insert(clean(clipboard));
            return true;
        }

        if (key == GLFW.GLFW_KEY_LEFT) {
            if (cursorIndex > 0) cursorIndex = editingValue.offsetByCodePoints(cursorIndex, -1);
            selectedAll = false;
            return true;
        }

        if (key == GLFW.GLFW_KEY_RIGHT) {
            if (cursorIndex < editingValue.length()) cursorIndex = editingValue.offsetByCodePoints(cursorIndex, 1);
            selectedAll = false;
            return true;
        }

        if (key == GLFW.GLFW_KEY_HOME) {
            cursorIndex = 0;
            selectedAll = false;
            return true;
        }

        if (key == GLFW.GLFW_KEY_END) {
            cursorIndex = editingValue.length();
            selectedAll = false;
            return true;
        }

        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (selectedAll) {
                replaceAll("");
            } else if (cursorIndex > 0) {
                int previous = editingValue.offsetByCodePoints(cursorIndex, -1);
                editingValue = editingValue.substring(0, previous) + editingValue.substring(cursorIndex);
                cursorIndex = previous;
                syncValue();
            }
            return true;
        }

        if (key == GLFW.GLFW_KEY_DELETE) {
            if (selectedAll) {
                replaceAll("");
            } else if (cursorIndex < editingValue.length()) {
                int next = editingValue.offsetByCodePoints(cursorIndex, 1);
                editingValue = editingValue.substring(0, cursorIndex) + editingValue.substring(next);
                syncValue();
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (!editing || !getValue().isVisible() || !input.isValidChar()) return false;

        int codepoint = input.codepoint();
        if (Character.isISOControl(codepoint) || codepoint == 167) return false;
        insert(input.asString());
        return true;
    }

    private void insert(String text) {
        if (text.isEmpty()) return;
        if (selectedAll) {
            replaceAll(text);
            return;
        }

        editingValue = editingValue.substring(0, cursorIndex) + text + editingValue.substring(cursorIndex);
        cursorIndex += text.length();
        syncValue();
    }

    private void replaceAll(String text) {
        editingValue = text;
        cursorIndex = text.length();
        selectedAll = false;
        syncValue();
    }

    private void syncValue() {
        ((TextValue) getValue()).setValue(editingValue);
    }

    private String clean(String value) {
        return value.replaceAll("[\\p{Cntrl}§]", "");
    }
}
