package cn.omix.module.impl.render;

import cn.omix.Client;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.TextValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class NickHider extends Module {
    private static final String ACCOUNT_MANAGER_PACKAGE = "me.ksyz.accountmanager.gui.";
    private static final ThreadLocal<Integer> BYPASS_DEPTH = ThreadLocal.withInitial(() -> 0);

    private final TextValue nickName = new TextValue("NickName", "Player");

    public NickHider() {
        super("NickHider", Category.Render);
    }

    public static String replace(String text) {
        Replacement replacement = getReplacement();
        if (replacement == null || text == null || !text.contains(replacement.accountName())) {
            return text;
        }
        return text.replace(replacement.accountName(), replacement.nickName());
    }

    public static StringVisitable replace(StringVisitable text) {
        Replacement replacement = getReplacement();
        if (replacement == null || text == null || !text.getString().contains(replacement.accountName())) {
            return text;
        }

        List<StringVisitable> parts = new ArrayList<>();
        text.visit((style, string) -> {
            parts.add(Text.literal(
                    string.replace(replacement.accountName(), replacement.nickName())
            ).setStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return StringVisitable.concat(parts);
    }

    public static OrderedText replace(OrderedText text) {
        Replacement replacement = getReplacement();
        if (replacement == null || text == null) return text;

        int[] accountCodePoints = replacement.accountName().codePoints().toArray();
        int[] nickCodePoints = replacement.nickName().codePoints().toArray();
        return visitor -> {
            List<StyledCodePoint> characters = new ArrayList<>();
            text.accept((index, style, codePoint) -> {
                characters.add(new StyledCodePoint(style, codePoint));
                return true;
            });

            int outputIndex = 0;
            for (int i = 0; i < characters.size(); ) {
                if (matches(characters, i, accountCodePoints)) {
                    Style style = characters.get(i).style();
                    for (int codePoint : nickCodePoints) {
                        if (!visitor.accept(outputIndex, style, codePoint)) return false;
                        outputIndex += Character.charCount(codePoint);
                    }
                    i += accountCodePoints.length;
                    continue;
                }

                StyledCodePoint character = characters.get(i++);
                if (!visitor.accept(outputIndex, character.style(), character.codePoint())) return false;
                outputIndex += Character.charCount(character.codePoint());
            }
            return true;
        };
    }

    public static void withoutHiding(Runnable action) {
        BYPASS_DEPTH.set(BYPASS_DEPTH.get() + 1);
        try {
            action.run();
        } finally {
            int depth = BYPASS_DEPTH.get() - 1;
            if (depth == 0) {
                BYPASS_DEPTH.remove();
            } else {
                BYPASS_DEPTH.set(depth);
            }
        }
    }

    private static boolean matches(List<StyledCodePoint> characters, int start, int[] expected) {
        if (expected.length == 0 || start + expected.length > characters.size()) return false;

        for (int i = 0; i < expected.length; i++) {
            if (characters.get(start + i).codePoint() != expected[i]) return false;
        }
        return true;
    }

    private static Replacement getReplacement() {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        if (minecraft == null || isBypassed(minecraft) || Client.instance == null
                || Client.instance.getModuleManager() == null) {
            return null;
        }

        NickHider module = Client.instance.getModuleManager().getModule(NickHider.class);
        if (module == null || !module.isEnabled()) return null;

        String accountName = minecraft.getSession().getUsername();
        String nickName = module.nickName.getValue();
        if (accountName == null || accountName.isEmpty() || nickName == null || accountName.equals(nickName)) {
            return null;
        }
        return new Replacement(accountName, nickName);
    }

    private static boolean isBypassed(MinecraftClient minecraft) {
        if (BYPASS_DEPTH.get() > 0) return true;
        return minecraft.currentScreen != null
                && minecraft.currentScreen.getClass().getName().startsWith(ACCOUNT_MANAGER_PACKAGE);
    }

    private record Replacement(String accountName, String nickName) {
    }

    private record StyledCodePoint(Style style, int codePoint) {
    }
}
