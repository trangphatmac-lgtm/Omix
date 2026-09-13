package ai.backend;

import java.util.List;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.UnaryOperator;

/** Detached slot/cursor copies protect actions from server updates and local inventory edits alike. */
record AiContainerSnapshot<T>(String id, Object handler, int revision, List<T> stacks, T cursor) {
    static <T> AiContainerSnapshot<T> capture(Object handler, int revision, List<T> stacks, T cursor,
                                             UnaryOperator<T> copy) {
        return new AiContainerSnapshot<>(UUID.randomUUID().toString(), handler, revision,
                stacks.stream().map(copy).toList(), copy.apply(cursor));
    }

    boolean matches(String requestedId, Object currentHandler, int currentRevision, List<T> currentStacks,
                    T currentCursor, BiPredicate<T, T> equal) {
        if (!id.equals(requestedId) || currentHandler != handler || currentRevision != revision
                || currentStacks.size() != stacks.size() || !equal.test(cursor, currentCursor)) return false;
        for (int i = 0; i < stacks.size(); i++) {
            if (!equal.test(stacks.get(i), currentStacks.get(i))) return false;
        }
        return true;
    }
}
