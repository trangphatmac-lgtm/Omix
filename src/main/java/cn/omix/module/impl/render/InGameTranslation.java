package cn.omix.module.impl.render;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.TickEvent;
import cn.omix.event.impl.WorldEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.module.value.impl.ModeValue;
import cn.omix.util.translation.TranslationController;
import lombok.Getter;

@Getter
public final class InGameTranslation extends Module {
    public static final String UNSELECTED = "(Select)";
    private final ModeValue language = new ModeValue("Language", "Simplified Chinese", "Simplified Chinese", "Traditional Chinese",
            "English", "Japanese", "Korean", "Russian", "German", "French", "Spanish", "Portuguese", "Italian", "Turkish", "Indonesian");
    private final ModeValue provider = new ModeValue("Provider", "(Select)", "(Select)").dynamic();
    private final ModeValue model = new ModeValue("Model", "(Select)", "(Select)").dynamic();
    private final BoolValue chat = new BoolValue("Chat", true);
    private final ModeValue chatDisplay = new ModeValue("Chat Display", "Bilingual", chat::getValue, "Translated", "Bilingual");
    private final BoolValue scoreboard = new BoolValue("Scoreboard", true);
    private final ModeValue scoreboardDisplay = new ModeValue("Scoreboard Display", "Translated", scoreboard::getValue, "Translated", "Bilingual");
    private final BoolValue nameTags = new BoolValue("Name Tags", true);
    private final ModeValue nameTagsDisplay = new ModeValue("Name Tags Display", "Translated", nameTags::getValue, "Translated", "Bilingual");
    private TranslationController controller;

    public InGameTranslation() {
        super("Translation", Category.Render);
        provider.onChange((old, next) -> {
            model.setValue(UNSELECTED);
            if (controller != null) controller.providerChanged();
        });
    }
    @Override public void onEnable() {
        if (controller == null) controller = new TranslationController(this);
        controller.enable();
    }
    @Override public void onDisable() { if (controller != null) controller.disable(); setSuffix(""); }
    @EventTarget public void onTick(TickEvent event) { if (controller != null) controller.tick(); }
    @EventTarget public void onWorld(WorldEvent event) { if (controller != null) controller.worldChanged(); }
    public void close() { if (controller != null) controller.close(); }
}
