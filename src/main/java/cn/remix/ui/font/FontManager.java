package cn.remix.ui.font;

import cn.remix.Client;
import cn.remix.util.IMinecraft;
import lombok.Getter;

import java.awt.*;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FontManager implements IMinecraft {
    private final Map<String, TrueTypeFont> fontCache = new ConcurrentHashMap<>();
    private final Map<String, Font> rawCache = new ConcurrentHashMap<>();
    private final List<Font> systemFonts = new ArrayList<>();

    public FontManager() {
        Collections.addAll(systemFonts, GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts());
    }

    @Getter
    private final MinecraftFont mcFont = new MinecraftFont();

    public TrueTypeFont getFont(int size) {
        return get("MiSans-Medium.ttf", size);
    }

    public TrueTypeFont getTahomaFont(int fontSize) {
        return get("Tahoma.ttf", fontSize);
    }

    public TrueTypeFont getBoldFont(int size) {
        return get("MiSans-Semibold.ttf", size);
    }

    private TrueTypeFont get(String name, int size) {
        float scale = mc.getWindow().getScaleFactor();
        return fontCache.computeIfAbsent(name + "#" + size + "@" + scale, k -> {
            float actual = size * scale * 0.5f;
            List<Font> fallbacks = new ArrayList<>();
            fallbacks.add(new Font("Microsoft YaHei", Font.PLAIN, (int) actual));
            for (Font f : systemFonts) {
                if (!"Microsoft YaHei".equalsIgnoreCase(f.getName())) {
                    fallbacks.add(f.deriveFont(Font.PLAIN, actual));
                }
            }

            return new TrueTypeFont(rawCache.computeIfAbsent(name, this::loadRaw).deriveFont(actual), fallbacks, scale);
        });
    }

    private Font loadRaw(String name) {
        try (InputStream s = FontManager.class.getResourceAsStream("/assets/remix/fonts/" + name)) {
            if (s != null) return Font.createFont(Font.TRUETYPE_FONT, s);
            Client.logger.warn("Font not found: {}", name);
        } catch (Exception e) {
            Client.logger.error("Failed to load font {}", name, e);
        }

        return new Font("Microsoft YaHei", Font.PLAIN, 16);
    }
}
