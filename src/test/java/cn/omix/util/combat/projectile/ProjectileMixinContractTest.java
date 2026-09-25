package cn.omix.util.combat.projectile;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** Check injection contracts against the actual mapped Minecraft dependency without booting it. */
class ProjectileMixinContractTest {
    private MethodNode method(String owner, String name) throws IOException {
        try (var input = getClass().getClassLoader().getResourceAsStream(owner + ".class")) {
            assertNotNull(input, owner);
            var type = new ClassNode();
            new ClassReader(input).accept(type, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return type.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
        }
    }

    private long calls(MethodNode method, String owner, String name, String descriptor) {
        return Arrays.stream(method.instructions.toArray()).filter(instruction -> instruction instanceof MethodInsnNode call
                && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(descriptor)).count();
    }

    @Test void inputHookRunsBeforeTheVanillaItemUseDecision() throws Exception {
        var method = method("net/minecraft/client/MinecraftClient", "handleInputEvents");
        assertTrue(calls(method, "net/minecraft/client/network/ClientPlayerEntity", "isUsingItem", "()Z") >= 1);
    }

    @Test void firstPersonStackAndEquipHooksMatchTheGameVersion() throws Exception {
        var render = method("net/minecraft/client/render/item/HeldItemRenderer", "renderFirstPersonItem");
        assertEquals("(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V", render.desc);
        var equip = method("net/minecraft/client/render/item/HeldItemRenderer", "updateHeldItems");
        assertEquals(1, calls(equip, "net/minecraft/client/network/ClientPlayerEntity", "getMainHandStack", "()Lnet/minecraft/item/ItemStack;"));
    }

    @Test void thirdPersonReplacesBothItemModelsAndBothCachedStacks() throws Exception {
        var method = method("net/minecraft/client/render/entity/state/ArmedEntityRenderState", "updateRenderState");
        assertEquals(4, calls(method, "net/minecraft/entity/LivingEntity", "getStackInArm", "(Lnet/minecraft/util/Arm;)Lnet/minecraft/item/ItemStack;"));
        assertEquals(1, calls(method, "net/minecraft/entity/LivingEntity", "getMainHandStack", "()Lnet/minecraft/item/ItemStack;"));
    }
}
