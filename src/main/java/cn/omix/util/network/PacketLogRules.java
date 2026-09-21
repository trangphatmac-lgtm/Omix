package cn.omix.util.network;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Literal protocol IDs with glob wildcards, never user-supplied regular expressions. */
public record PacketLogRules(List<String> whitelist, List<String> blacklist) {
    public static final PacketLogRules ALL = parse("", "");

    public PacketLogRules {
        whitelist = List.copyOf(whitelist);
        blacklist = List.copyOf(blacklist);
    }

    public static PacketLogRules parse(String whitelist, String blacklist) {
        return new PacketLogRules(tokens(whitelist), tokens(blacklist));
    }

    private static List<String> tokens(String text) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[\\s,;，；]+"))
                .filter(s -> !s.isEmpty()).distinct().toList();
    }

    public boolean allows(String id) {
        String normalized = id.toLowerCase(Locale.ROOT);
        return blacklist.stream().noneMatch(pattern -> matches(pattern, normalized))
                && (whitelist.isEmpty() || whitelist.stream().anyMatch(pattern -> matches(pattern, normalized)));
    }

    private static boolean matches(String pattern, String id) {
        // An omitted namespace means minecraft, so modded IDs are never accidentally matched.
        if (!pattern.contains(":")) pattern = "minecraft:" + pattern;
        int p = 0, i = 0, star = -1, retry = -1;
        while (i < id.length()) {
            if (p < pattern.length() && (pattern.charAt(p) == '?' || pattern.charAt(p) == id.charAt(i))) {
                p++;
                i++;
            } else if (p < pattern.length() && pattern.charAt(p) == '*') {
                star = p++;
                retry = i;
            } else if (star >= 0) {
                p = star + 1;
                i = ++retry;
            } else return false;
        }
        while (p < pattern.length() && pattern.charAt(p) == '*') p++;
        return p == pattern.length();
    }
}
