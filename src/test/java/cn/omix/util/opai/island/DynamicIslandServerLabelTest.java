package cn.omix.util.opai.island;


import net.minecraft.SharedConstants;
import net.minecraft.text.Text;
import net.minecraft.Bootstrap;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.Formatting;
import net.minecraft.scoreboard.ScoreboardCriterion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class DynamicIslandServerLabelTest {
   private static final String ADDRESS = "private-relay.example:25565";

   @BeforeAll static void bootstrap() {
      SharedConstants.createGameVersion();
   }

   private static ScoreboardObjective sidebar(Scoreboard scoreboard, String name, ScoreboardDisplaySlot slot) {
      var objective = scoreboard.addObjective(name, ScoreboardCriterion.DUMMY, Text.literal(name),
         ScoreboardCriterion.RenderType.INTEGER, false, null);
      scoreboard.setObjectiveSlot(slot, objective);
      return objective;
   }

   private static void line(Scoreboard scoreboard, ScoreboardObjective objective, String owner, int value, String display) {
      var score = scoreboard.getOrCreateScore(ScoreHolder.fromName(owner), objective);
      score.setScore(value);
      if (display != null) score.setDisplayText(Text.literal(display));
   }

   @Test void footerDomainsAndFormattingReplaceRelayAddressInIslandContent() {
      for (String footer : new String[]{"www.hypixel.net", "§eWWW.HYPIXEL.COM§r", "Visit www.hypixel.net"}) {
         var scoreboard = new Scoreboard();
         line(scoreboard, sidebar(scoreboard, "game", ScoreboardDisplaySlot.SIDEBAR), "footer", 0, footer);
         var labels = new DynamicIslandManager.ServerLabel();
         var address = labels.address(new Object(), false, ADDRESS, scoreboard, "Player");
         assertEquals("mc.hypixel.net", address);
         var status = new DynamicIslandStatus("Player", address, 56, 240);
         String rendered = status.layout((text, size) -> text.length() * 4, 1000).parts().stream()
            .map(DynamicIslandStatus.Part::text).collect(java.util.stream.Collectors.joining());
         assertTrue(rendered.contains("56ms to mc.hypixel.net"));
         assertFalse(rendered.contains(ADDRESS));
      }
   }

   @Test void teamPrefixOwnerAndSuffixAreReadAsOneVisibleLine() {
      var scoreboard = new Scoreboard();
      var objective = sidebar(scoreboard, "game", ScoreboardDisplaySlot.SIDEBAR);
      var team = scoreboard.addTeam("footer");
      team.setPrefix(Text.literal("§ewww.hypi"));
      team.setSuffix(Text.literal("xel.net§r"));
      scoreboard.addScoreHolderToTeam("§a", team);
      line(scoreboard, objective, "§a", 0, null);
      assertEquals("mc.hypixel.net", new DynamicIslandManager.ServerLabel()
         .address(new Object(), false, ADDRESS, scoreboard, "Player"));
   }

   @Test void recognitionSurvivesBoardRefreshButResetsForNewConnectionsAndSingleplayer() {
      var scoreboard = new Scoreboard();
      line(scoreboard, sidebar(scoreboard, "game", ScoreboardDisplaySlot.SIDEBAR), "www.hypixel.net", 0, null);
      var labels = new DynamicIslandManager.ServerLabel();
      var connection = new Object();
      assertEquals("mc.hypixel.net", labels.address(connection, false, ADDRESS, scoreboard, "Player"));
      scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, null);
      assertEquals("mc.hypixel.net", labels.address(connection, false, ADDRESS, scoreboard, "Player"));
      assertEquals(ADDRESS, labels.address(new Object(), false, ADDRESS, scoreboard, "Player"));
      assertNull(labels.address(connection, true, ADDRESS, scoreboard, "Player"));
      assertEquals("server", labels.address(null, false, ADDRESS, scoreboard, "Player"));
      assertEquals(ADDRESS, labels.address(connection, false, ADDRESS, scoreboard, "Player"));
   }

   @Test void hiddenLinesInactiveObjectivesAndSimilarDomainsCannotIdentifyHypixel() {
      var scoreboard = new Scoreboard();
      var sidebar = sidebar(scoreboard, "game", ScoreboardDisplaySlot.SIDEBAR);
      line(scoreboard, sidebar, "#hidden", 10, "www.hypixel.net");
      line(scoreboard, sidebar, "fake", 9, "www.hypixel.net.evil.example");
      line(scoreboard, sidebar, "other", 8, "fakewww.hypixel.com");
      line(scoreboard, sidebar(scoreboard, "tab", ScoreboardDisplaySlot.LIST), "www.hypixel.net", 1, null);
      assertEquals(ADDRESS, new DynamicIslandManager.ServerLabel()
         .address(new Object(), false, ADDRESS, scoreboard, "Player"));
   }

   @Test void viewersColoredSidebarHasPriorityOverDefaultSidebar() {
      var scoreboard = new Scoreboard();
      line(scoreboard, sidebar(scoreboard, "default", ScoreboardDisplaySlot.SIDEBAR), "www.hypixel.net", 0, null);
      var viewerTeam = scoreboard.addTeam("viewer");
      viewerTeam.setColor(Formatting.GREEN);
      scoreboard.addScoreHolderToTeam("Player", viewerTeam);
      var colored = sidebar(scoreboard, "green", ScoreboardDisplaySlot.TEAM_GREEN);
      line(scoreboard, colored, "footer", 0, "other.example");
      var labels = new DynamicIslandManager.ServerLabel();
      var connection = new Object();
      assertEquals(ADDRESS, labels.address(connection, false, ADDRESS, scoreboard, "Player"));
      line(scoreboard, colored, "footer", 0, "www.hypixel.net");
      assertEquals("mc.hypixel.net", labels.address(connection, false, ADDRESS, scoreboard, "Player"));
   }

   @Test void entriesBelowVanillasFifteenLineLimitAreIgnored() {
      var scoreboard = new Scoreboard();
      var objective = sidebar(scoreboard, "game", ScoreboardDisplaySlot.SIDEBAR);
      for (int i = 1; i <= 15; i++) line(scoreboard, objective, "line" + i, i, null);
      line(scoreboard, objective, "www.hypixel.net", 0, null);
      assertEquals(ADDRESS, new DynamicIslandManager.ServerLabel()
         .address(new Object(), false, ADDRESS, scoreboard, "Player"));
   }
}
