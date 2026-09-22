package im.webui.backend;

import net.minecraft.util.Identifier;

public interface Browser extends AutoCloseable {
    boolean isInitialized();

    BrowserLoadState getState();

    BrowserViewport getViewport();

    void setViewport(BrowserViewport viewport);

    boolean isVisible();

    void setVisible(boolean visible);

    short getPriority();

    void setPriority(short priority);

    BrowserSettings getSettings();

    boolean acceptsInput();

    String getUrl();

    void setUrl(String url);

    Identifier getTextureIdentifier();

    int getTextureWidth();

    int getTextureHeight();

    boolean isTextureReady();

    void forceReload();

    void reload();

    void goForward();

    void goBack();

    void update();

    void invalidate();

    void mouseClicked(double mouseX, double mouseY, int button);

    void mouseReleased(double mouseX, double mouseY, int button);

    void mouseMoved(double mouseX, double mouseY);

    void mouseScrolled(double mouseX, double mouseY, double delta);

    void keyPressed(int keyCode, int scanCode, int modifiers);

    void keyReleased(int keyCode, int scanCode, int modifiers);

    void charTyped(int codePoint, int modifiers);

    @Override
    void close();
}
