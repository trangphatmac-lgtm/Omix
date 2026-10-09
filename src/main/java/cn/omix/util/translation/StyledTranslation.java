package cn.omix.util.translation;

import net.minecraft.text.*;
import java.util.*;

public record StyledTranslation(TranslationTemplate template, List<Style> styles) {
    public static StyledTranslation capture(Text text, Collection<String> playerNames) {
        var strings = new ArrayList<StringBuilder>(); var styles = new ArrayList<Style>();
        TextVisitFactory.visitFormatted(text, Style.EMPTY, (index, style, cp) -> {
            if (styles.isEmpty() || !styles.getLast().equals(style)) {
                styles.add(style); strings.add(new StringBuilder());
            }
            strings.getLast().appendCodePoint(cp); return true;
        });
        return new StyledTranslation(TranslationTemplate.of(strings.stream().map(StringBuilder::toString).toList(), playerNames), List.copyOf(styles));
    }
    public Text restore(String translation) {
        List<String> spans = template.restore(translation);
        MutableText result = Text.empty();
        for (int i = 0; i < spans.size(); i++) result.append(Text.literal(spans.get(i)).setStyle(styles.get(i)));
        return result;
    }
    public Text display(Text original, String translation, boolean bilingual, boolean chat) {
        Text translated = restore(translation);
        if (translated.getString().equals(original.getString()) || translation.equals(template.source())) return original;
        if (!bilingual) return translated;
        return Text.empty().append(original).append(Text.literal(chat ? "\n" : " · ")).append(translated);
    }
}
