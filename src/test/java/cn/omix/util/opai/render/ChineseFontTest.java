package cn.omix.util.opai.render;

import com.google.gson.JsonParser;
import java.awt.Font;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChineseFontTest {
    private static Font font(String resource) throws Exception {
        try (InputStream input = ChineseFontTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            return Font.createFont(Font.TRUETYPE_FONT, input);
        }
    }

    @Test void bundledFallbackCoversChineseLabelsAndPunctuation() throws Exception {
        String chinese = "中文分类配置测试繁體數字，。！？";
        assertEquals(-1, font(FontRepository.CJK_RESOURCE).canDisplayUpTo(chinese));
        // The original Latin design faces cannot serve as a CJK font by themselves.
        assertNotEquals(-1, font("assets/omix/opai/fonts/googlesans-medium.ttf").canDisplayUpTo(chinese));
    }

    @Test void nativeTargetFontUsesTheSameCjkAssetAfterTheOriginalLatinFace() throws Exception {
        try (var input = getClass().getClassLoader().getResourceAsStream("assets/omix/font/opai/google.json")) {
            assertNotNull(input);
            var providers = JsonParser.parseString(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("providers");
            assertEquals("omix:opai/google.ttf", providers.get(0).getAsJsonObject().get("file").getAsString());
            String file = providers.get(1).getAsJsonObject().get("file").getAsString();
            assertEquals(-1, font("assets/omix/font/" + file.substring("omix:".length())).canDisplayUpTo("目标玩家中文"));
        }
    }
}
