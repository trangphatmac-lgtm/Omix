package cn.omix.util.opai.render;

import net.minecraft.client.gl.Framebuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderPass;
import cn.omix.util.opai.OpaiHud.InventoryHud.InventoryHudLayout;
import cn.omix.util.opai.OpaiHud.TargetHud.OpaiTargetHudPainter;
import cn.omix.util.opai.island.DynamicIslandManager;
import cn.omix.util.opai.island.DynamicIslandState;
import cn.omix.util.opai.layout.HudLayouts;
import java.util.ArrayList;
import java.util.Optional;
import net.minecraft.client.MinecraftClient;
import org.slf4j.LoggerFactory;

/** Bridges GUI extraction and rendering without native texture interop or a previous-frame image. */
public final class HudBackdrop {
   public enum Blur {
      HUD(HudGlassStyle.BLUR_SIGMA), MENU(6);
      private final float sigma;
      Blur(float sigma) { this.sigma = sigma; }
      public float sigma() { return sigma; }
   }
   private static final HudBlurRenderer BLUR = new HudBlurRenderer();
   private static final HudBlurRenderer MENU_BLUR = new HudBlurRenderer();
   private static InventoryHudLayout.Bounds inventory;
   private static OpaiTargetHudPainter.Bounds targetHud;
   private record Widget(HudLayouts.Box box, float radius, float opacity, Blur blur) { }
   private static final ArrayList<Widget> widgets = new ArrayList<>();
   private static boolean ready;
   private static boolean failed;

   private HudBackdrop() { }

   public static void inventory(InventoryHudLayout.Bounds bounds) {
      inventory = bounds;
   }

   public static void target(OpaiTargetHudPainter.Bounds bounds) {
      targetHud = bounds;
   }

   public static void widget(HudLayouts.Box box, float radius) { widget(box, radius, 1); }
   public static void widget(HudLayouts.Box box, float radius, float opacity) {
      widget(box, radius, opacity, Blur.HUD);
   }
   public static void widget(HudLayouts.Box box, float radius, float opacity, Blur blur) {
      if (opacity > 0) widgets.add(new Widget(box, radius, Math.clamp(opacity, 0, 1), blur));
   }

   /** World rendering has completed; native GUI item rendering has not started. */
   public static void prepare() {
      InventoryHudLayout.Bounds panel = inventory;
      OpaiTargetHudPainter.Bounds targetPanel = targetHud;
      var widgetPanels = new ArrayList<>(widgets);
      widgets.clear();
      inventory = null;
      targetHud = null;
      ready = false;
      BLUR.invalidate();
      MENU_BLUR.invalidate();
      MinecraftClient mc = MinecraftClient.getInstance();
      if (failed || !NVGRenderer.isAvailable() || mc.player == null || mc.world == null
          || mc.getOverlay() != null
          || (panel == null && targetPanel == null && widgetPanels.isEmpty() && !DynamicIslandManager.shouldRender(mc.currentScreen))) return;
      Framebuffer target = mc.getFramebuffer();
      if (target == null || target.getColorAttachmentView() == null) return;
      int guiWidth = mc.getWindow().getScaledWidth();
      var regions = new ArrayList<HudBlurRenderer.Region>(3);
      var menuRegions = new ArrayList<HudBlurRenderer.Region>();
      if (DynamicIslandManager.shouldRender(mc.currentScreen)) {
         regions.add(new HudBlurRenderer.Region(0, DynamicIslandState.TOP - 1, guiWidth,
            DynamicIslandState.IDLE_TOP - DynamicIslandState.TOP
               + Math.max(DynamicIslandState.MAX_NOTICES * DynamicIslandState.ROW_HEIGHT, 128) * 1.1f
                  * HudLayouts.INSTANCE.get(HudLayouts.Element.ISLAND).scale() + 2));
      }
      if (panel != null) {
         regions.add(new HudBlurRenderer.Region(panel.x(), panel.y(), panel.width(), panel.height()));
      }
      if (targetPanel != null) {
         regions.add(new HudBlurRenderer.Region(targetPanel.x(), targetPanel.y(), targetPanel.width(), targetPanel.height()));
      }
      for (Widget widget : widgetPanels) {
         var box = widget.box();
         (widget.blur() == Blur.MENU ? menuRegions : regions)
            .add(new HudBlurRenderer.Region(box.x(), box.y(), box.width(), box.height()));
      }
      
      try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
              .createRenderPass(() -> "omix/opai/hud-backdrop", target.getColorAttachmentView(), java.util.OptionalInt.empty())) {
         // Capture both strengths before compositing either, so each samples the untouched world.
         if (!regions.isEmpty()) {
            BLUR.capture(target.textureWidth, target.textureHeight, guiWidth, regions);
         }
         if (!menuRegions.isEmpty()) {
            MENU_BLUR.capture(target.textureWidth, target.textureHeight, guiWidth, menuRegions, Blur.MENU.sigma());
         }
         if (panel != null) {
            panel(target, panel.x(), panel.y(), panel.width(), panel.height(), InventoryHudLayout.RADIUS * panel.scale(),
               guiWidth, mc.getWindow().getScaledHeight(), 1);
         }
         if (targetPanel != null) {
            panel(target, targetPanel.x(), targetPanel.y(), targetPanel.width(), targetPanel.height(),
               OpaiTargetHudPainter.RADIUS, guiWidth, mc.getWindow().getScaledHeight(), 1);
         }
         for (Widget widget : widgetPanels) {
            var box = widget.box(); panel(target, box.x(), box.y(), box.width(), box.height(), widget.radius(),
               guiWidth, mc.getWindow().getScaledHeight(), widget.opacity(), widget.blur());
         }
         ready = true;
      } catch (RuntimeException failure) {
         fail(failure);
      }
   }

   /** Animated island geometry is known at the NanoVG stage, using the same world capture. */
   public static void island(float x, float y, float width, float height, float radius) {
      if (!ready || failed) return;
      MinecraftClient mc = MinecraftClient.getInstance();
      Framebuffer target = mc.getFramebuffer();
      if (target == null || target.getColorAttachmentView() == null) return;
      try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
              .createRenderPass(() -> "omix/opai/island-backdrop", target.getColorAttachmentView(), java.util.OptionalInt.empty())) {
         panel(target, x, y, width, height, radius,
            mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight(), 1);
      } catch (RuntimeException failure) {
         fail(failure);
      }
   }

   private static void panel(Framebuffer target, float x, float y, float width, float height, float radius,
                             float guiWidth, float guiHeight, float opacity) {
      panel(target, x, y, width, height, radius, guiWidth, guiHeight, opacity, Blur.HUD);
   }

   private static void panel(Framebuffer target, float x, float y, float width, float height, float radius,
                             float guiWidth, float guiHeight, float opacity, Blur blur) {
      (blur == Blur.MENU ? MENU_BLUR : BLUR).panel(x, y, width, height, radius, guiWidth, guiHeight, opacity);
   }

   private static void fail(RuntimeException failure) {
      failed = true;
      ready = false;
      LoggerFactory.getLogger("omix-opai-hud").warn("HUD backdrop blur unavailable; keeping translucent panels", failure);
   }

   public static void close() {
      BLUR.close();
      MENU_BLUR.close();
      inventory = null;
      targetHud = null;
      ready = failed = false;
      widgets.clear();
   }
}
