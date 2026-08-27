package cn.omix.module.impl.world;

import java.util.List;
import java.util.Locale;

final class VictorySignalMatcher {
    private static final List<String> TITLE_KEYWORDS = List.of(
            "victory",
            "胜利",
            "congratulations",
            "恭喜"
    );
    private static final List<String> CHAT_KEYWORDS = List.of(
            "congratulations",
            "恭喜",
            "you won",
            "you win",
            "you got 1st"
    );

    private VictorySignalMatcher() {}

    static boolean matchesTitle(String text) {
        return containsAny(text, TITLE_KEYWORDS);
    }

    static boolean matchesChat(String text) {
        return containsAny(text, CHAT_KEYWORDS);
    }

    private static boolean containsAny(String text, List<String> keywords) {
        if (text == null || text.isBlank()) return false;

        String normalized = text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return keywords.stream().anyMatch(normalized::contains);
    }
}
