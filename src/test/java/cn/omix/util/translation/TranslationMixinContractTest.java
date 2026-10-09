package cn.omix.util.translation;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class TranslationMixinContractTest {
    private ClassNode type(String name) throws Exception {
        try (var input = getClass().getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(input); var node = new ClassNode(); new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG); return node;
        }
    }
    private long calls(ClassNode type, String owner, String name, String descriptor) {
        return type.methods.stream().flatMap(method -> Arrays.stream(method.instructions.toArray()))
                .filter(value -> value instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(descriptor)).count();
    }
    @Test void sidebarMapperAndTitleHooksMatchThePinnedMinecraftVersion() throws Exception {
        var hud = type("net/minecraft/client/gui/hud/InGameHud");
        assertEquals(1, calls(hud, "net/minecraft/scoreboard/Team", "decorateName", "(Lnet/minecraft/scoreboard/AbstractTeam;Lnet/minecraft/text/Text;)Lnet/minecraft/text/MutableText;"));
        assertTrue(hud.methods.stream().anyMatch(method -> method.name.equals("renderScoreboardSidebar") && method.desc.equals("(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V")));
    }
    @Test void receivedMessageHooksIncludeSignedAndDelayedSystemDelivery() throws Exception {
        var handler = type("net/minecraft/client/network/message/MessageHandler");
        assertEquals(2, calls(handler, "net/minecraft/client/gui/hud/ChatHud", "addMessage", "(Lnet/minecraft/text/Text;)V"));
        assertEquals(3, calls(handler, "net/minecraft/client/gui/hud/ChatHud", "addMessage", "(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V"));
        var hud = type("net/minecraft/client/gui/hud/ChatHud");
        assertTrue(hud.methods.stream().anyMatch(method -> method.name.equals("addVisibleMessage") && method.desc.equals("(Lnet/minecraft/client/gui/hud/ChatHudLine;)V")));
    }
}
