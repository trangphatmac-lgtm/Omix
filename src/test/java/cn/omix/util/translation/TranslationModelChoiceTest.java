package cn.omix.util.translation;

import cn.omix.module.value.impl.ModeValue;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TranslationModelChoiceTest {
    @Test void restoresExactIdBeforeCatalogAndRetainsUnavailableSelections() {
        var value = new ModeValue("Model", "(Select)", "(Select)").dynamic();
        value.setValue("MyModel"); value.replaceModes(List.of("(Select)", "OtherModel"));
        assertEquals("MyModel", value.getValue()); assertTrue(List.of(value.getModes()).contains("MyModel"));
        value.replaceModes(List.of("(Select)", "MyModel"));
        assertEquals(2, value.getModes().length); assertFalse(value.is("mymodel"));
    }
    @Test void ordinaryModeValidationAndListenersRemainCompatible() {
        var value = new ModeValue("Mode", "One", "One", "Two");
        value.setValue("invalid"); assertEquals("One", value.getValue());
        value.setValue("two"); assertEquals("Two", value.getValue());
        value.onChange((old, next) -> { throw new IllegalStateException("Rejected"); });
        assertThrows(IllegalStateException.class, () -> value.setValue("One")); assertEquals("Two", value.getValue());
        assertThrows(IllegalStateException.class, () -> value.replaceModes(List.of("Other")));
    }
}
