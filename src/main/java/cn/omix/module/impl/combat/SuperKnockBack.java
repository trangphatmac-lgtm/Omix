package cn.omix.module.impl.combat;

import cn.omix.event.base.annotation.EventPriority;
import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.AttackEvent;
import cn.omix.event.impl.PlayerUpdateEvent;
import cn.omix.event.impl.WorldEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.LivingEntity;
import org.lwjgl.glfw.GLFW;

/** Southside's one-tick forward-key release after attacking a living entity. */
public final class SuperKnockBack extends Module {
    private int ticks;
    private boolean forwardReleased;

    public SuperKnockBack() {
        super("SuperKnockBack", Category.Combat);
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        reset();
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        reset();
    }

    @EventTarget
    @EventPriority(1000)
    public void onAttack(AttackEvent event) {
        if (!event.isCancelled() && mc.player != null && mc.world != null
                && event.getEntity() instanceof LivingEntity) {
            ticks = 2;
        }
    }

    @EventTarget
    public void onUpdate(PlayerUpdateEvent event) {
        if (mc.player == null || mc.world == null) {
            reset();
            return;
        }

        if (ticks == 2) {
            mc.options.forwardKey.setPressed(false);
            forwardReleased = true;
            ticks = 1;
        } else if (ticks == 1) {
            restoreForwardKey();
            ticks = 0;
        }
    }

    private void restoreForwardKey() {
        if (!forwardReleased) return;
        forwardReleased = false;

        InputUtil.Key key = InputUtil.fromTranslationKey(mc.options.forwardKey.getBoundKeyTranslationKey());
        boolean pressed = false;
        if (mc.isWindowFocused()) {
            if (key.getCategory() == InputUtil.Type.MOUSE) {
                pressed = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), key.getCode()) == GLFW.GLFW_PRESS;
            } else if (key.getCategory() == InputUtil.Type.KEYSYM && key.getCode() > 0) {
                pressed = InputUtil.isKeyPressed(mc.getWindow(), key.getCode());
            }
        }
        mc.options.forwardKey.setPressed(pressed);
    }

    private void reset() {
        restoreForwardKey();
        ticks = 0;
    }
}
