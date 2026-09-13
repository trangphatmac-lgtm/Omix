package ai.backend;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class AiContainerSnapshotTest {
    @Test
    void rejectsDifferentTokenHandlerRevisionAndLayout() {
        Object handler = new Object();
        var stacks = List.of("diamond:8", "empty");
        var snapshot = AiContainerSnapshot.capture(handler, 2, stacks, "empty", value -> value);
        assertTrue(snapshot.matches(snapshot.id(), handler, 2, stacks, "empty", Objects::equals));
        assertFalse(snapshot.matches("old-token", handler, 2, stacks, "empty", Objects::equals));
        assertFalse(snapshot.matches(snapshot.id(), new Object(), 2, stacks, "empty", Objects::equals));
        assertFalse(snapshot.matches(snapshot.id(), handler, 3, stacks, "empty", Objects::equals));
        assertFalse(snapshot.matches(snapshot.id(), handler, 2, List.of("diamond:8"), "empty", Objects::equals));
        var next = AiContainerSnapshot.capture(handler, 2, stacks, "empty", value -> value);
        assertNotEquals(snapshot.id(), next.id());
    }

    @Test
    void detectsSlotCountItemAndCursorChangesWithoutRevisionChange() {
        Object handler = new Object();
        var snapshot = AiContainerSnapshot.capture(handler, 2, List.of("diamond:8"), "empty", value -> value);
        assertFalse(snapshot.matches(snapshot.id(), handler, 2, List.of("diamond:7"), "empty", Objects::equals));
        assertFalse(snapshot.matches(snapshot.id(), handler, 2, List.of("dirt:8"), "empty", Objects::equals));
        assertFalse(snapshot.matches(snapshot.id(), handler, 2, List.of("diamond:8"), "diamond:1", Objects::equals));
    }

    @Test
    void copiesBothSlotAndCursorStateBeforeLocalMutations() {
        Object handler = new Object();
        List<String> stack = new ArrayList<>(List.of("diamond", "8"));
        List<String> cursor = new ArrayList<>(List.of("dirt", "2"));
        var snapshot = AiContainerSnapshot.capture(handler, 2, List.of(stack), cursor, ArrayList::new);
        stack.set(1, "7");
        cursor.set(1, "1");
        assertEquals(List.of("diamond", "8"), snapshot.stacks().getFirst());
        assertEquals(List.of("dirt", "2"), snapshot.cursor());
        assertFalse(snapshot.matches(snapshot.id(), handler, 2, List.of(stack), cursor, Objects::equals));
    }
}
