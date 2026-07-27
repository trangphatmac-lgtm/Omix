package cn.omix.module.impl.player;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.UpdateEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.util.Util;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import org.lwjgl.glfw.GLFW;

public final class MCF extends Module {
    private boolean middlePressed = false;

    public MCF() {
            super("MCF", Category.Player);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        boolean middlePressed = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;

        if (middlePressed && !this.middlePressed) {
            if (mc.crosshairTarget instanceof EntityHitResult entityHit) {
                Entity entity = entityHit.getEntity();

                if (entity instanceof PlayerEntity player) {
                    String playerName = player.getName().getString();

                    if (instance.getFriendManager().isFriend(playerName)) {
                        instance.getFriendManager().removeFriend(playerName);
                        Util.log("Deleted: " + playerName);
                    } else {
                        instance.getFriendManager().addFriend(playerName);
                        Util.log("Added: " + playerName);
                    }
                }
            }
        }

        this.middlePressed = middlePressed;
    }
}
