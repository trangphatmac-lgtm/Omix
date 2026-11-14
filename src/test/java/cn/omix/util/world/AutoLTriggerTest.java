package cn.omix.util.world;

import cn.omix.module.impl.world.AutoL;
import cn.omix.module.value.impl.ModeValue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;

import static org.junit.jupiter.api.Assertions.*;

class AutoLTriggerTest {
    private final UUID localPlayer = UUID.randomUUID();
    private final UUID otherPlayer = UUID.randomUUID();

    @Test
    void matchesEverySuppliedKeywordAsALiteralSubstring() throws ReflectiveOperationException {
        for (String word : List.of("=f", "=g", "=lz", "=贵", "外g", "外G", "s8", "S8", "s13", "S13",
                "冯", "木琴", "你冯", "糙", "举报", "挂", "是贵", "是桂", "你m", "沙壁", "东西",
                "lz", "纪狗", "G？", "G?", "g？", "g?", "外j", "贵？", "gua", "喜欢开", "也配", "ac", "占位符")) {
            assertTrue(matches("前面 " + word + " 后面"), word);
        }
    }

    @Test
    void preservesSuppliedCaseAndPunctuation() throws ReflectiveOperationException {
        assertFalse(matches("AC LZ GUA =F 外J"));
        assertFalse(matches("g G f 贵"));
        assertFalse(matches("hello world"));
        assertFalse(matches(""));
        assertTrue(matches("§cg§r?"));
    }

    @Test
    void ignoresOwnMessagesAndUnknownSenders() throws ReflectiveOperationException {
        assertFalse(matches(localPlayer, "挂 举报 g?"));
        assertFalse(matches(null, "挂"));
        assertFalse(matches(otherPlayer, null));
        assertTrue(matches("挂 举报 g?"));
    }

    @Test
    void parsesVanillaAndRankedServerChat() throws ReflectiveOperationException {
        assertChat("Player_1", "你开挂了", "<Player_1> 你开挂了");
        for (String separator : List.of(":", "：", "»", ">>", "›")) {
            assertChat("Player_1", "g?", "[全局] [MVP+] Player_1 " + separator + " g?");
        }
        assertChat("Player_1", "g?", " §a[VIP] §b<Player_1> §fg? §r");
    }

    @Test
    void checksOnlyTheBodyOfFormattedMessages() throws ReflectiveOperationException {
        var chat = parse("[ac] <s13> hello");
        assertNotNull(chat);
        assertFalse(matches(chat.group(3)));
        var ownChat = parse("[VIP] <Self> 挂");
        assertNotNull(ownChat);
        assertFalse(matches(localPlayer, ownChat.group(3)));
    }

    @Test
    void ignoresAnnouncementsEmptyMessagesAndMultilineText() throws ReflectiveOperationException {
        for (String message : List.of("举报成功", "[公告] 请勿开挂", "Player joined the game", "<Player> ",
                "<Player> hello\n<Other> 挂")) {
            assertNull(parse(message), message);
        }
        assertNull(parse(null));
    }

    @Test
    void defaultsToExistingTextRepliesAndRetainsExactModelIds() throws ReflectiveOperationException {
        AutoL module = new AutoL();
        assertEquals("Kill", mode(module, "mode").getValue());
        assertEquals("Text", mode(module, "replyMode").getValue());
        assertFalse(mode(module, "aiProvider").isVisible());
        mode(module, "mode").setValue("Trigger");
        mode(module, "replyMode").setValue("AI");
        assertTrue(mode(module, "aiProvider").isVisible());
        mode(module, "aiProvider").setValue("SavedProvider");
        mode(module, "aiModel").setValue("ExactModelID");
        assertEquals("ExactModelID", mode(module, "aiModel").getValue());
        assertFalse(mode(module, "aiModel").is("exactmodelid"));
        mode(module, "aiProvider").setValue("AnotherProvider");
        assertEquals("(Select)", mode(module, "aiModel").getValue());
    }

    @Test
    void lifecycleAndSelectionChangesCancelPendingAiWork() throws ReflectiveOperationException {
        AutoL module = new AutoL();
        mode(module, "mode").setValue("Trigger");
        mode(module, "replyMode").setValue("AI");
        var reply = new CompletableFuture<String>();
        var catalog = new CompletableFuture<>();
        setField(module, "aiReplyRequest", reply);
        setField(module, "aiCatalogRequest", catalog);
        module.onDisable();
        assertTrue(reply.isCancelled());
        assertTrue(catalog.isCancelled());

        for (String setting : List.of("mode", "replyMode", "aiProvider", "aiModel")) {
            var pending = new CompletableFuture<String>();
            setField(module, "aiReplyRequest", pending);
            mode(module, setting).setValue(switch (setting) {
                case "mode" -> "Kill";
                case "replyMode" -> "Text";
                default -> "Changed";
            });
            assertTrue(pending.isCancelled(), setting);
        }

        var pending = new CompletableFuture<String>();
        setField(module, "aiReplyRequest", pending);
        module.onWorld(null);
        assertTrue(pending.isCancelled());
    }

    private ModeValue mode(AutoL module, String name) throws ReflectiveOperationException {
        var field = AutoL.class.getDeclaredField(name);
        field.setAccessible(true);
        return (ModeValue) field.get(module);
    }

    private void setField(AutoL module, String name, Object value) throws ReflectiveOperationException {
        var field = AutoL.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(module, value);
    }

    private boolean matches(String message) throws ReflectiveOperationException {
        return matches(otherPlayer, message);
    }

    private boolean matches(UUID sender, String message) throws ReflectiveOperationException {
        var method = AutoL.class.getDeclaredMethod("matchesTrigger", UUID.class, UUID.class, String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, sender, localPlayer, message);
    }

    private Matcher parse(String message) throws ReflectiveOperationException {
        var method = AutoL.class.getDeclaredMethod("parseTriggerMessage", String.class);
        method.setAccessible(true);
        return (Matcher) method.invoke(null, message);
    }

    private void assertChat(String sender, String content, String message) throws ReflectiveOperationException {
        Matcher chat = parse(message);
        assertNotNull(chat);
        assertEquals(sender, chat.group(1) == null ? chat.group(2) : chat.group(1));
        assertEquals(content, chat.group(3));
    }
}
