package cn.omix.module.impl.combat;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.LivingUpdateEvent;
import cn.omix.event.impl.MotionEvent;
import cn.omix.event.impl.UpdateEvent;
import cn.omix.management.RotationManager;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.impl.world.ScaffoldX;
import cn.omix.module.impl.world.Scaffold;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.module.value.impl.ModeValue;
import cn.omix.module.value.impl.NumberValue;
import cn.omix.util.misc.MathUtil;
import cn.omix.util.misc.TimerUtil;
import cn.omix.util.network.PacketUtil;
import cn.omix.util.player.EntityUtil;
import cn.omix.util.player.RayCastUtil;
import cn.omix.util.player.RotationUtil;
import lombok.Getter;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Getter
public class Aura extends Module {
    private final ModeValue targetMode = new ModeValue("Target Mode", "Single", "Single", "Switch");
    private final NumberValue switchDelay = new NumberValue("Switch Delay", 200, 0, 1000, 50, () -> targetMode.is("Switch"));
    private final ModeValue priority = new ModeValue("Priority", "Distance", "Distance", "Health", "Fov", "LivingTime", "Armor");
    private final ModeValue attackMode = new ModeValue("Combat Mode", "1.8", "1.8", "1.9+");
    private final NumberValue maxCps = new NumberValue("Max CPS", 10, 1, 20, 1, () -> attackMode.is("1.8"));
    private final NumberValue minCps = new NumberValue("Min CPS", 7, 1, 20, 1, () -> attackMode.is("1.8"));
    private final BoolValue keepSwing = new BoolValue("Keep Swing", false, () -> attackMode.is("1.9+"));
    private final NumberValue attackRange = new NumberValue("Range", 3, 3, 8, .1);
    private final NumberValue blockRange = new NumberValue("Block Range", 4, 3, 8, .1);
    private final NumberValue wallRange = new NumberValue("Wall Range", 0, 0, 8, .1);
    private final NumberValue rotationRange = new NumberValue("Rotation Range", 4, 3, 8, .1);
    private final ModeValue autoBlockMode = new ModeValue("AutoBlock Mode", "None", "None", "Fake", "Use Item", "Vanilla");
    private final NumberValue rotationSpeed = new NumberValue("Rotation Speed", 180, 0, 180, 5);
    private final ModeValue movementFixMode = new ModeValue("MovementFix Mode", "None", "None", "Silent", "Strict");
    private final BoolValue rayCast = new BoolValue("Ray Cast", false);
    private final List<LivingEntity> targets = new ArrayList<>();
    private final TimerUtil switchTimer = new TimerUtil();
    private final TimerUtil attackTimer = new TimerUtil();
    private LivingEntity target = null;
    private float[] rotations = null;

    private boolean renderBlock = false;
    private boolean blocking = false;

    public Aura() {
        super("Aura", Category.Combat);
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        reset();
    }

    public void reset() {
        switchTimer.reset();
        attackTimer.reset();
        rotations = null;
        targets.clear();
        target = null;

        unBlock();
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || check()) return;

        updateTargets();
        if (targets.isEmpty()) {
            reset();
            return;
        }

        selectTarget();

        if (target != null) {
            if (canAttack(target)) {
                if (keepSwing.getValue()) {
                    mc.player.handSwinging = true;
                }

                if (mc.player.getAttackCooldownProgress(.5f) < 1 && attackMode.is("1.9+")) return;

                if (attackTimer.hasTimeElapsed(700L / getCps())) {
                    doAttack(target);
                    attackTimer.reset();
                }
            }
        }
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (mc.player == null || check()) return;

        if (event.isPost()) {
            if (target != null) {
                if (canBlock(target)) {
                    doBlock();
                } else {
                    unBlock();
                }
            }
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (mc.player == null || check()) return;

        if (target != null) {
            if (RotationUtil.getDistanceToEntity(target) <= rotationRange.getValue()) {
                rotations = RotationUtil.nearestRotation(target.getBoundingBox());
            }
        }
    }

    private void selectTarget() {
        if (target == null || targets.isEmpty()) {
            switchTimer.setTime(0);
        }

        if (targetMode.is("Switch")) {
            if (switchTimer.hasTimeElapsed(switchDelay.getValue().longValue())) {
                int index = 0;
                if (targets.size() > 1) {
                    index = (int) (Math.random() * targets.size());
                }
                target = targets.isEmpty() ? null : targets.get(index);
                switchTimer.reset();
            }
        } else {
            target = targets.isEmpty() ? null : targets.getFirst();
        }

        setSuffix(targetMode.getValue());
    }

    private boolean canAttack(LivingEntity target) {
        Vec3d bestPoint = RotationUtil.getNearestPointBB(target.getBoundingBox());
        boolean canSee = RotationUtil.isVisible(bestPoint);
        float range = canSee ? attackRange.getValue() : wallRange.getValue();

        if (rayCast.getValue() && !RayCastUtil.overEntity(target)) {
            return false;
        }

        return !(RotationUtil.getDistanceToEntity(target) > range);
    }

    public boolean canBlock(LivingEntity target) {
        if (autoBlockMode.is("None") || target == null) return false;

        if (!isHoldingSword()) return false;

        return RotationUtil.getDistanceToEntity(target) <= blockRange.getValue();
    }

    private void doAttack(LivingEntity entity) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        mc.interactionManager.attackEntity(mc.player, entity);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private void doBlock() {
        if (mc.player == null || mc.world == null || autoBlockMode.is("None") || target == null) return;

        switch (autoBlockMode.getValue()) {
            case "Use Item" :
                mc.options.useKey.setPressed(true);
                blocking = true;
                break;

            case "Vanilla" :
                PacketUtil.sendSequencedPacket(sequence -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, sequence, RotationManager.currentRotations[0], RotationManager.currentRotations[1]));
                blocking = true;
                break;
        }

        renderBlock = true;
    }

    private void unBlock() {
        if (autoBlockMode.is("None")) return;

        if (blocking) {
            switch (autoBlockMode.getValue()) {
                case "Use Item":
                    mc.options.useKey.setPressed(false);
                    blocking = false;
                    break;

                case "Vanilla":
                    PacketUtil.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, Direction.DOWN));
                    blocking = false;
                    break;
            }
        }

        renderBlock = false;
    }

    private void updateTargets() {
        if (mc.player == null || mc.world == null) return;

        targets.clear();
        for (LivingEntity entity : instance.getTargetManager().getTargets()) {
            if (filter(entity)) {
                targets.add(entity);
            }
        }

        if (!targets.isEmpty()) {
            targets.sort(sortTargets(priority.getValue()));
        }

        setSuffix(targetMode.getValue());
    }

    public Comparator<LivingEntity> sortTargets(final String priority) {
        return switch (priority) {
            case "Health" -> Comparator.comparingDouble(entity -> entity.getHealth() + entity.getAbsorptionAmount());
            case "Fov" -> Comparator.comparingDouble(RotationUtil::getRotationDifference);
            case "LivingTime" -> Comparator.comparingInt((LivingEntity entity) -> entity.age).reversed();
            case "Armor" -> Comparator.comparingInt(LivingEntity::getArmor);
            default -> Comparator.comparingDouble(RotationUtil::getDistanceToEntity);
        };
    }

    public boolean filter(LivingEntity entity) {
        if (mc.player == null || mc.world == null) return false;

        if (!EntityUtil.isSelected(entity)) {
            return false;
        }

        if (RotationUtil.getDistanceToEntity(entity) > rotationRange.getValue()) {
            return false;
        }

        return !entity.isDead() && entity.isAlive() && entity.getHealth() > 0;
    }

    private boolean check() {
        if (mc.player == null || mc.world == null) return true;

        if ((getModule(ScaffoldX.class).isEnabled() && getModule(ScaffoldX.class).isCanRotation())
                || (getModule(Scaffold.class).isEnabled() && getModule(Scaffold.class).isCanRotation())) {
            return true;
        }

        return !mc.player.isAlive() || mc.player.isSpectator();
    }

    private long getCps() {
        long min = minCps.getValue().longValue();
        long max = maxCps.getValue().longValue();
        Velocity velocity = getModule(Velocity.class);

        if (velocity.isAttacking()) {
            return MathUtil.getRandomInRange(min - 5, max - 5);
        }

        return MathUtil.getRandomInRange(min, max);
    }

    private boolean isHoldingSword() {
        if (mc.player == null) return false;

        return mc.player.getMainHandStack().isIn(ItemTags.SWORDS);
    }
}
