package cn.omix.util.opai.clickgui;

import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.util.opai.bridge.Feature;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OpaiCategoryRowsTest {
    private record Row(Feature feature) { }
    private record Panel(Category category, OpaiCategoryRows<Row> group) { }
    private static Feature feature(String name, Category category) {
        return new Feature(new Module(name, category) { });
    }
    private static OpaiCategoryRows<Row> group(Category category) {
        return new OpaiCategoryRows<>(category, Row::feature, Row::new);
    }

    @Test void bringingAnyPanelToFrontNeverChangesItsModulesOrDuplicatesRows() {
        var modules = new ArrayList<Feature>();
        var panels = new ArrayList<Panel>();
        for (var category : Category.values()) {
            modules.add(feature(category.name() + " 中文模块", category));
            panels.add(new Panel(category, group(category)));
        }
        for (int pass = 0; pass < 30; pass++) {
            var clicked = panels.remove(pass % panels.size());
            panels.add(clicked);
            for (var panel : panels) {
                panel.group.reconcile(modules);
                assertEquals(1, panel.group.rows().size());
                assertEquals(panel.category, panel.group.rows().getFirst().feature.getCategory());
            }
        }
    }

    @Test void dynamicRegistrationRetainsExistingRowStateAndRemovesOnlyDepartedModules() {
        var kept = feature("B", Category.Combat);
        var removed = feature("C", Category.Combat);
        var unrelated = feature("Visual", Category.Render);
        var group = group(Category.Combat);
        group.reconcile(List.of(kept, removed, unrelated));
        var originalRow = group.rows().getFirst();
        var added = feature("A 中文", Category.Combat);
        kept.nativeModule.setHidden(true);
        group.reconcile(List.of(unrelated, added, kept));
        assertEquals(List.of(added, kept), group.rows().stream().map(Row::feature).toList());
        assertSame(originalRow, group.rows().getLast());
        // Discard any stale rows left by an older/category-mismatched view.
        group.rows().add(new Row(unrelated));
        group.reconcile(List.of(unrelated, added, kept));
        assertEquals(2, group.rows().size());
    }
}
