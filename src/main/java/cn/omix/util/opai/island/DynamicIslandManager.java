package cn.omix.util.opai.island;

import cn.omix.module.Module;
import cn.omix.module.impl.player.BedAura;
import cn.omix.module.impl.render.ClickGui;
import cn.omix.util.opai.OpaiHud;
import cn.omix.ui.neverlose.NeverloseClickGuiScreen;
import cn.omix.util.opai.island.ChestIsland.IslandGeometry;
import cn.omix.util.opai.island.ChestIsland.IslandView;
import cn.omix.util.opai.island.DynamicIslandState.Icon;
import cn.omix.util.opai.render.*;
import java.util.Locale;
import java.util.Comparator;
import java.util.regex.Pattern;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.scoreboard.*;
import static org.lwjgl.nanovg.NanoVG.*;

public final class DynamicIslandManager {
   private static final MinecraftClient MC = MinecraftClient.getInstance();
   private static final DynamicIslandState STATE = new DynamicIslandState();
   private static final ScaffoldBpsTracker SCAFFOLD_BPS = new ScaffoldBpsTracker();
   private static final ServerLabel SERVER_LABEL = new ServerLabel();
   private static final ChestIsland CHEST = new ChestIsland();
   private static cn.omix.module.impl.world.Scaffold scaffold() {
      return cn.omix.Client.instance.getModuleManager().getModule(cn.omix.module.impl.world.Scaffold.class);
   }
   private static Sample extracted;
   private static boolean bedAuraBreaking;
   private record Sample(DynamicIslandState.Frame frame, float viewportWidth, float scale,
                         IslandView chest, Screen screen) {
      IslandGeometry geometry() { return new IslandGeometry(frame, viewportWidth, scale); }
   }

   private DynamicIslandManager() { }

   public static synchronized boolean shouldRender(Screen current) {
      if (current instanceof NeverloseClickGuiScreen) {
         // Neverlose uses the full window; stale island frames must not leak into it.
         extracted = null;
         CHEST.clear();
         STATE.clear();
         SCAFFOLD_BPS.reset();
         return false;
      }
      if (MC.player == null || MC.world == null) {
         extracted = null;
         CHEST.clear();
         STATE.clear();
         SCAFFOLD_BPS.reset();
         if (MC.getNetworkHandler() == null) SERVER_LABEL.reset();
         return false;
      }
      if (!OpaiHud.enabled(OpaiHud.Widget.STATUS_BAR)) {
         extracted = null;
         CHEST.clear();
         STATE.clear();
         SCAFFOLD_BPS.reset();
         return false;
      }
      return MC.getOverlay() == null && !MC.options.hudHidden && !MC.options.playerListKey.isPressed();
   }

   public static synchronized void onModuleToggled(Module module) {
      if (module == null || module instanceof ClickGui) {
         return;
      }
      if (module == scaffold()) {
         SCAFFOLD_BPS.reset();
         if (!module.isNativeBehaviorActive()) {
            STATE.remove("scaffold");
         }
      }
      if (module instanceof BedAura && !module.isNativeBehaviorActive() && bedAuraBreaking) {
         STATE.remove("bed-aura");
         bedAuraBreaking = false;
      }
      if (MC.player == null || !OpaiHud.enabled(OpaiHud.Widget.STATUS_BAR)) {
         return;
      }
      String label = module.getName().replaceAll("(?<=[a-z])(?=[A-Z])", " ");
      STATE.post("module:" + module.getName(), "Module Toggled", label + " has been ",
         module.isEnabled() ? "Enabled" : "Disabled", Icon.TOGGLE, module.isEnabled(), now(), DynamicIslandState.TOGGLE_MS);
   }

   public static synchronized void notifySuccess(String title, String description) {
      notify(title, description, Icon.SUCCESS, 2600);
   }

   public static synchronized void notifyWarning(String title, String description) {
      notify(title, description, Icon.WARNING, 3000);
   }

   public static synchronized void notifyInfo(String title, String description) {
      notify(title, description, Icon.INFO, 2600);
   }

   private static void notify(String title, String description, Icon icon, long duration) {
      if (MC.player != null && OpaiHud.enabled(OpaiHud.Widget.STATUS_BAR)) {
         STATE.post(icon + ":" + title + ":" + description, title, description, "", icon, false, now(), duration);
      }
   }

   private static long now() {
      return System.nanoTime() / 1_000_000L;
   }

   private static DynamicIslandNanoSurface surface() {
      return new DynamicIslandNanoSurface(NVGRenderer.getContext(),
         FontRepository.getFont(DynamicIslandNanoSurface.FONT).getFontId(),
         FontRepository.getFont(DynamicIslandNanoSurface.IDLE_FONT).getFontId());
   }

   public static synchronized void beginExtraction() { extracted = null; }

   public static synchronized boolean replacesContainer(Screen screen) {
      return CHEST != null && NVGRenderer.isAvailable() && shouldRender(screen)
         && CHEST.replacesContainer(screen);
   }

   public static synchronized boolean extractChestItems(Screen screen, DrawContext graphics) {
      if (!replacesContainer(screen)) return false;
      extracted = sample(now(), screen);
      if (extracted.chest() == null) return false;
      var geometry = extracted.geometry();
      HudBackdrop.widget(new cn.omix.util.opai.layout.HudLayouts.Box(
         geometry.left() * extracted.scale(), extracted.frame().top(),
         extracted.frame().width() * extracted.scale(), extracted.frame().height() * extracted.scale()),
         extracted.frame().radius() * extracted.scale());
      graphics.createNewRootLayer();
      screen.renderInGameBackground(graphics);
      
      graphics.createNewRootLayer();
      CHEST.extractIslandItems(graphics, extracted.chest(), extracted.geometry());
      return true;
   }

   private static Sample sample(long now, Screen screen) {
      boolean reducedMotion = MC.options.getDistortionEffectScale().getValue() <= 0;
      IslandView chest = CHEST == null ? null
         : CHEST.islandView(screen, now, reducedMotion);
      if (chest == null) {
         if (!updateBreaking(now)) updateScaffold(now);
      }
      float screenWidth = MC.getWindow().getScaledWidth();
      float scale = cn.omix.util.opai.layout.HudLayouts.INSTANCE.get(cn.omix.util.opai.layout.HudLayouts.Element.ISLAND).scale();
      // The complete grid also fits at large HUD scales or in narrow windows.
      if (chest != null) scale = Math.min(scale, Math.min(Math.max(1, screenWidth - 16) / 190f,
         Math.max(1, MC.getWindow().getScaledHeight() - DynamicIslandState.IDLE_TOP - 8) / chest.panel().height()));
      float logicalWidth = screenWidth / scale;
      long vg = NVGRenderer.getContext();
      nvgSave(vg);
      try {
         var frame = STATE.frame(now, logicalWidth, defaultStatus(now), surface(), reducedMotion,
            chest == null ? null : chest.panel());
         return new Sample(frame, logicalWidth, scale, chest, screen);
      } finally {
         nvgRestore(vg);
      }
   }

   public static synchronized void renderNano() {
      if (!shouldRender(MC.currentScreen)) return;
      Sample sample = extracted != null ? extracted : sample(now(), MC.currentScreen);
      var frame = sample.frame();
      float scale = sample.scale();
      float screenWidth = MC.getWindow().getScaledWidth();
      var box = new cn.omix.util.opai.layout.HudLayouts.Box((screenWidth - frame.width() * scale) / 2,
         DynamicIslandState.top(frame), frame.width() * scale, frame.height() * scale);
      cn.omix.util.opai.layout.HudLayouts.INSTANCE.drawn(cn.omix.util.opai.layout.HudLayouts.Element.ISLAND, box);
      // In 1.21.11 native items have already been submitted at this stage.
      // Chest blur is composed by prepare(), before its native shell and items.
      if (sample.chest() == null)
         HudBackdrop.island(box.x(), box.y(), box.width(), box.height(), DynamicIslandState.radius(frame) * scale);
      long vg = NVGRenderer.getContext();
      nvgSave(vg);
      try {
         nvgTranslate(vg, 0, DynamicIslandState.top(frame) * (1 - scale));
         nvgScale(vg, scale, scale);
         // Chest extraction draws its shell between the vanilla dimmer and native items.
         if (sample.chest() == null)
            DynamicIslandPainter.paint(surface(), frame, sample.viewportWidth(), cn.omix.util.opai.OpaiHudTheme.currentPalette());
      } finally {
         nvgRestore(vg);
      }
   }

   public static synchronized boolean hasChestOverlay() {
      return extracted != null && extracted.chest() != null && extracted.screen() == MC.currentScreen
         && replacesContainer(MC.currentScreen);
   }

   public static synchronized void renderChestOverlay() {
      if (!hasChestOverlay()) return;
      long vg = NVGRenderer.getContext();
      nvgSave(vg);
      try {
         nvgTranslate(vg, 0, extracted.frame().top() * (1 - extracted.scale()));
         nvgScale(vg, extracted.scale(), extracted.scale());
         ChestIsland.paintIslandOverlay(surface(), extracted.chest(), extracted.geometry());
      } finally {
         nvgRestore(vg);
      }
   }

   private static DynamicIslandStatus defaultStatus(long now) {
      String username = MC.getSession().getUsername();
      int ping = -1;
      if (MC.player != null && MC.getNetworkHandler() != null) {
         var connection = MC.getNetworkHandler();
         var info = connection.getPlayerListEntry(MC.player.getUuid());
         if (info != null) ping = Math.max(0, info.getLatency());
         if (connection instanceof DynamicIslandLatency.Source source) ping = source.omix$getLivePing(now, ping);
      }
      var server = MC.getCurrentServerEntry();
      String address = SERVER_LABEL.address(MC.getNetworkHandler(), MC.isInSingleplayer(),
         server != null ? server.address : "server", MC.world == null ? null : MC.world.getScoreboard(),
         MC.player == null ? null : MC.player.getNameForScoreboard());
      return new DynamicIslandStatus(username, address, ping, MC.getCurrentFps());
   }

   static final class ServerLabel {
      private static final Pattern HYPIXEL = Pattern.compile("(?i)(?<![\\w.-])www\\.hypixel\\.(?:net|com)(?![\\w.-])");
      private static final Comparator<ScoreboardEntry> DISPLAY_ORDER = Comparator
         .comparingInt(ScoreboardEntry::value).reversed().thenComparing(ScoreboardEntry::owner, String.CASE_INSENSITIVE_ORDER);
      private Object connection;
      private boolean hypixel;

      String address(Object connection, boolean singleplayer, String address, Scoreboard scoreboard, String playerName) {
         if (singleplayer || connection == null) {
            reset();
            return singleplayer ? null : "server";
         }
         if (this.connection != connection) {
            reset();
            this.connection = connection;
         }
         if (!this.hypixel && scoreboard != null) this.hypixel = hasHypixelFooter(scoreboard, playerName);
         return this.hypixel ? "mc.hypixel.net" : address;
      }

      void reset() {
         this.connection = null;
         this.hypixel = false;
      }

      private static boolean hasHypixelFooter(Scoreboard scoreboard, String playerName) {
         var team = playerName == null ? null : scoreboard.getScoreHolderTeam(playerName);
         var objective = team == null || !team.getColor().isColor() ? null
            : scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.fromFormatting(team.getColor()));
         if (objective == null) objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
         if (objective == null) return false;
         return scoreboard.getScoreboardEntries(objective).stream().filter(entry -> !entry.hidden())
            .sorted(DISPLAY_ORDER).limit(15).anyMatch(entry -> {
               var entryTeam = scoreboard.getScoreHolderTeam(entry.owner());
               String text = Team.decorateName(entryTeam, entry.name()).getString().replaceAll("§.", "");
               return HYPIXEL.matcher(text).find();
            });
      }
   }

   /** Called by Scaffold's existing post-motion event; this only observes player movement. */
   public static synchronized void sampleScaffoldMovement() {
      if (MC.player == null || MC.world == null) {
         SCAFFOLD_BPS.reset();
         return;
      }
      SCAFFOLD_BPS.sample(MC.player, MC.world, MC.player.age,
         MC.player.getX(), MC.player.getZ(), cn.omix.util.misc.TimerSpeedUtil.getTimerSpeed());
   }

   private static void updateScaffold(long now) {
      if (scaffold() == null || !scaffold().isNativeBehaviorActive() || MC.player == null || MC.world == null) {
         STATE.remove("scaffold");
         SCAFFOLD_BPS.reset();
         return;
      }
      int blocks = 0;
      for (int i = 0; i < 36; i++) {
         var stack = MC.player.getInventory().getStack(i);
         if (stack.getItem() instanceof net.minecraft.item.BlockItem) blocks += stack.getCount();
      }
      String detail = String.format(Locale.ROOT, "%d blocks left - %.1f block/s",
         blocks, SCAFFOLD_BPS.blocksPerSecond(MC.player, MC.world));
      STATE.postScaffold(detail, Math.min(1, blocks / 100f), now);
   }

   private static boolean updateBreaking(long now) {
      BedAura module = cn.omix.Client.instance.getModuleManager().getModule(BedAura.class);
      var target = module == null ? null : module.diggingTarget(MC.getRenderTickCounter().getTickProgress(false));
      if (target == null) {
         if (bedAuraBreaking) STATE.remove("bed-aura");
         bedAuraBreaking = false;
         return false;
      }
      STATE.postBreaking(target.state().getBlock().getName().getString(), target.progress(), now);
      bedAuraBreaking = true;
      return true;
   }

   /** Public integration point retained for script/external progress producers. */
   public static synchronized void postBreaking(String name, float progress) {
      STATE.postBreaking(name, progress, now());
      bedAuraBreaking = false;
   }
}
