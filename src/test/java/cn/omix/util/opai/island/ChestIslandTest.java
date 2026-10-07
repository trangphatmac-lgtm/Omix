package cn.omix.util.opai.island;

import cn.omix.util.opai.island.ChestIsland;
import cn.omix.util.opai.island.ChestIsland.LootAnimation;
import cn.omix.util.opai.island.DynamicIslandState;
import cn.omix.util.opai.island.DynamicIslandStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ChestIslandTest {
   private static final DynamicIslandStatus STATUS = new DynamicIslandStatus("Player", "mc.example.net", 70, 200);
   private static final DynamicIslandState.TextWidth MEASURE = (text, size) -> text.length() * size * .5f;
   private static final DynamicIslandState.Panel CHEST = new DynamicIslandState.Panel(190, 68, 21, 8.5f);

   @Test void failedTransfersNeverFlashAndPartialTransfersDo() {
      var animation = new LootAnimation();
      animation.attach(new Object(), 3);
      animation.transferred(0, 64, 64, 100);
      animation.transferred(-1, 64, 0, 100);
      animation.transferred(27, 64, 0, 100);
      assertTrue(animation.feedback(150).isEmpty());
      animation.transferred(26, 64, 32, 100);
      assertEquals(26, animation.feedback(150).getFirst().slot());
      assertTrue(animation.feedback(480).isEmpty());
   }

   @Test void menuIdentityAndDisableClearFeedbackEvenWithReusedContainerIds() {
      var animation = new LootAnimation();
      Object firstMenu = new Object();
      animation.attach(firstMenu, 3);
      animation.transferred(0, 1, 0, 100);
      animation.attach(firstMenu, 3);
      assertFalse(animation.feedback(150).isEmpty());
      animation.attach(new Object(), 3);
      assertTrue(animation.feedback(150).isEmpty());
      animation.transferred(0, 1, 0, 150);
      animation.clear();
      assertTrue(animation.feedback(200).isEmpty());
      animation.attach(firstMenu, 6);
      animation.transferred(53, 1, 0, 200);
      assertEquals(53, animation.feedback(250).getFirst().slot());
   }

   @Test void feedbackGrowsFromCircleIntoFadingTileRegardlessOfSamplingRate() {
      var animation = new LootAnimation();
      animation.attach(new Object(), 3);
      animation.transferred(0, 1, 0, 100);
      var circle = animation.feedback(150).getFirst();
      var tile = animation.feedback(220).getFirst();
      assertEquals(circle.size() / 2, circle.radius(), .001f);
      assertTrue(tile.size() > circle.size());
      assertTrue(tile.radius() < tile.size() / 2);
      assertTrue(tile.opacity() < circle.opacity());
      var expected = animation.feedback(300);
      for (long time = 100; time < 300; time += 7) animation.feedback(time);
      assertEquals(expected, animation.feedback(300));
   }

   @Test void chestTemporarilyTakesPriorityAndReturnsToCurrentStatus() {
      var state = new DynamicIslandState();
      state.frame(0, 600, STATUS, MEASURE, false);
      state.postBreaking("Red Bed", .5f, 0);
      var chest = state.frame(0, 600, STATUS, MEASURE, false, CHEST);
      assertTrue(chest.rows().isEmpty());
      assertEquals(0, chest.idleOpacity());
      var stable = state.frame(900, 600, STATUS, MEASURE, false, CHEST);
      assertEquals(190, stable.width(), .1f);
      assertEquals(68, stable.height(), .1f);
      var closing = state.frame(900, 600, STATUS, MEASURE, false);
      assertEquals(1, closing.idleOpacity());
      assertEquals(stable.width(), closing.width(), .001f);
      assertEquals(stable.height(), closing.height(), .001f);
      assertEquals(23, state.frame(1800, 600, STATUS, MEASURE, false).height(), .1f);
   }

   @Test void chestMorphHasTheSameGeometryAtThirtySixtyAndOneFortyFourFps() {
      float[] expected = null;
      for (int fps : new int[]{30, 60, 144}) {
         var state = new DynamicIslandState();
         state.frame(0, 600, STATUS, MEASURE, false);
         state.frame(0, 600, STATUS, MEASURE, false, CHEST);
         for (int sample = 1; sample * 1000.0 / fps < 300; sample++) {
            state.frame(Math.round(sample * 1000.0 / fps), 600, STATUS, MEASURE, false, CHEST);
         }
         var f = state.frame(300, 600, STATUS, MEASURE, false, CHEST);
         float[] actual = {f.width(), f.height(), f.top()};
         if (expected == null) expected = actual;
         else assertArrayEquals(expected, actual, .001f);
      }
   }

   @Test void interruptedMorphKeepsGeometryAndReducedMotionUsesFinalGrid() {
      var state = new DynamicIslandState();
      state.frame(0, 600, STATUS, MEASURE, false);
      state.frame(0, 600, STATUS, MEASURE, false, CHEST);
      var before = state.frame(80, 600, STATUS, MEASURE, false, CHEST);
      var reverse = state.frame(80, 600, STATUS, MEASURE, false);
      assertEquals(before.width(), reverse.width(), .001f);
      assertEquals(before.height(), reverse.height(), .001f);
      var reopen = state.frame(90, 600, STATUS, MEASURE, false, CHEST);
      assertTrue(Float.isFinite(reopen.width()) && reopen.height() > 0);
      var large = state.frame(100, 600, STATUS, MEASURE, true,
         new DynamicIslandState.Panel(190, 128, 21, 8.5f));
      assertEquals(128, large.height());
      assertEquals(190, large.width());
   }

   @Test void allChestSlotsKeepTheirRowsAndColumns() {
      assertEquals(7, ChestIsland.IslandGeometry.slotX(0));
      assertEquals(167, ChestIsland.IslandGeometry.slotX(26));
      assertEquals(46, ChestIsland.IslandGeometry.slotY(26));
      assertEquals(106, ChestIsland.IslandGeometry.slotY(53));
   }

   @Test void shellSlicesCoverTheAnimatedPanelWhileItemsKeepTheirFinalCoordinates() {
      var state = new DynamicIslandState();
      state.frame(0, 600, STATUS, MEASURE, false);
      var open = state.frame(0, 600, STATUS, MEASURE, false, CHEST);
      var moving = state.frame(100, 600, STATUS, MEASURE, false, CHEST);
      assertEquals(new ChestIsland.IslandGeometry(open, 600, 1).contentLeft(),
         new ChestIsland.IslandGeometry(moving, 600, 1).contentLeft());
      for (float height : new float[]{23, 32, 68, 71, 128}) {
         var slices = ChestIsland.IslandGeometry.shellSlices(190, height);
         double area = slices.stream().mapToDouble(slice -> slice.width() * slice.height()).sum();
         assertEquals((190 + 16) * (height + 16), area, .001);
         assertTrue(slices.stream().allMatch(slice -> slice.width() > 0 && slice.height() > 0
            && slice.sourceX() >= 0 && slice.sourceX() + slice.sourceWidth() <= 56
            && slice.sourceY() >= 0 && slice.sourceY() + slice.sourceHeight() <= 56));
      }
   }
}
