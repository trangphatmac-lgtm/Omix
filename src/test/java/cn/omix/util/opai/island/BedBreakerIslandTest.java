package cn.omix.util.opai.island;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BedBreakerIslandTest {
    private static final DynamicIslandStatus STATUS = new DynamicIslandStatus("Player", "localhost", 0, 60);
    private static final DynamicIslandState.TextWidth WIDTH = (text, size) -> text.length() * size / 2;

    @Test void defenseAndBedShareOnePanelWithFreshNameAndProgress() {
        var state = new DynamicIslandState();
        state.postScaffold("64 blocks left", .64f, 0);
        state.postBreaking("End Stone", .72f, 10);
        var frame = state.frame(10, 800, STATUS, WIDTH, true);
        assertEquals(1, frame.rows().size());
        assertEquals("Breaking End Stone", frame.rows().getFirst().title());
        assertEquals("Break Progress: 72%", frame.rows().getFirst().detail());
        assertEquals(DynamicIslandState.BREAKING_HEIGHT, frame.height());
        state.postBreaking("Red Bed", .1f, 20);
        var bed = state.frame(20, 800, STATUS, WIDTH, true);
        assertEquals(1, bed.rows().size());
        assertEquals("Breaking Red Bed", bed.rows().getFirst().title());
        assertEquals(.1f, bed.rows().getFirst().progress());
    }

    @Test void completionAbortAndMissingSamplesReturnToIdle() {
        var state = new DynamicIslandState();
        state.postBreaking("Red Bed", 2, 0);
        assertEquals(1, state.frame(0, 800, STATUS, WIDTH, true).rows().getFirst().progress());
        state.remove("bed-breaker");
        assertEquals(1, state.frame(1, 800, STATUS, WIDTH, true).idleOpacity());
        state.postBreaking("Oak Planks", .3f, 10);
        assertTrue(state.frame(510, 800, STATUS, WIDTH, true).rows().isEmpty());
    }
}
