package cn.omix.util.opai.render;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import static org.lwjgl.nanovg.NanoVG.nvgAddFallbackFontId;

public final class FontRepository {

   private static final Map<String, NVGTextRenderer> FONTS = new HashMap<>();
   private static final String RESOURCE_PREFIX = "assets/omix/opai/fonts/";
   public static final String CJK_RESOURCE = "assets/omix/fonts/MiSans-Medium.ttf";
   private static NVGTextRenderer cjk;

   private FontRepository() {
   }

   static void clear() { FONTS.clear(); cjk = null; }

   public static NVGTextRenderer getFont(String name) {
      NVGTextRenderer cached = FONTS.get(name);
      if (cached != null) {
         return cached;
      }

      if (cjk == null) cjk = load("omix-opai-cjk", CJK_RESOURCE);
      NVGTextRenderer renderer = load(name, RESOURCE_PREFIX + name + ".ttf");
      if (nvgAddFallbackFontId(NVGRenderer.getContext(), renderer.getFontId(), cjk.getFontId()) == 0)
         throw new IllegalStateException("Unable to attach Chinese fallback to font: " + name);
      FONTS.put(name, renderer);
      return renderer;
   }

   private static NVGTextRenderer load(String name, String resource) {
      InputStream input = FabricLoader.getInstance().getModContainer("omix")
         .flatMap(container -> container.findPath(resource))
         .map(path -> {
            try {
               return java.nio.file.Files.newInputStream(path);
            } catch (java.io.IOException e) {
               throw new IllegalStateException("Unable to open font: " + name, e);
            }
         })
         .orElse(null);
      if (input == null) {
         throw new IllegalStateException("Font not found: " + resource);
      }

      try (InputStream stream = input) {
         NVGTextRenderer renderer = new NVGTextRenderer(name, stream);
         if (renderer.getFontId() < 0) throw new IllegalStateException("Invalid font: " + resource);
         return renderer;
      } catch (Exception e) {
         throw new IllegalStateException("Unable to load font: " + name, e);
      }
   }
}
