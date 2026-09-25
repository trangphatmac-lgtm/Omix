package cn.omix.util.sigma;

import cn.omix.Client;
import cn.omix.util.IMinecraft;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** UI positions have a separate namespace so switching module profiles never moves the panels. */
public final class SigmaGuiLayout implements IMinecraft {
    private SigmaGuiLayout() {}
    private static Path file() { return mc.runDirectory.toPath().resolve("Omix/sigma/clickgui.json"); }
    public static void read(List<SigmaCategoryPanel> panels) {
        try {
            Path file = file(); if (!Files.isRegularFile(file) || Files.size(file) > 65536) return;
            JsonObject object = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            for (SigmaCategoryPanel panel : panels) {
                if (!object.has(panel.category.name())) continue;
                JsonObject row = object.getAsJsonObject(panel.category.name());
                float x = row.get("x").getAsFloat(), y = row.get("y").getAsFloat();
                if (Float.isFinite(x) && Float.isFinite(y)) { panel.x = Math.clamp(x, 0, Math.max(0, SigmaDraw.width() - 200)); panel.y = Math.clamp(y, 0, Math.max(0, SigmaDraw.height() - 60)); }
            }
        } catch (Exception error) { Client.logger.debug("Cannot read Jello panel layout", error); }
    }
    public static void write(List<SigmaCategoryPanel> panels) {
        try {
            JsonObject object = new JsonObject();
            for (SigmaCategoryPanel panel : panels) { JsonObject row = new JsonObject(); row.addProperty("x", panel.x); row.addProperty("y", panel.y); object.add(panel.category.name(), row); }
            Path file = file(); Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling("clickgui.json.tmp");
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(object));
            try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException ignored) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } catch (Exception error) { Client.logger.debug("Cannot save Jello panel layout", error); }
    }
}
