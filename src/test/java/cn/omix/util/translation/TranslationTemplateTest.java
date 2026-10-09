package cn.omix.util.translation;

import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TranslationTemplateTest {
    @Test void dynamicValuesShareTemplatesButRestoreCurrentValues() {
        var first = TranslationTemplate.of(List.of("Kills: 12 Time: 01:30 Alice"), List.of("Alice"));
        var next = TranslationTemplate.of(List.of("Kills: 15 Time: 00:59 Bob"), List.of("Bob"));
        assertEquals(first.source(), next.source());
        String translation = first.source().replace("Kills", "击杀").replace("Time", "时间");
        assertEquals(List.of("击杀: 15 时间: 00:59 Bob"), next.restore(translation));
    }
    @Test void protectsNamesAcrossStyleBoundariesAndUrlsCommandsAndLiteralMarkers() {
        var template = TranslationTemplate.of(List.of("Al", "ice Visit https://example.com/key-secret /help [[p8]]"), List.of("Alice"));
        assertFalse(template.source().contains("Alice"));
        assertFalse(template.source().contains("key-secret"));
        assertFalse(template.source().contains("/help"));
        assertEquals(List.of("Al", "ice Visit https://example.com/key-secret /help [[p8]]"), template.restore(template.source()));
    }
    @Test void rejectsMissingDuplicateReorderedAndUnstyledMarkers() {
        var template = TranslationTemplate.of(List.of("Kills: 2", "Time: 4"), List.of());
        String source = template.source();
        assertFalse(template.accepts(source.replace("[[p0]]", "")));
        assertFalse(template.accepts(source.replace("[[p0]]", "[[p0]][[p0]]")));
        assertFalse(template.accepts(source.replace("[[p0]]", "[[p9]]")));
        assertFalse(template.accepts("extra" + source));
        assertFalse(template.accepts(source + "\n"));
        assertThrows(IllegalArgumentException.class, () -> template.restore("broken"));
    }
    @Test void skipsNumericOrIdentifierOnlyLabelsAndBoundsLargeInputs() {
        assertFalse(TranslationTemplate.of(List.of("Alice 123 02:30"), List.of("Alice")).translatable());
        assertFalse(TranslationTemplate.of(List.of("x".repeat(8100)), List.of()).translatable());
    }
    @Test void preservesStyleAndInteractionLocallyWithoutSendingActions() {
        var event = new ClickEvent.SuggestCommand("/party Alice");
        var hover = new HoverEvent.ShowText(Text.literal("Private hover text"));
        Text source = Text.empty().append(Text.literal("Kills: ").formatted(Formatting.RED))
                .append(Text.literal("12").styled(style -> style.withColor(Formatting.GREEN).withClickEvent(event).withHoverEvent(hover)));
        var captured = StyledTranslation.capture(source, List.of("Alice"));
        assertFalse(captured.template().source().contains("party"));
        assertFalse(captured.template().source().contains("Private"));
        Text result = captured.restore(captured.template().source().replace("Kills", "击杀"));
        assertEquals("击杀: 12", result.getString());
        assertEquals(Formatting.RED.getColorValue(), result.getSiblings().getFirst().getStyle().getColor().getRgb());
        assertEquals(event, result.getSiblings().getLast().getStyle().getClickEvent());
        assertEquals(hover, result.getSiblings().getLast().getStyle().getHoverEvent());
        assertEquals("Kills: 12", source.getString());
        assertSame(source, captured.display(source, captured.template().source(), true, true));
        assertEquals("Kills: 12\n击杀: 12", captured.display(source, captured.template().source().replace("Kills", "击杀"), true, true).getString());
        assertEquals("Kills: 12 · 击杀: 12", captured.display(source, captured.template().source().replace("Kills", "击杀"), true, false).getString());
    }
}
