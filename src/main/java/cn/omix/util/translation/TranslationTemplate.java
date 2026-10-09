package cn.omix.util.translation;

import java.util.*;
import java.util.regex.*;

/** Provider-neutral template. Protected values and style objects never leave the client. */
public final class TranslationTemplate {
    public static final int VERSION = 1;
    private static final Pattern PROTECTED = Pattern.compile(
            "\\[\\[[^\\r\\n]*?]]|https?://[^\\s]+|www\\.[^\\s]+|[\\w.-]+\\.(?:com|net|org|gg|io)(?:/[^\\s]*)?"
            + "|[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"
            + "|(?<![\\p{L}\\p{N}_])[+-]?\\d+(?:[.,:/-]\\d+)*(?:%)?"
            + "|(?<!\\S)[/.#][A-Za-z][\\w:.-]*|\\R");
    private static final Pattern MARKER = Pattern.compile("\\[\\[(?:/?s|p)\\d+]]");
    private final String source;
    private final List<String> originals;
    private final List<String> protectedValues;
    private final List<String> markers;
    private final boolean translatable;

    private TranslationTemplate(String source, List<String> originals, List<String> protectedValues, boolean translatable) {
        this.source = source; this.originals = List.copyOf(originals);
        this.protectedValues = List.copyOf(protectedValues);
        this.markers = markers(source); this.translatable = translatable;
    }

    public static TranslationTemplate of(List<String> spans, Collection<String> playerNames) {
        String plain = String.join("", spans);
        boolean[] protectedChars = new boolean[plain.length()];
        Matcher matcher = PROTECTED.matcher(plain);
        while (matcher.find()) Arrays.fill(protectedChars, matcher.start(), matcher.end(), true);
        for (String name : playerNames) {
            if (name == null || name.isEmpty()) continue;
            int start = 0;
            while ((start = plain.indexOf(name, start)) >= 0) {
                int end = start + name.length();
                if ((start == 0 || !identifier(plain.charAt(start - 1))) && (end == plain.length() || !identifier(plain.charAt(end))))
                    Arrays.fill(protectedChars, start, end, true);
                start = end;
            }
        }
        var values = new ArrayList<String>();
        var template = new StringBuilder();
        int offset = 0; boolean natural = false;
        for (int i = 0; i < spans.size(); i++) {
            String span = spans.get(i); template.append("[[s").append(i).append("]]");
            for (int j = 0; j < span.length();) {
                if (protectedChars[offset + j]) {
                    int end = j + 1;
                    while (end < span.length() && protectedChars[offset + end]) end++;
                    template.append("[[p").append(values.size()).append("]]");
                    values.add(span.substring(j, end)); j = end;
                } else {
                    int cp = span.codePointAt(j); natural |= Character.isLetter(cp);
                    template.appendCodePoint(cp); j += Character.charCount(cp);
                }
            }
            template.append("[[/s").append(i).append("]]"); offset += span.length();
        }
        return new TranslationTemplate(template.toString(), spans, values, natural && spans.size() <= 128 && template.length() <= 8000);
    }

    private static boolean identifier(char value) { return Character.isLetterOrDigit(value) || value == '_'; }
    private static List<String> markers(String text) { return MARKER.matcher(text).results().map(MatchResult::group).toList(); }
    public String source() { return source; }
    public boolean translatable() { return translatable; }

    public boolean accepts(String translated) {
        if (translated == null || translated.length() > 16000 || !markers.equals(markers(translated))
                || translated.codePoints().anyMatch(cp -> cp < 32 || cp == 127 || cp == '§')) return false;
        // Require precisely the original style skeleton, without unstyled text between spans.
        int end = 0;
        for (int i = 0; i < originals.size(); i++) {
            String open = "[[s" + i + "]]", close = "[[/s" + i + "]]";
            if (!translated.startsWith(open, end)) return false;
            int closeAt = translated.indexOf(close, end + open.length());
            if (closeAt < 0) return false;
            end = closeAt + close.length();
        }
        return end == translated.length();
    }

    public List<String> restore(String translated) {
        if (!accepts(translated)) throw new IllegalArgumentException("Invalid translated template");
        var spans = new ArrayList<String>();
        for (int i = 0; i < originals.size(); i++) {
            String open = "[[s" + i + "]]", close = "[[/s" + i + "]]";
            String span = translated.substring(translated.indexOf(open) + open.length(), translated.indexOf(close));
            // One pass: a protected value may itself contain marker-looking text.
            Matcher matcher = Pattern.compile("\\[\\[p(\\d+)]]").matcher(span);
            spans.add(matcher.replaceAll(match -> Matcher.quoteReplacement(protectedValues.get(Integer.parseInt(match.group(1))))));
        }
        return spans;
    }
}
