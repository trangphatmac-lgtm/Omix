package cn.omix.util.opai.render;

import net.minecraft.client.gl.Framebuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderPass;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.opengl.GL;

import static org.lwjgl.nanovg.NanoVG.*;
import static org.lwjgl.nanovg.NanoVGGL3.*;

public final class NVGRenderer {

   private static long VG = 0;
   private static final NanoVGStencilBuffer STENCIL = new NanoVGStencilBuffer();

   public static final NVGPaint NVG_PAINT = NVGPaint.create();
   public static final NVGColor NVG_COLOR_1 = NVGColor.create();
   public static final NVGColor NVG_COLOR_2 = NVGColor.create();

   private static boolean frameStarted;
   private static boolean failed;
   public static float globalAlpha = 1;

   private static final MinecraftClient mc = MinecraftClient.getInstance();

   private static long getVG() {
      if (VG == 0) {
         VG = nvgCreate(NVG_ANTIALIAS | NVG_STENCIL_STROKES);
         if (VG == 0) {
            throw new IllegalStateException("Failed to create NanoVG context");
         }
      }
      return VG;
   }

   public static void ensureContext() {
      getVG();
   }

   /** Only called at shutdown, while the device and native context are still alive. */
   public static void close() {
      HudBackdrop.close();
      if (VG != 0) {
         if (frameStarted) nvgCancelFrame(VG);
         STENCIL.close(); nvgDelete(VG);
         VG = 0;
      }
      frameStarted = false;
      scissors.clear();
      FontRepository.clear();
   }

   public static boolean isAvailable() {
      if (failed) return false;
      if (usesGpuBackend()) {
         try { getVG(); return true; }
         catch (RuntimeException error) { unavailable(error); return false; }
      }
      try {
         return GL.getCapabilities() != null && GL.getCapabilities().OpenGL33;
      } catch (Throwable t) {
         return false;
      }
   }

   public static boolean usesGpuBackend() { return false; }

   public static boolean beginFrame() {
      if (!isAvailable()) {
         return false;
      }

      long vg = getVG();

      if (!frameStarted) {
         GLUtility.setup();
         GLUtility.push();
         GLUtility.prepareNanoVG();
         final int guiWidth = mc.getWindow().getScaledWidth();
         final int guiHeight = mc.getWindow().getScaledHeight();
         final float devicePixelRatio = (float)mc.getWindow().getFramebufferWidth() / (float)guiWidth;
         nvgBeginFrame(vg, guiWidth, guiHeight, devicePixelRatio);
         if (!scissors.isEmpty()) {
            useCurrentScissors();
         }
         frameStarted = true;

         return true;
      }

      return false;
   }

   public static void endFrame() {
      if (!frameStarted) {
         return;
      }

      final long vg = getVG();
      final Framebuffer target = mc.getFramebuffer();
      try {
         if (target == null || target.getColorAttachmentView() == null) {
            nvgCancelFrame(vg);
            return;
         }

         // GuiRenderer has closed its pass. Bind the main target again before
         // NanoVG submits raw GL commands, otherwise they land in a stale FBO.
         try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                 .createRenderPass(() -> "omix/opai/nvg", target.getColorAttachmentView(), java.util.OptionalInt.empty())) {
            org.lwjgl.opengl.GL11.glViewport(0, 0, target.textureWidth, target.textureHeight);
            GLUtility.prepareNanoVG();
            STENCIL.render(target.textureWidth, target.textureHeight, () -> nvgEndFrame(vg));
         }
      } finally {
         GLUtility.pop();
         frameStarted = false;
      }
   }

   private static void unavailable(RuntimeException error) {
      failed = true;
      org.slf4j.LoggerFactory.getLogger("omix-opai-nvg").error(
         "Opai NanoVG renderer unavailable", error);
   }

   public static void clearScissors() {
      scissors.clear();
   }

   public static void globalAlpha(float alpha) {
      globalAlpha = alpha;
      nvgGlobalAlpha(getVG(), alpha);
   }

   public static void rect(float x, float y, float width, float height, int color) {
      applyColor(color, NVG_COLOR_1);

      nvgBeginPath(getVG());
      nvgFillColor(getVG(), NVG_COLOR_1);
      nvgRect(getVG(), x, y, width, height);
      nvgFill(getVG());
      nvgClosePath(getVG());
   }

   public static void rect(float x, float y, float width, float height, NVGPaint nvgPaint) {
      nvgBeginPath(getVG());
      nvgFillPaint(getVG(), nvgPaint);
      nvgRect(getVG(), x, y, width, height);
      nvgFill(getVG());
      nvgClosePath(getVG());
   }

   public static void scale(float factor, float x, float y, float width, float height, Runnable content) {
      long vg = getVG();
      float translateX = x + width / 2F;
      float translateY = y + height / 2F;

      nvgSave(vg);
      nvgTranslate(vg, translateX, translateY);
      nvgScale(vg, factor, factor);
      nvgTranslate(vg, -translateX, -translateY);

      content.run();

      nvgRestore(vg);
   }

   public static void rectStroke(float x, float y, float width, float height, float strokeThickness, int color, int strokeColor) {
      rect(x - strokeThickness, y - strokeThickness, width + strokeThickness * 2, height + strokeThickness * 2, strokeColor);
      rect(x, y, width, height, color);
   }

   public static void rotate(double degrees, float x, float y, float width, float height, Runnable content) {
      long vg = getVG();
      float translateX = x + width / 2f;
      float translateY = y + height / 2f;

      nvgSave(vg);
      nvgTranslate(vg, translateX, translateY);
      nvgRotate(vg, (float)Math.toRadians(degrees));

      content.run();

      nvgRestore(vg);
   }

   public static void rectOutline(float x, float y, float width, float height, float thickness, int color) {
      applyColor(color, NVG_COLOR_1);

      nvgBeginPath(getVG());
      nvgFillColor(getVG(), NVG_COLOR_1);
      nvgRect(getVG(), x, y, width, thickness);
      nvgFill(getVG());
      nvgClosePath(getVG());

      nvgBeginPath(getVG());
      nvgFillColor(getVG(), NVG_COLOR_1);
      nvgRect(getVG(), x + width - thickness, y + thickness, thickness, height - thickness);
      nvgFill(getVG());
      nvgClosePath(getVG());

      nvgBeginPath(getVG());
      nvgFillColor(getVG(), NVG_COLOR_1);
      nvgRect(getVG(), x, y + height - thickness, width - thickness, thickness);
      nvgFill(getVG());
      nvgClosePath(getVG());

      nvgBeginPath(getVG());
      nvgFillColor(getVG(), NVG_COLOR_1);
      nvgRect(getVG(), x, y + thickness, thickness, height - thickness);
      nvgFill(getVG());
      nvgClosePath(getVG());
   }

   private static final List<ScreenPosition> scissors = new ArrayList<>();

   public static void scissor(float x, float y, float width, float height, Runnable content) {
      long vg = getVG();
      ScreenPosition scissor = new ScreenPosition(x, y, width, height);
      scissors.add(scissor);

      nvgIntersectScissor(vg, x, y, width, height);
      content.run();
      nvgResetScissor(vg);

      scissors.remove(scissor);
      useCurrentScissors();
   }

   private static void useCurrentScissors() {
      for (ScreenPosition scissor : scissors) {
         nvgIntersectScissor(getVG(), scissor.getX(), scissor.getY(), scissor.getWidth(), scissor.getHeight());
      }
   }

   public static void roundedRect(float x, float y, float width, float height, float radius, int color) {
      applyColor(color, NVG_COLOR_1);

      nvgBeginPath(getVG());
      nvgFillColor(getVG(), NVG_COLOR_1);
      nvgRoundedRect(getVG(), x, y, width, height, radius);
      nvgFill(getVG());
      nvgClosePath(getVG());
   }

   public static void roundedRectGradient(float x, float y, float width, float height, float radius, int color1, int color2, float angleDegrees) {
      applyColor(color1, NVG_COLOR_1);
      applyColor(color2, NVG_COLOR_2);

      float angleRadians = (float)Math.toRadians(angleDegrees);
      float dx = (float)Math.cos(angleRadians);
      float dy = (float)Math.sin(angleRadians);

      nvgLinearGradient(
         getVG(),
         x + width * 0.5f - dx * width * 0.5f,
         y + height * 0.5f - dy * height * 0.5f,
         x + width * 0.5f + dx * width * 0.5f,
         y + height * 0.5f + dy * height * 0.5f,
         NVG_COLOR_1,
         NVG_COLOR_2,
         NVG_PAINT
      );

      nvgBeginPath(getVG());
      nvgFillPaint(getVG(), NVG_PAINT);
      nvgRoundedRect(getVG(), x, y, width, height, radius);
      nvgFill(getVG());
      nvgClosePath(getVG());
   }

   public static void roundedRectVarying(float x, float y, float width, float height, float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft, int color) {
      applyColor(color, NVG_COLOR_1);

      nvgBeginPath(getVG());
      nvgFillColor(getVG(), NVG_COLOR_1);
      nvgRoundedRectVarying(getVG(), x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft);
      nvgFill(getVG());
      nvgClosePath(getVG());
   }

   public static void applyColor(int color, NVGColor nvgColor) {
      int[] rgba = ColorUtility.hexToRGBA(color);
      nvgRGBAf(rgba[0] / 255f, rgba[1] / 255f, rgba[2] / 255f, rgba[3] / 255f, nvgColor);
   }

   public static MinecraftClient getMinecraft() {
      return mc;
   }

   public static long getContext() {
      return getVG();
   }
}
