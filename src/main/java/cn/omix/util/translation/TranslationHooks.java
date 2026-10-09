package cn.omix.util.translation;

import cn.omix.Client;
import cn.omix.module.impl.render.InGameTranslation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

/** Narrow display hooks; never replace game-state Text objects or the global TextRenderer. */
public final class TranslationHooks {
    private TranslationHooks() {}
    private static TranslationController active() {
        if (Client.instance == null || Client.instance.getModuleManager() == null) return null;
        var module = Client.instance.getModuleManager().getModule(InGameTranslation.class);
        return module != null && module.isNativeBehaviorActive() ? module.getController() : null;
    }
    public static Text received(Text text) { var controller = active(); if (controller != null) controller.received(text); return text; }
    public static void protectPlayer(String name) { var controller = active(); if (controller != null) controller.protectPlayer(name); }
    public static Text chat(Text text) { var controller = active(); return controller == null ? text : controller.chat(text); }
    public static Text scoreboard(Text text) { var controller = active(); return controller == null ? text : controller.scoreboard(text); }
    public static Text nameTag(Text text, Entity entity) {
        var controller = active();
        return controller == null ? text : controller.nameTag(text, entity instanceof PlayerEntity ? entity.getName().getString() : null);
    }
    public static Text customNameTag(Text text, String identity) {
        var controller = active(); return controller == null ? text : controller.nameTag(text, identity);
    }
}
