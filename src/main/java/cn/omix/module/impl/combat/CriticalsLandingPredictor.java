package cn.omix.module.impl.combat;

import cn.omix.util.player.MovementUtil;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Short airborne prediction using Minecraft's block/entity collision resolution. */
final class CriticalsLandingPredictor {
    private CriticalsLandingPredictor() { }

    static boolean willLand(ClientPlayerEntity player, int ticks) {
        var world = player.getEntityWorld();
        Box box = player.getBoundingBox();
        Vec3d velocity = player.getVelocity();
        float sideways = player.input == null ? 0F : player.input.getMovementInput().x;
        float forward = player.input == null ? 0F : player.input.getMovementInput().y;
        Vec3d input = MovementUtil.movementInputToVelocity(new Vec3d(sideways * .98F, 0, forward * .98F),
                player.isSprinting() ? .026F : .02F, player.getYaw());

        for (int tick = 0; tick < ticks; tick++) {
            Vec3d requested = velocity.add(input);
            Vec3d movement = Entity.adjustMovementForCollisions(player, requested, box, world,
                    world.getEntityCollisions(player, box.stretch(requested)));
            boolean verticalCollision = !MathHelper.approximatelyEquals(requested.y, movement.y);
            box = box.offset(movement);
            if (requested.y < 0.0 && verticalCollision) return true;
            velocity = new Vec3d(
                    MathHelper.approximatelyEquals(requested.x, movement.x) ? requested.x * .91 : 0.0,
                    ((verticalCollision ? 0.0 : requested.y) - .08) * .98,
                    MathHelper.approximatelyEquals(requested.z, movement.z) ? requested.z * .91 : 0.0);
        }
        return false;
    }
}
