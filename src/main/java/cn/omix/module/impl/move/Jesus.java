package cn.omix.module.impl.move;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.BlockCollisionEvent;
import cn.omix.event.impl.UpdateEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.ModeValue;
import cn.omix.util.player.MovementUtil;
import net.minecraft.block.BlockState;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

public final class Jesus extends Module {
    private static final VoxelShape WATER_SHAPE = VoxelShapes.cuboid(
            0.0, 0.0, 0.0,
            1.0, 0.9999, 1.0
    );

    private final ModeValue mode = new ModeValue(
            "Mode", "Vanilla", "Vanilla", "Verus", "Bouncy", "Mini Jump"
    );

    private boolean wasInLiquid;

    public Jesus() {
        super("Jesus", Category.Move);
    }

    @Override
    public void onDisable() {
        wasInLiquid = false;
    }

    @EventTarget
    public void onBlockCollision(BlockCollisionEvent event) {
        if (mc.player == null
                || mc.world == null
                || !usesSolidCollision()
                || !isLiquid(event.getState())) {
            return;
        }

        Entity vehicle = mc.player.getVehicle();
        boolean canStandOnLiquid = isLiquidBelow(mc.player, false)
                || vehicle != null && isLiquidBelow(vehicle, false) && vehicle.fallDistance < 3.0;

        if (!mc.player.isSneaking()
                && mc.player.fallDistance < 3.0
                && !isLiquidAtFeet(mc.player)
                && canStandOnLiquid
                && mc.player.getY() >= event.getPos().getY()) {
            event.setShape(WATER_SHAPE);
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        setSuffix(mode.getValue());
        if (mc.player == null || mc.world == null) {
            wasInLiquid = false;
            return;
        }

        switch (mode.getValue()) {
            case "Verus" -> {
                if (!mc.player.isTouchingWater()) return;
                MovementUtil.strafe(mc.player.hasStatusEffect(StatusEffects.SPEED) ? 0.38 : 0.33);
            }
            case "Bouncy" -> handleWaterMode(0.6, true);
            case "Mini Jump" -> handleWaterMode(0.2, false);
            default -> {
            }
        }
    }

    private void handleWaterMode(double liquidBoost, boolean bouncy) {
        if (mc.player.isSneaking()) return;

        if (isLiquidAtFeet(mc.player) && !isSneakInputDown()) {
            setVelocityY(0.1);
            return;
        }

        if (mc.player.isOnGround() || mc.player.isClimbing()) {
            wasInLiquid = false;
        }

        double motionY = mc.player.getVelocity().y;
        if (motionY > 0.0 && wasInLiquid) {
            if (motionY < 0.03) {
                pushVelocityY(0.06713);
            } else if (motionY <= 0.05) {
                if (bouncy) scaleVelocityY(1.20000000999);
                pushVelocityY(0.06);
            } else if (motionY <= 0.08) {
                if (bouncy) scaleVelocityY(1.20000003);
                pushVelocityY(0.055);
            } else if (motionY <= 0.112) {
                pushVelocityY(0.0535);
            } else {
                scaleVelocityY(bouncy ? 1.000000000002 : 0.500000000002);
                pushVelocityY(0.0517);
            }
        }

        motionY = mc.player.getVelocity().y;
        if (wasInLiquid && motionY < 0.0 && motionY > -0.3) {
            pushVelocityY(0.045835);
        }

        mc.player.fallDistance = 0.0;
        if (!isLiquidBelow(mc.player, true)) return;

        setVelocityY(liquidBoost);
        wasInLiquid = true;
    }

    private void setVelocityY(double y) {
        Vec3d velocity = mc.player.getVelocity();
        mc.player.setVelocity(velocity.x, y, velocity.z);
    }

    private void pushVelocityY(double y) {
        mc.player.addVelocity(0.0, y, 0.0);
    }

    private void scaleVelocityY(double multiplier) {
        Vec3d velocity = mc.player.getVelocity();
        mc.player.setVelocity(velocity.x, velocity.y * multiplier, velocity.z);
    }

    private boolean usesSolidCollision() {
        return mode.is("Vanilla") || mode.is("Bouncy") || mode.is("Mini Jump");
    }

    private boolean isSneakInputDown() {
        InputUtil.Key key = InputUtil.fromTranslationKey(mc.options.sneakKey.getBoundKeyTranslationKey());
        boolean physicallyPressed = key.getCategory() == InputUtil.Type.KEYSYM
                && InputUtil.isKeyPressed(mc.getWindow(), key.getCode());
        return physicallyPressed || mc.options.sneakKey.isPressed();
    }

    private boolean isLiquidBelow(Entity entity, boolean shallow) {
        if (entity == null) return false;

        double offset = shallow ? 0.03 : entity instanceof PlayerEntity ? 0.2 : 0.5;
        return isLiquidAt(entity, entity.getY() - offset, true);
    }

    private boolean isLiquidAtFeet(Entity entity) {
        return entity != null && isLiquidAt(entity, entity.getY() + 0.01, false);
    }

    private boolean isLiquidAt(Entity entity, double y, boolean floorY) {
        if (mc.world == null) return false;

        Box box = entity.getBoundingBox();
        int minX = MathHelper.floor(box.minX);
        int maxX = MathHelper.ceil(box.maxX);
        int minZ = MathHelper.floor(box.minZ);
        int maxZ = MathHelper.ceil(box.maxZ);
        int blockY = floorY ? MathHelper.floor(y) : (int) y;
        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int x = minX; x < maxX; x++) {
            for (int z = minZ; z < maxZ; z++) {
                pos.set(x, blockY, z);
                if (isLiquid(mc.world.getBlockState(pos))) return true;
            }
        }

        return false;
    }

    private boolean isLiquid(BlockState state) {
        return state.getFluidState().isIn(FluidTags.WATER)
                || state.getFluidState().isIn(FluidTags.LAVA);
    }
}
