package cn.omix.util.player;

import cn.omix.util.IMinecraft;
import lombok.experimental.UtilityClass;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

@UtilityClass
public class RayCastUtil implements IMinecraft {

    public boolean overBlock(BlockPos pos, Direction side, boolean strict) {
        if (mc.player == null || mc.world == null || mc.crosshairTarget == null) return false;
        if (!(mc.crosshairTarget instanceof BlockHitResult hit)) return false;
        if (!hit.getBlockPos().equals(pos)) return false;
        return !strict || hit.getSide() == side;
    }

    public boolean overBlock(BlockPos pos, Direction side, boolean strict, float yaw, float pitch, double range) {
        BlockHitResult hit = raycastBlock(yaw, pitch, range);
        if (hit == null || !hit.getBlockPos().equals(pos)) return false;
        return !strict || hit.getSide() == side;
    }

    public BlockHitResult raycastBlock(float yaw, float pitch, double range) {
        if (mc.player == null || mc.world == null) return null;

        Vec3d start = mc.player.getEyePos();
        Vec3d direction = RotationUtil.getVectorForRotation(yaw, pitch);
        Vec3d end = start.add(direction.multiply(range));
        RaycastContext context = new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                mc.player
        );
        return mc.world.raycast(context);
    }

    public boolean overEntity(Entity target) {
        if (mc.player == null || mc.world == null || mc.crosshairTarget == null) return false;
        if (!(mc.crosshairTarget instanceof EntityHitResult hit)) return false;
        return hit.getEntity().equals(target);
    }
}
