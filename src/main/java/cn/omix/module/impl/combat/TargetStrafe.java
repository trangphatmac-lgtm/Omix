package cn.omix.module.impl.combat;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.LivingUpdateEvent;
import cn.omix.event.impl.MoveEvent;
import cn.omix.event.impl.MoveInputEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.impl.world.Scaffold;
import cn.omix.module.impl.world.ScaffoldX;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.module.value.impl.ModeValue;
import cn.omix.module.value.impl.NumberValue;
import cn.omix.util.player.EntityUtil;
import cn.omix.util.player.MovementUtil;
import cn.omix.util.player.RotationUtil;
import lombok.Getter;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

@Getter
public final class TargetStrafe extends Module {
    private final ModeValue mode = new ModeValue("Mode", "Adaptive", "Adaptive", "Behind");
    private final NumberValue distance = new NumberValue("Distance", 2, .5, 4.5, .1);
    private final NumberValue points = new NumberValue("Points", 12, 3, 16, 1);
    private final BoolValue space = new BoolValue("Require space key", false);
    private final BoolValue auto3rdPerson = new BoolValue("Auto 3rd Person", false);
    private final BoolValue legit = new BoolValue("Legit", false);
    private final BoolValue silentAim = new BoolValue("Silent Aim", false, legit::getValue);
    private Perspective perspective = Perspective.FIRST_PERSON;
    private boolean f5 = false;
    private int direction = 1;
    private float[] rotations = null;

    public TargetStrafe() {
        super("TargetStrafe", Category.Combat);
    }

    @Override
    public void onDisable() {
        rotations = null;
        resetPerspective();
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        rotations = null;
        if (mc.player == null || mc.options == null || check()) return;

        Entity target = getTarget();
        if (target == null) return;

        updateDirection();
        if (!legit.getValue()) return;

        Vec3d goal = getGoal(target);
        if (goal != null) {
            float[] goalRotations = RotationUtil.getRotations(goal);
            rotations = new float[]{goalRotations[0], mc.player.getPitch()};
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (!isLegitRotationActive()) return;
        if (event.getForward() == 0.0F && event.getStrafe() == 0.0F) return;

        // Keep the movement server-valid: vanilla forward input follows the
        // Goal rotation selected by RotationManager instead of rewriting X/Z.
        event.setForward(1.0F);
        event.setStrafe(0.0F);
    }

    @EventTarget
    public void onMove(MoveEvent event) {
        if (mc.player == null || mc.options == null || check()) return;

        if (auto3rdPerson.getValue()) {
            if (!f5 && mc.options.getPerspective() == Perspective.FIRST_PERSON) {
                perspective = mc.options.getPerspective();
                mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                f5 = true;
            }
        }

        Entity target = getTarget();
        if (target == null) {
            resetPerspective();
            return;
        }

        if (legit.getValue()) return;

        Vec3d goal = getGoal(target);
        if (goal == null) return;

        double diffX = goal.x - mc.player.getX();
        double diffZ = goal.z - mc.player.getZ();

        double speed = MovementUtil.getSpeed();
        double yaw = Math.atan2(diffZ, diffX);
        double motionX = speed * Math.cos(yaw);
        double motionZ = speed * Math.sin(yaw);

        if (EntityUtil.isOverVoid(mc.player.getX() + motionX, mc.player.getY(), mc.player.getZ() + motionZ)) {
            direction = -direction;
            return;
        }

        event.setX(motionX);
        event.setZ(motionZ);
    }

    private void updateDirection() {
        if (mc.options.leftKey.isPressed()) {
            direction = 1;
        } else if (mc.options.rightKey.isPressed()) {
            direction = -1;
        }

        if (mc.player.horizontalCollision) {
            direction = -direction;
        }
    }

    private Vec3d getGoal(Entity target) {
        if (mc.player == null || target == null) return null;
        double dist = Math.max(.1, distance.getValue().doubleValue());

        if (mode.is("Behind")) {
            double yaw = Math.toRadians(target.getYaw() + 180);
            return new Vec3d(target.getX() - Math.sin(yaw) * dist, target.getY(), target.getZ() + Math.cos(yaw) * dist);
        }

        double currentAngle = Math.atan2(mc.player.getZ() - target.getZ(), mc.player.getX() - target.getX());
        double angleStep = (Math.PI * 2.0) / points.getValue().intValue();
        double nextAngle = currentAngle + (direction * angleStep);
        return new Vec3d(target.getX() + Math.cos(nextAngle) * dist, target.getY(), target.getZ() + Math.sin(nextAngle) * dist);
    }

    public Entity getTarget() {
        Aura aura = getModule(Aura.class);
        return aura.isEnabled() ? aura.getTarget() : null;
    }

    public boolean isLegitRotationActive() {
        return isEnabled() && legit.getValue() && rotations != null && getTarget() != null;
    }

    private boolean check() {
        if (mc.player == null) return true;
        setSuffix(mode.getValue());
        if (getModule(ScaffoldX.class).isEnabled() || getModule(Scaffold.class).isEnabled()) return true;
        return space.getValue() && !mc.options.jumpKey.isPressed();
    }

    private void resetPerspective() {
        if (f5 && mc.options != null) {
            mc.options.setPerspective(perspective);
            f5 = false;
        }
    }
}
