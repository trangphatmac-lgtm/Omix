package cn.remix.module.impl.render;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.Render3DEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.render.Render3D;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.entity.Entity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.TridentItem;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class Trajectories extends Module {
    private static final int MAX_STEPS = 300;

    private final NumberValue opacity = new NumberValue("Opacity", 100, 0, 100, 1);
    private final BoolValue bow = new BoolValue("Bow", true);
    private final BoolValue projectiles = new BoolValue("Projectiles", true);
    private final BoolValue pearls = new BoolValue("Pearls", true);
    private final BoolValue tridents = new BoolValue("Tridents", true);
    private final BoolValue crossbows = new BoolValue("Crossbows", true);

    public Trajectories() {
        super("Trajectories", Category.Render);
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (mc.player == null
                || mc.world == null
                || !mc.options.getPerspective().isFirstPerson()) {
            return;
        }

        ItemStack stack = getTrajectoryStack(mc.player);
        List<ProjectileSettings> settings = getSettings(stack);
        if (settings.isEmpty()) return;

        for (ProjectileSettings projectileSettings : settings) {
            Prediction prediction = predictTrajectory(mc.player, projectileSettings, event.getTickDelta());
            if (prediction.points().size() < 2) continue;

            Color color = new Color(
                    prediction.hitEntity() ? 85 : 255,
                    255,
                    prediction.hitEntity() ? 85 : 255,
                    Math.round(opacity.getValue() / 100.0F * 255.0F)
            );

            for (int i = 1; i < prediction.points().size(); i++) {
                Render3D.drawLine(event, prediction.points().get(i - 1), prediction.points().get(i), color);
            }

            if (prediction.hitPos() != null) {
                drawHitMarker(event, prediction.hitPos(), prediction.hitSide(), color);
            }
        }
    }

    private ItemStack getTrajectoryStack(ClientPlayerEntity player) {
        if (player.isUsingItem()) {
            return player.getStackInHand(player.getActiveHand());
        }

        ItemStack mainHand = player.getMainHandStack();
        if (!getSettings(mainHand).isEmpty()) {
            return mainHand;
        }
        return player.getStackInHand(Hand.OFF_HAND);
    }

    private List<ProjectileSettings> getSettings(ItemStack stack) {
        if (stack.isEmpty() || mc.player == null) return List.of();

        Item item = stack.getItem();
        if (item instanceof BowItem && bow.getValue()) {
            if (!mc.player.isUsingItem()) return List.of();

            float charge = BowItem.getPullProgress(mc.player.getItemUseTime());
            if (charge < 0.1F) return List.of();
            return List.of(new ProjectileSettings(charge * 3.0F, 0.05F, 0.99F, 0.3F, 0.0F));
        }

        if (item instanceof CrossbowItem && crossbows.getValue()) {
            if (CrossbowItem.isCharged(stack)) {
                ChargedProjectilesComponent charged = stack.getOrDefault(
                        DataComponentTypes.CHARGED_PROJECTILES,
                        ChargedProjectilesComponent.DEFAULT
                );
                return getCrossbowSettings(charged.getProjectiles());
            }

            if (!mc.player.isUsingItem()) return List.of();

            float charge = Math.min(1.0F,
                    (float) mc.player.getItemUseTime() / CrossbowItem.getPullTime(stack, mc.player));
            if (charge < 0.1F) return List.of();
            return List.of(new ProjectileSettings(charge * 3.15F, 0.05F, 0.99F, 0.3F, 0.0F));
        }

        if (item instanceof TridentItem && tridents.getValue()) {
            if (!mc.player.isUsingItem() || mc.player.getItemUseTime() < 10) return List.of();
            return List.of(new ProjectileSettings(2.5F, 0.05F, 0.99F, 0.3F, 0.0F));
        }

        if (item instanceof FishingRodItem && projectiles.getValue()) {
            return List.of(new ProjectileSettings(1.5F, 0.04F, 0.92F, 0.25F, 0.0F));
        }

        if (stack.isOf(Items.SNOWBALL)
                || stack.isOf(Items.EGG)
                || stack.isOf(Items.EXPERIENCE_BOTTLE)
                || stack.isOf(Items.SPLASH_POTION)
                || stack.isOf(Items.LINGERING_POTION)) {
            if (!projectiles.getValue()) return List.of();

            if (stack.isOf(Items.EXPERIENCE_BOTTLE)) {
                return List.of(new ProjectileSettings(0.7F, 0.07F, 0.99F, 0.25F, 0.0F));
            }
            if (stack.isOf(Items.SPLASH_POTION) || stack.isOf(Items.LINGERING_POTION)) {
                return List.of(new ProjectileSettings(0.5F, 0.05F, 0.99F, 0.25F, 0.0F));
            }
            return List.of(new ProjectileSettings(1.5F, 0.03F, 0.99F, 0.25F, 0.0F));
        }

        if (stack.isOf(Items.ENDER_PEARL) && pearls.getValue()) {
            return List.of(new ProjectileSettings(1.5F, 0.03F, 0.99F, 0.25F, 0.0F));
        }

        return List.of();
    }

    private List<ProjectileSettings> getCrossbowSettings(List<ItemStack> projectiles) {
        if (projectiles.isEmpty()) return List.of();

        List<ProjectileSettings> result = new ArrayList<>(projectiles.size());
        for (int i = 0; i < projectiles.size(); i++) {
            boolean firework = projectiles.get(i).isOf(Items.FIREWORK_ROCKET);
            float offset = projectiles.size() == 1
                    ? 0.0F
                    : (i - (projectiles.size() - 1) / 2.0F) * 10.0F;
            result.add(firework
                    ? new ProjectileSettings(1.6F, 0.0F, 0.99F, 0.3F, offset)
                    : new ProjectileSettings(3.15F, 0.05F, 0.99F, 0.3F, offset));
        }
        return result;
    }

    private Prediction predictTrajectory(ClientPlayerEntity player, ProjectileSettings settings, float tickDelta) {
        float yaw = player.getYaw(tickDelta);
        float pitch = player.getPitch(tickDelta);
        Vec3d position = player.getCameraPosVec(tickDelta).subtract(
                Math.cos(Math.toRadians(yaw)) * 0.16,
                0.1,
                Math.sin(Math.toRadians(yaw)) * 0.16
        );
        Vec3d velocity = Vec3d.fromPolar(pitch, yaw + settings.yawOffset())
                .normalize()
                .multiply(settings.velocity());

        List<Vec3d> points = new ArrayList<>();
        points.add(position);
        Vec3d hitPos = null;
        Direction hitSide = null;
        boolean hitEntity = false;

        for (int step = 0; step < MAX_STEPS && position.y > -80.0; step++) {
            Vec3d next = position.add(velocity);
            BlockHitResult blockHit = mc.world.raycast(new RaycastContext(
                    position,
                    next,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    player
            ));
            if (blockHit.getType() != HitResult.Type.MISS) {
                next = blockHit.getPos();
                hitPos = next;
                hitSide = blockHit.getSide();
            }

            EntityHit entityHit = raycastEntities(player, position, next, settings.hitboxExpand());
            if (entityHit != null) {
                next = entityHit.pos();
                hitPos = next;
                hitSide = null;
                hitEntity = true;
            }

            points.add(next);
            if (hitPos != null) break;

            position = next;
            velocity = velocity.multiply(mc.world.getFluidState(BlockPos.ofFloored(position)).isEmpty()
                    ? settings.drag()
                    : 0.6);
            velocity = velocity.subtract(0.0, settings.gravity(), 0.0);
        }

        return new Prediction(points, hitPos, hitSide, hitEntity);
    }

    private EntityHit raycastEntities(ClientPlayerEntity player, Vec3d start, Vec3d end, float hitboxExpand) {
        Box sweep = new Box(start, end).expand(1.0);
        return mc.world.getOtherEntities(
                        player,
                        sweep,
                        entity -> entity.canHit() && entity.isAlive() && !entity.isSpectator()
                ).stream()
                .map(entity -> {
                    Optional<Vec3d> intercept = entity.getBoundingBox().expand(hitboxExpand).raycast(start, end);
                    return intercept.map(pos -> new EntityHit(entity, pos)).orElse(null);
                })
                .filter(Objects::nonNull)
                .min(Comparator.comparingDouble(hit -> hit.pos().squaredDistanceTo(start)))
                .orElse(null);
    }

    private void drawHitMarker(Render3DEvent event, Vec3d center, Direction side, Color color) {
        double size = 0.25;
        if (side != null && side.getAxis() == Direction.Axis.Y) {
            Render3D.drawLine(event, center.add(-size, 0.0, -size), center.add(size, 0.0, size), color);
            Render3D.drawLine(event, center.add(-size, 0.0, size), center.add(size, 0.0, -size), color);
        } else if (side != null && side.getAxis() == Direction.Axis.X) {
            Render3D.drawLine(event, center.add(0.0, -size, -size), center.add(0.0, size, size), color);
            Render3D.drawLine(event, center.add(0.0, -size, size), center.add(0.0, size, -size), color);
        } else {
            Render3D.drawLine(event, center.add(-size, -size, 0.0), center.add(size, size, 0.0), color);
            Render3D.drawLine(event, center.add(-size, size, 0.0), center.add(size, -size, 0.0), color);
        }
    }

    private record ProjectileSettings(
            float velocity,
            float gravity,
            float drag,
            float hitboxExpand,
            float yawOffset
    ) {}

    private record Prediction(List<Vec3d> points, Vec3d hitPos, Direction hitSide, boolean hitEntity) {}

    private record EntityHit(Entity entity, Vec3d pos) {}
}
