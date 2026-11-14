package cn.omix.module.impl.combat;

import cn.omix.event.base.annotation.EventPriority;
import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.PacketEvent;
import cn.omix.event.impl.TickEvent;
import cn.omix.event.impl.WorldEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.module.value.impl.ModeValue;
import cn.omix.module.value.impl.NumberValue;
import cn.omix.util.combat.TimerRangeState;
import cn.omix.util.misc.TimerSpeedUtil;
import cn.omix.util.player.EntityUtil;
import cn.omix.util.player.MovementUtil;
import injection.accessor.PlayerInteractEntityC2SPacketAccessor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

/** Combined Southside TimerRange (A) and TimerRangeB (B). */
public final class TimerRange extends Module {
    private final ModeValue mode = new ModeValue("Mode", "A", "A", "B");
    private final NumberValue range = new NumberValue("Range", 3.0F, 3.0F, 6.0F, 0.02F, () -> mode.is("A"));
    private final NumberValue boost = new NumberValue("Boost", 4.0F, 1.0F, 9.0F, 0.1F, () -> mode.is("A"));
    private final NumberValue slowSpeed = new NumberValue("SlowSpeed", 0.2F, 0.1F, 0.9F, 0.02F, () -> mode.is("A"));
    private final BoolValue onlyGround = new BoolValue("OnlyGround", true, () -> mode.is("A"));
    private final BoolValue notOnEat = new BoolValue("NotOnEat", true, () -> mode.is("A"));
    private final BoolValue onlyKillaura = new BoolValue("OnlyKillaura", true, () -> mode.is("A"));
    private final NumberValue rangeB = new NumberValue("RangeB", 3.5F, 1.0F, 6.0F, 0.02F, () -> mode.is("B"));
    private final NumberValue positiveTimer = new NumberValue("PositiveTimer", 1.5F, 0.1F, 20.0F, 0.05F, () -> mode.is("B"));
    private final NumberValue positiveTicks = new NumberValue("PositiveTicks", 8, 1, 30, 1, () -> mode.is("B"));
    private final NumberValue negativeTimer = new NumberValue("NegativeTimer", 0.4F, 0.05F, 5.0F, 0.05F, () -> mode.is("B"));
    private final NumberValue negativeTicks = new NumberValue("NegativeTicks", 15, 1, 50, 1, () -> mode.is("B"));
    // Kept for configuration parity. Southside declares this switch without reading it.
    private final BoolValue confirmKB = new BoolValue("ConfirmKnockback", true, () -> mode.is("B"));
    private final TimerRangeState state = new TimerRangeState();
    private volatile long generation;
    private PlayerEntity player;

    public TimerRange() {
        super("TimerRange", Category.Combat);
        mode.onChange((previous, next) -> reset());
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        reset();
    }

    @Override
    public String getSuffix() {
        return mode.getValue();
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        reset();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (player != mc.player) reset();
        if (mc.player == null || mc.world == null) {
            reset();
            return;
        }

        if (mode.is("A")) {
            tickA();
        } else {
            applySpeed(state.tickB(positiveTicks.getValue(), positiveTimer.getValue(), negativeTimer.getValue()));
        }
    }

    private void tickA() {
        Aura aura = getModule(Aura.class);
        if (onlyGround.getValue() && !mc.player.isOnGround()
                || notOnEat.getValue() && mc.player.isUsingItem()
                || onlyKillaura.getValue() && !aura.isEnabled()) {
            applySpeed(1.0F);
            return;
        }

        applySpeed(state.tickA(searchTarget(aura) != null, boost.getValue(), slowSpeed.getValue()));
    }

    private PlayerEntity searchTarget(Aura aura) {
        if (aura.getTarget() instanceof PlayerEntity target && mc.player.distanceTo(target) < range.getValue()) {
            return target;
        }

        PlayerEntity closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (PlayerEntity candidate : mc.world.getPlayers()) {
            if (candidate == mc.player || !EntityUtil.isSelected(candidate)) continue;
            double distance = mc.player.distanceTo(candidate);
            if (distance <= range.getValue() && distance < closestDistance) {
                closest = candidate;
                closestDistance = distance;
            }
        }
        return closest;
    }

    @EventTarget
    @EventPriority(1000)
    public void onPacket(PacketEvent event) {
        if (!mode.is("B") || event.isCancelled()) return;
        var packet = event.getPacket();
        boolean interaction = event.getType() == PacketEvent.Type.Send && packet instanceof PlayerInteractEntityC2SPacket;
        boolean velocity = event.getType() == PacketEvent.Type.Received && packet instanceof EntityVelocityUpdateS2CPacket;
        if (!interaction && !velocity) return;

        // Receive callbacks run on the network thread. Discard work from a previous mode/session.
        long session = generation;
        var world = mc.world;
        var currentPlayer = mc.player;
        mc.execute(() -> {
            if (session != generation || !isNativeBehaviorActive() || !mode.is("B")
                    || mc.world == null || mc.world != world || mc.player == null || mc.player != currentPlayer) return;
            if (packet instanceof PlayerInteractEntityC2SPacket interact) {
                onInteraction(interact);
            } else if (packet instanceof EntityVelocityUpdateS2CPacket knockback) {
                onKnockback(knockback);
            }
        });
    }

    private void onInteraction(PlayerInteractEntityC2SPacket packet) {
        packet.handle(new PlayerInteractEntityC2SPacket.Handler() {
            public void interact(Hand hand) { handleInteraction(false); }
            public void interactAt(Hand hand, Vec3d pos) { handleInteraction(false); }
            public void attack() { handleInteraction(true); }

            private void handleInteraction(boolean attack) {
                Entity target = mc.world.getEntityById(((PlayerInteractEntityC2SPacketAccessor) packet).omix$getEntityId());
                if (target == null) {
                    applySpeed(1.0F);
                    return;
                }
                if (!state.onInteraction(attack, mc.player.distanceTo(target), rangeB.getValue(),
                        positiveTicks.getValue().intValue(), negativeTicks.getValue().intValue())) {
                    applySpeed(1.0F);
                }
            }
        });
    }

    private void onKnockback(EntityVelocityUpdateS2CPacket packet) {
        if (packet.getEntityId() != mc.player.getId() || !MovementUtil.isMoving()
                || state.hasActiveCycle() || mc.player.isSpectator() || mc.player.isDead()
                || mc.player.isInLava() || mc.player.isTouchingWater() || mc.player.isClimbing()
                || mc.player.hasVehicle() || TimerSpeedUtil.getTimerSpeed() == 1.0F) return;

        Vec3d velocity = packet.getVelocity();
        if (velocity.y > 0.0 && (velocity.x != 0.0 || velocity.z != 0.0)) applySpeed(1.0F);
    }

    private void applySpeed(float speed) {
        if (speed == 1.0F) {
            TimerSpeedUtil.clearTemporaryOverride(this);
        } else {
            TimerSpeedUtil.setTemporaryOverride(this, speed);
        }
    }

    private void reset() {
        generation++;
        state.reset();
        player = mc.player;
        TimerSpeedUtil.clearTemporaryOverride(this);
    }
}
