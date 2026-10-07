package cn.omix.util.opai.clickgui;

import cn.omix.module.Category;
import cn.omix.util.opai.bridge.Feature;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/** A panel owns its category independently of its position in the drawing order. */
public final class OpaiCategoryRows<R> {
    private final Category category;
    private final Function<R, Feature> module;
    private final Function<Feature, R> create;
    private final List<R> rows = new ArrayList<>();

    public OpaiCategoryRows(Category category, Function<R, Feature> module, Function<Feature, R> create) {
        this.category = category;
        this.module = module;
        this.create = create;
    }

    public List<R> rows() { return rows; }

    public void reconcile(List<Feature> modules) {
        rows.removeIf(row -> module.apply(row).getCategory() != category || !modules.contains(module.apply(row)));
        for (Feature feature : modules) {
            if (feature.getCategory() == category && rows.stream().noneMatch(row -> module.apply(row) == feature))
                rows.add(create.apply(feature));
        }
        rows.sort(Comparator.comparing(row -> module.apply(row).getName(), String.CASE_INSENSITIVE_ORDER));
    }
}
