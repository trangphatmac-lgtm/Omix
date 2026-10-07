package cn.omix.ui.opai;
import cn.omix.util.opai.editor.*;

import cn.omix.util.opai.layout.HudLayouts;
import cn.omix.util.opai.bridge.ConfigManager;
import cn.omix.util.opai.bridge.FeatureManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** A transparent unified editor. Fixed widgets participate in scaling but never in dragging. */
public final class HudEditorScreen extends Screen {
   private final HudEditorGesture gesture = new HudEditorGesture(HudLayouts.INSTANCE);
   private long entered;
   private int mouseX, mouseY;
   public HudEditorScreen() { super(Text.literal("HUD Editor")); }
   private static long now() { return System.nanoTime() / 1_000_000L; }
   public static boolean active() { return MinecraftClient.getInstance().currentScreen instanceof HudEditorScreen; }
   public static float previewOpacity() {
      var screen = MinecraftClient.getInstance().currentScreen;
      return screen instanceof HudEditorScreen editor ? 1 - (float)Math.exp(-(now() - editor.entered) / 60.0) : 1;
   }
   @Override protected void init() { this.entered = now(); }
   @Override public void renderBackground(DrawContext graphics, int mouseX, int mouseY, float partialTick) { }
   @Override public void render(DrawContext graphics, int mouseX, int mouseY, float partialTick) {
      this.mouseX = mouseX; this.mouseY = mouseY;
      // Preview through the same native rendering code, even without an Aura target.
      if (FeatureManager.hud() != null) FeatureManager.hud().renderPreview(graphics, partialTick);
      graphics.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
         "Drag HUDs • Hold left + scroll to resize • Right-click resets size • Esc to finish",
         this.width / 2, this.height - 22, 0xFFFFFFFF);
   }
   @Override public boolean mouseClicked(Click event, boolean doubleClick) {
      return this.gesture.press(event.x(), event.y(), event.button(), now());
   }
   @Override public boolean mouseDragged(Click event, double x, double y) {
      return this.gesture.drag(event.x(), event.y(), event.button(), this.width, this.height);
   }
   @Override public boolean mouseReleased(Click event) { return this.gesture.release(event.button()); }
   @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
      this.gesture.wheel(vertical, now()); return true;
   }
   @Override public boolean keyPressed(KeyInput event) {
      if (event.key() == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
      return super.keyPressed(event);
   }
   @Override public void close() { ConfigManager.saveQuietly(); this.client.setScreen(null); }
   public void renderOutline() {
      var element = this.gesture.captured() != null ? this.gesture.captured() : HudLayouts.INSTANCE.hit(mouseX, mouseY);
      if (element == null) return;
      var box = HudLayouts.INSTANCE.bounds(element); if (box == null) return;
      long vg = cn.omix.util.opai.render.NVGRenderer.getContext();
      org.lwjgl.nanovg.NanoVG.nvgSave(vg);
      try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
         var color = org.lwjgl.nanovg.NVGColor.malloc(stack);
         int accent = cn.omix.util.opai.OpaiHudTheme.currentPalette().accent();
         org.lwjgl.nanovg.NanoVG.nvgRGBA((byte)(accent >> 16),(byte)(accent >> 8),(byte)accent,(byte)160,color);
         org.lwjgl.nanovg.NanoVG.nvgStrokeColor(vg,color); org.lwjgl.nanovg.NanoVG.nvgStrokeWidth(vg,.6f);
         org.lwjgl.nanovg.NanoVG.nvgBeginPath(vg);
         org.lwjgl.nanovg.NanoVG.nvgRoundedRect(vg,box.x()-1,box.y()-1,box.width()+2,box.height()+2,4);
         org.lwjgl.nanovg.NanoVG.nvgStroke(vg);
      } finally { org.lwjgl.nanovg.NanoVG.nvgRestore(vg); }
   }
   @Override public boolean shouldPause() { return false; }
}
