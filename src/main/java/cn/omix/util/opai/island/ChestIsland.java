package cn.omix.util.opai.island;

import cn.omix.Client;
import cn.omix.module.impl.player.ChestStealer;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.util.player.ItemUtil;
import cn.omix.util.opai.render.ModTextures;
import cn.omix.util.opai.island.DynamicIslandPainter.Surface;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import java.util.ArrayList;
import java.util.List;

/** Samsara's island grid and animation, observing Omix's existing chest transfers. */
public final class ChestIsland {
   private static final MinecraftClient mc = MinecraftClient.getInstance();
   private final LootAnimation animation = new LootAnimation();
   private Object menu;
   private int[] counts = new int[0];

   private GenericContainerScreenHandler workingMenu(Screen screen) {
      if (mc.player == null || mc.world == null || mc.player.isSpectator()
          || !(screen instanceof GenericContainerScreen container)
          || mc.player.currentScreenHandler != container.getScreenHandler()) return null;
      var module = Client.instance.getModuleManager().getModule(ChestStealer.class);
      return module != null && module.isNativeBehaviorActive() ? container.getScreenHandler() : null;
   }

   public boolean replacesContainer(Screen screen) {
      var handler = workingMenu(screen);
      if (handler == null || !handler.getCursorStack().isEmpty()) return false;
      var module = Client.instance.getModuleManager().getModule(ChestStealer.class);
      boolean onlyBest = module.getValues().stream().anyMatch(value -> value instanceof BoolValue bool
         && value.getName().equals("Only Best") && bool.getValue());
      boolean hasWanted = false;
      for (int slot = 0; slot < handler.getInventory().size(); slot++) {
         ItemStack item = handler.getSlot(slot).getStack();
         if (item.isEmpty() || onlyBest && ItemUtil.isUseless(-1, item)) continue;
         hasWanted = true;
         // Leave normal slots accessible when the player cannot accept anything.
         for (int i = 0; i < 36; i++) {
            ItemStack target = mc.player.getInventory().getStack(i);
            if (target.isEmpty() || ItemStack.areItemsAndComponentsEqual(target, item)
               && target.getCount() < target.getMaxCount()) return true;
         }
      }
      // Keep the final animation until the native module closes the completed chest.
      return !hasWanted && menu == handler && module.getValues().stream().anyMatch(value -> value instanceof BoolValue bool
         && value.getName().equals("Auto Close") && bool.getValue());
   }

   public IslandView islandView(Screen screen, long now, boolean reducedMotion) {
      if (!replacesContainer(screen)) { clear(); return null; }
      var handler = workingMenu(screen);
      if (menu != handler) {
         menu = handler; counts = new int[handler.getInventory().size()];
         for (int i = 0; i < counts.length; i++) counts[i] = handler.getSlot(i).getStack().getCount();
      }
      animation.attach(handler, handler.getRows());
      List<ItemStack> items = new ArrayList<>();
      for (int slot = 0; slot < counts.length; slot++) {
         ItemStack item = handler.getSlot(slot).getStack();
         animation.transferred(slot, counts[slot], item.getCount(), now);
         counts[slot] = item.getCount(); items.add(item.copy());
      }
      return new IslandView(handler.getRows(), List.copyOf(items), reducedMotion ? List.of() : animation.feedback(now));
   }

   public void clear() { menu = null; counts = new int[0]; animation.clear(); }

   public record IslandView(int rows, List<ItemStack> items, List<Feedback> feedback) {
      public DynamicIslandState.Panel panel() {
         return new DynamicIslandState.Panel(190, rows * 20 + 8, DynamicIslandState.IDLE_TOP, 8.5f);
      }
   }

   public record IslandGeometry(DynamicIslandState.Frame frame, float viewportWidth, float scale) {
      public float left() { return (viewportWidth - frame.width()) / 2; }
      public float contentLeft() { return (viewportWidth - 190) / 2; }
      public static int slotX(int slot) { return 7 + slot % 9 * 20; }
      public static int slotY(int slot) { return 6 + slot / 9 * 20; }

      public static List<ShellSlice> shellSlices(float width, float height) {
         float totalWidth = width + 16, totalHeight = height + 16;
         float edgeX = Math.min(17, totalWidth / 2), edgeY = Math.min(17, totalHeight / 2);
         float[] x = {-8, -8 + edgeX, width + 8 - edgeX, width + 8};
         float[] y = {-8, -8 + edgeY, height + 8 - edgeY, height + 8};
         int[] source = {0, 17, 39, 56};
         List<ShellSlice> slices = new ArrayList<>(9);
         for (int row = 0; row < 3; row++) for (int column = 0; column < 3; column++) {
            float w = x[column + 1] - x[column], h = y[row + 1] - y[row];
            if (w > 0 && h > 0) slices.add(new ShellSlice(x[column], y[row], w, h,
               source[column], source[row], source[column + 1] - source[column], source[row + 1] - source[row]));
         }
         return List.copyOf(slices);
      }
   }

   public record ShellSlice(float x, float y, float width, float height,
                            int sourceX, int sourceY, int sourceWidth, int sourceHeight) { }

   public void extractIslandItems(DrawContext graphics, IslandView view, IslandGeometry geometry) {
      var frame = geometry.frame();
      if (frame.width() <= 2 || frame.height() <= 2) return;
      float left = geometry.left() * geometry.scale(), top = frame.top();
      graphics.getMatrices().pushMatrix();
      try {
         graphics.getMatrices().translate(left, top);
         graphics.getMatrices().scale(geometry.scale(), geometry.scale());
         extractIslandShell(graphics, frame.width(), frame.height());
      } finally {
         graphics.getMatrices().popMatrix();
      }
      graphics.enableScissor((int)Math.ceil(left + geometry.scale()), (int)Math.ceil(top + geometry.scale()),
         (int)Math.floor(left + (frame.width() - 1) * geometry.scale()),
         (int)Math.floor(top + (frame.height() - 1) * geometry.scale()));
      graphics.getMatrices().pushMatrix();
      try {
         graphics.getMatrices().translate(geometry.contentLeft() * geometry.scale(), top);
         graphics.getMatrices().scale(geometry.scale(), geometry.scale());
         graphics.createNewRootLayer();
         for (int slot = 0; slot < view.items().size(); slot++) {
            ItemStack stack = view.items().get(slot);
            if (stack.isEmpty()) continue;
            int x = IslandGeometry.slotX(slot), y = IslandGeometry.slotY(slot);
            graphics.drawItem(stack, x, y);
            graphics.drawStackOverlay(mc.textRenderer, stack, x, y);
         }
      } finally {
         graphics.getMatrices().popMatrix();
         graphics.disableScissor();
      }
   }

   private static void extractIslandShell(DrawContext graphics, float width, float height) {
      var texture = ModTextures.register("textures/hud/chest-island.png");
      // Nine slices preserve corner/shadow radii while the spring changes both dimensions.
      for (ShellSlice slice : IslandGeometry.shellSlices(width, height)) {
         graphics.getMatrices().pushMatrix();
         try {
            graphics.getMatrices().translate(slice.x(), slice.y());
            graphics.getMatrices().scale(slice.width() / slice.sourceWidth(), slice.height() / slice.sourceHeight());
            graphics.drawTexture(RenderPipelines.GUI_TEXTURED, texture, 0, 0,
               slice.sourceX() * 4, slice.sourceY() * 4, slice.sourceWidth(), slice.sourceHeight(),
               slice.sourceWidth() * 4, slice.sourceHeight() * 4, 224, 224);
         } finally {
            graphics.getMatrices().popMatrix();
         }
      }
   }

   public static void paintIslandOverlay(Surface surface, IslandView view, IslandGeometry geometry) {
      float x = geometry.contentLeft(), y = geometry.frame().top();
      surface.clip(geometry.left() + 1, y + 1, Math.max(0, geometry.frame().width() - 2),
         Math.max(0, geometry.frame().height() - 2), () -> {
         for (Feedback feedback : view.feedback()) {
            float cx = x + IslandGeometry.slotX(feedback.slot()) + 8;
            float cy = y + IslandGeometry.slotY(feedback.slot()) + 8;
            surface.rounded(cx - feedback.size() / 2, cy - feedback.size() / 2,
               feedback.size(), feedback.size(), feedback.radius(),
               (Math.round(feedback.opacity() * 255) << 24) | 0xDCDCDC);
         }
      });
   }

   public record Feedback(int slot, float size, float radius, float opacity) { }

   public static final class LootAnimation {
      private Object menu;
      private long[] transferredAt = new long[0];

      public void attach(Object menu, int rows) {
         if (this.menu == menu && this.transferredAt.length == rows * 9) return;
         this.menu = menu;
         this.transferredAt = new long[rows * 9];
         java.util.Arrays.fill(this.transferredAt, -1);
      }

      public void transferred(int slot, int before, int after, long now) {
         if (slot >= 0 && slot < this.transferredAt.length && before > after) this.transferredAt[slot] = now;
      }

      public List<Feedback> feedback(long now) {
         List<Feedback> result = new ArrayList<>();
         for (int slot = 0; slot < this.transferredAt.length; slot++) {
            long age = now - this.transferredAt[slot];
            if (this.transferredAt[slot] < 0 || age < 0 || age >= 380) continue;
            float growth = Math.clamp(age / 120f, 0, 1);
            float size = 20 * growth;
            float corners = Math.clamp((growth - .75f) / .25f, 0, 1);
            float radius = size / 2 + (4 - size / 2) * corners;
            float opacity = .34f * (float)Math.exp(-Math.max(0, age - 75) / 80.0);
            result.add(new Feedback(slot, size, radius, opacity));
         }
         return List.copyOf(result);
      }

      public void clear() {
         this.menu = null;
         this.transferredAt = new long[0];
      }
   }
}
