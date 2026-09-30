package cn.omix.util.skeet;

import cn.omix.Client;
import cn.omix.config.Config;
import cn.omix.config.impl.ModuleConfig;
import cn.omix.util.IMinecraft;
import cn.omix.util.render.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;

import java.util.List;

import static cn.omix.util.skeet.SkeetDraw.*;
import static cn.omix.util.skeet.SkeetLayout.*;

/** Local ConfigManager operations, retaining the existing ModuleConfig encryption format. */
public final class SkeetProfiles implements IMinecraft {
    private final SkeetTextField name = new SkeetTextField(128, false);
    private List<Config> configs = List.of();
    private String selected, status = "", deleteCandidate;
    private float scroll;
    private Rect left, right, list;

    public SkeetProfiles() { refresh(); }

    public void refresh() {
        configs = instance.getConfigManager().getConfigs().stream()
                .filter(config -> config.getFile().isFile() || config.getName().equalsIgnoreCase("Default"))
                .sorted(java.util.Comparator.comparing(Config::getName, String.CASE_INSENSITIVE_ORDER)).toList();
        if (selected == null || configs.stream().noneMatch(config -> config.getName().equalsIgnoreCase(selected))) {
            Config current = instance.getConfigManager().getCurrentConfig();
            selected = current == null ? null : current.getName();
        }
        deleteCandidate = null;
    }

    public void layout(float x, float y, float height) {
        left = new Rect(x, y, GROUP_WIDTH, 188);
        right = new Rect(x + GROUP_WIDTH + GAP, y, GROUP_WIDTH, height);
        list = new Rect(right.x() + 5, right.y() + 10, right.width() - 10, right.height() - 16);
        scroll = SkeetLayout.scroll(scroll, configs.size() * 17, list.height());
    }
    private Rect input() { return new Rect(left.x() + 10, left.y() + 25, left.width() - 20, 15); }
    private Rect button(int index) { return new Rect(left.x() + 10, left.y() + 47 + index * 22, left.width() - 20, 16); }
    private Config selectedConfig() { return configs.stream().filter(config -> config.getName().equalsIgnoreCase(selected)).findFirst().orElse(null); }

    public void draw(DrawContext c, SkeetDraw draw, float mx, float my) {
        draw.group(c, "Configuration", left);
        draw.text(c, "Config name", left.x() + 10, left.y() + 11, TEXT);
        name.draw(c, draw, input(), "New config...");
        String[] labels = {"Create", "Load", "Save", deleteCandidate == null ? "Delete" : "Confirm delete", "Refresh"};
        for (int i = 0; i < labels.length; i++) draw.button(c, labels[i], button(i), button(i).contains(mx, my), enabled(i));
        Config current = instance.getConfigManager().getCurrentConfig();
        draw.clipped(c, "Active: " + (current == null ? "None" : current.getName()), new Rect(left.x() + 10, left.y() + 158, left.width() - 20, 12), ACCENT);
        Config chosen = selectedConfig();
        String storage = chosen instanceof ModuleConfig module ? module.getStorageMode().displayName() : "none";
        draw.text(c, "Storage: " + storage, left.x() + 10, left.y() + 174, MUTED);
        draw.group(c, "Saved configs", right);
        SkeetDraw.scissor(c, list);
        for (int i = 0; i < configs.size(); i++) {
            Config config = configs.get(i);
            Rect row = new Rect(list.x(), list.y() + i * 17 - scroll, list.width() - 4, 17);
            if (!row.intersects(list)) continue;
            boolean chosenRow = config.getName().equalsIgnoreCase(selected);
            if (chosenRow || row.contains(mx, my)) draw.rect(c, row.x(), row.y(), row.width(), row.height(), chosenRow ? 0x30382a : 0x242424);
            draw.clipped(c, config.getName(), new Rect(row.x() + 4, row.y(), row.width() - 8, row.height()), chosenRow ? ACCENT : TEXT);
        }
        Render2D.endScissor(c);
        draw.scrollbar(c, list, configs.size() * 17, scroll);
        draw.clipped(c, status, new Rect(left.x(), right.y() + right.height() + 5, GROUP_WIDTH * 2 + GAP, 13), TEXT);
    }

    private boolean enabled(int action) {
        Config config = selectedConfig();
        return switch (action) {
            case 0 -> validName(name.value().strip());
            case 1, 2 -> config != null;
            case 3 -> config != null && !config.getName().equalsIgnoreCase("Default");
            default -> true;
        };
    }

    public boolean click(float mx, float my, int button) {
        if (button != 0) return true;
        name.focus(input().contains(mx, my));
        if (name.focused()) { deleteCandidate = null; return true; }
        if (list.contains(mx, my)) {
            int index = (int) ((my - list.y() + scroll) / 17);
            if (index >= 0 && index < configs.size()) selected = configs.get(index).getName();
            deleteCandidate = null; return true;
        }
        for (int i = 0; i < 5; i++) {
            if (button(i).contains(mx, my)) { if (enabled(i)) action(i); return true; }
        }
        deleteCandidate = null;
        return true;
    }

    private void action(int index) {
        var manager = instance.getConfigManager();
        if (index != 3) deleteCandidate = null;
        try {
            switch (index) {
                case 0 -> {
                    String requested = name.value().strip();
                    if (!validName(requested)) { status = "Choose a valid config name"; return; }
                    Config created = manager.createConfig(requested);
                    if (created == null) status = "A config with that name already exists";
                    else { selected = created.getName(); status = "Created: " + selected; name.set(""); }
                    refresh();
                }
                case 1 -> {
                    Config loaded = manager.loadConfig(selected);
                    status = loaded == null ? "Config is unavailable" : "Loaded: " + loaded.getName();
                }
                case 2 -> { manager.saveConfig(selected); status = "Saved: " + selected; refresh(); }
                case 3 -> {
                    if (!selected.equals(deleteCandidate)) {
                        deleteCandidate = selected; status = "Click Confirm delete to remove " + selected;
                    } else {
                        boolean deleted = manager.deleteConfig(selected);
                        status = deleted ? "Deleted: " + selected : "Could not delete config";
                        refresh();
                    }
                }
                case 4 -> { refresh(); status = "Config list refreshed"; }
            }
        } catch (RuntimeException error) {
            status = "Config operation failed; see client log";
            Client.logger.warn("Skeet config operation failed", error);
        }
    }

    public boolean key(KeyInput input) {
        if (!name.focused()) {
            if (deleteCandidate != null && input.key() == GLFW.GLFW_KEY_ESCAPE) { deleteCandidate = null; status = ""; return true; }
            return false;
        }
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) name.focus(false);
        else if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) action(0);
        else name.key(input);
        return true;
    }
    public boolean type(CharInput input) { return name.type(input); }
    public void blur() { name.focus(false); deleteCandidate = null; }
    public void scroll(float mx, float my, double amount) {
        if (list.contains(mx, my)) scroll = SkeetLayout.scroll(scroll - (float) amount * 34, configs.size() * 17, list.height());
    }

    public static boolean validName(String name) {
        return name != null && !name.isBlank() && name.length() <= 128 && name.equals(name.strip())
                && !name.endsWith(".") && !name.matches(".*[\\\\/:*?\"<>|\\p{Cntrl}].*")
                && !name.matches("(?i)(con|prn|aux|nul|com[1-9]|lpt[1-9])(\\..*)?");
    }
}
