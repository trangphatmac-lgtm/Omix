package cn.omix.util.skeet;

import cn.omix.module.Module;
import cn.omix.module.value.Value;
import cn.omix.module.value.impl.*;

import java.util.ArrayList;
import java.util.List;

import static cn.omix.util.skeet.SkeetLayout.*;

/** Rebuilt from live modules on each layout pass, including script-provided modules and values. */
public final class SkeetModules {
    private SkeetModules() {}
    public record Group(Module module, Rect bounds, List<SkeetSettings.Row> rows) {
        public Rect enable() { return new Rect(bounds.x() + 6, bounds.y() + 9, bounds.width() - 77, 12); }
        public Rect bind() { return new Rect(bounds.x() + bounds.width() - 68, bounds.y() + 9, 62, 12); }
    }
    public record Layout(List<Group> groups, List<SkeetSettings.Row> rows, float height) {}

    public static List<Value> visibleValues(Module module) {
        return module.getValues().stream().filter(Value::isVisible).filter(value -> value instanceof BoolValue
                || value instanceof NumberValue || value instanceof ModeValue || value instanceof MultiBoolValue
                || value instanceof TextValue || value instanceof KeyValue || value instanceof ColorValue).toList();
    }

    public static Layout layout(List<Module> modules, float x, float y, float scroll) {
        List<Group> groups = new ArrayList<>();
        List<SkeetSettings.Row> allRows = new ArrayList<>();
        float[] columns = {6, 6};
        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            int column = i % 2;
            float gx = x + column * (GROUP_WIDTH + GAP), gy = y + columns[column] - scroll;
            float cy = 26;
            List<SkeetSettings.Row> rows = new ArrayList<>();
            for (Value value : visibleValues(module)) {
                float height = SkeetSettings.height(value);
                rows.add(new SkeetSettings.Row(value, new Rect(gx + 3, gy + cy, GROUP_WIDTH - 6, height)));
                cy += height + ROW_GAP;
            }
            float height = cy + 3;
            groups.add(new Group(module, new Rect(gx, gy, GROUP_WIDTH, height), List.copyOf(rows)));
            allRows.addAll(rows);
            columns[column] += height + 10;
        }
        return new Layout(List.copyOf(groups), List.copyOf(allRows), Math.max(columns[0], columns[1]));
    }
}
