package me.ksyz.accountmanager.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Path;
import java.util.List;

final class CookieLoginScreen extends Screen {
    private final AccountManagerScreen parent;
    private TextFieldWidget cookieField;
    private String input = "";
    private String status = "Paste cookies, open a file, or drop one here.";

    CookieLoginScreen(AccountManagerScreen parent) {
        super(Text.literal("Cookie Login"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = width / 2 - 150;
        int top = height / 2 - 24;
        cookieField = new TextFieldWidget(textRenderer, left, top, 300, 20, Text.literal("Cookies"));
        cookieField.setMaxLength(65536);
        cookieField.setPlaceholder(Text.literal("Cookie header (name=value; ...)"));
        cookieField.addFormatter((text, offset) -> OrderedText.styledForwardsVisitedString("*".repeat(text.length()), Style.EMPTY));
        cookieField.setText(input);
        cookieField.setChangedListener(value -> input = value);
        addDrawableChild(cookieField);
        setInitialFocus(cookieField);
        addDrawableChild(ButtonWidget.builder(Text.literal("Login"), button -> submit(input))
                .dimensions(left, top + 28, 146, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Paste & Login"), button -> pasteAndLogin())
                .dimensions(left + 154, top + 28, 146, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Open cookie file..."), button -> openFile())
                .dimensions(left, top + 52, 300, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> close())
                .dimensions(left, top + 76, 300, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 62, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("JSON / Netscape / Cookie header"),
                width / 2, height / 2 - 46, 0xFFAAAAAA);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(status), width / 2, height / 2 + 82, 0xFFFFD36B);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            submit(this.input);
            return true;
        }
        if (key == GLFW.GLFW_KEY_V && (input.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0) {
            // Reading the clipboard directly preserves tabs/newlines in Netscape and JSON exports.
            String clipboard = MinecraftClient.getInstance().keyboard.getClipboard();
            cookieField.setText(clipboard);
            this.input = clipboard;
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void onFilesDropped(List<Path> paths) {
        if (paths.size() != 1) {
            status = "Drop one cookie file at a time.";
            return;
        }
        submitFile(paths.getFirst());
    }

    @Override
    public void close() {
        input = "";
        if (cookieField != null) cookieField.setText("");
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void pasteAndLogin() {
        submit(MinecraftClient.getInstance().keyboard.getClipboard());
    }

    private void submit(String content) {
        if (content == null || content.isBlank()) {
            status = "Paste cookies or select a cookie file first.";
            return;
        }
        close();
        parent.addCookies(content);
    }

    private void submitFile(Path path) {
        close();
        parent.addCookieFile(path);
    }

    private void openFile() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var filters = stack.pointers(stack.UTF8("*.txt"), stack.UTF8("*.json"), stack.UTF8("*.cookies"));
            String file = TinyFileDialogs.tinyfd_openFileDialog("Select a cookie file", "", filters,
                    "Cookie files (*.txt, *.json, *.cookies)", false);
            if (file != null) submitFile(Path.of(file));
        } catch (RuntimeException | LinkageError e) {
            status = "File picker unavailable. Drop a file or use Paste & Login.";
        }
    }
}
