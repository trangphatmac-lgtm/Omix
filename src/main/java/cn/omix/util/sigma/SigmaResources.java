package cn.omix.util.sigma;

import cn.omix.ui.font.TrueTypeFont;
import net.minecraft.util.Identifier;

import java.awt.Font;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Original Helvetica Neue fonts and textures, lazily loaded on the render thread. */
public final class SigmaResources {
    private static final Map<String, TrueTypeFont> FONTS = new HashMap<>();
    private static final Map<String, Font> RAW_FONTS = new HashMap<>();
    private static final Map<String, Identifier> TEXTURES = new HashMap<>();

    private SigmaResources() {}

    public static Identifier texture(String path) {
        return TEXTURES.computeIfAbsent(path, key -> Identifier.of("omix", "sigma/" + key));
    }

    public static TrueTypeFont light(int size) { return font("helvetica-neue-light.ttf", size); }
    public static TrueTypeFont medium(int size) { return font("helvetica-neue medium.ttf", size); }

    private static TrueTypeFont font(String name, int size) {
        return FONTS.computeIfAbsent(name + size, key -> {
            Font font = RAW_FONTS.computeIfAbsent(name, SigmaResources::loadFont).deriveFont((float) size);
            // A Jello Latin UI font does not need a 64 MiB atlas per size. Pages grow on demand.
            return new TrueTypeFont(font, List.of(new Font("Dialog", Font.PLAIN, size)), 1, 1024, true);
        });
    }

    private static Font loadFont(String name) {
        try (var stream = SigmaResources.class.getResourceAsStream("/assets/omix/sigma/font/" + name)) {
            if (stream == null) throw new IOException("Missing Jello font " + name);
            return Font.createFont(Font.TRUETYPE_FONT, stream);
        } catch (IOException | java.awt.FontFormatException error) {
            throw new IllegalStateException("Cannot load original Jello font", error);
        }
    }
}
