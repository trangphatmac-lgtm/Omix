package cn.omix.module.impl.combat;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.MoveInputEvent;
import cn.omix.event.impl.PacketEvent;
import cn.omix.event.impl.RotationRequestEvent;
import cn.omix.event.impl.TickEvent;
import cn.omix.event.impl.WorldEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.module.value.impl.ModeValue;
import cn.omix.module.value.impl.NumberValue;
import cn.omix.util.player.RotationUtil;
import cn.omix.util.player.velocity.GrimFullPackets;
import cn.omix.util.player.velocity.GrimFullState;
import cn.omix.util.player.velocity.HeypixelReduce;
import injection.accessor.EntityVelocityUpdateS2CPacketAccessor;
import lombok.Getter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

@Getter
public class Velocity extends Module {
    private final ModeValue mode = new ModeValue("Mode", "Normal", "Normal", "Packet", "Reduce", "Grim Full", "Heypixel Reduce");
    private final NumberValue horizontal = new NumberValue("Horizontal", 0, 0, 100, 1, () -> mode.is("Packet"));
    private final NumberValue vertical = new NumberValue("Vertical", 0, 0, 100, 1, () -> mode.is("Packet"));
    private final BoolValue allowVelocityDuringWait = new BoolValue("Allow Velocity During Wait", false, () -> mode.is("Grim Full"));
    private final BoolValue autoAttackCount = new BoolValue("AutoAttackCount", true, () -> mode.is("Heypixel Reduce"));
    private final NumberValue attackCount = new NumberValue("AttackCount", 4, 0, 20, 1, () -> mode.is("Heypixel Reduce") && !autoAttackCount.getValue());
    private final ModeValue attackMode = new ModeValue("AttackMode", "PerTick", () -> mode.is("Heypixel Reduce"), "OneTime", "PerTick");
    private final NumberValue alinkTargetRange = new NumberValue("AlinkTargetRange", 10, 0, 20, 0.1F, () -> mode.is("Heypixel Reduce"));
    private final NumberValue alinkMaxDelay = new NumberValue("AlinkMaxDelay", 60, 0, 200, 1, () -> mode.is("Heypixel Reduce"));
    private final BoolValue requireKillAura = new BoolValue("RequireKillAura", false, () -> mode.is("Heypixel Reduce"));
    private final BoolValue debug = new BoolValue("Debug", false, () -> mode.is("Heypixel Reduce"));
    private final HeypixelReduce heypixelReduce = new HeypixelReduce(this);
    private final GrimFullState grimFullState = new GrimFullState();
    private final GrimFullPackets grimFullPackets = new GrimFullPackets();
    private LivingEntity attackTarget = null;
    private boolean jump = false;
    private boolean attacking;
    private int reduceTicks;
    private int resetTicks;

    public Velocity() {
        super("Velocity", Category.Combat);
        mode.onChange((previous, current) -> {
            if (previous.equals("Heypixel Reduce")) heypixelReduce.disable();
            reset();
        });
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        heypixelReduce.disable();
        reset();
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        reset();
    }

    private void reset() {
        heypixelReduce.reset();
        grimFullState.reset();
        grimFullPackets.reset();
        attackTarget = null;
        attacking = false;
        reduceTicks = 0;
        resetTicks = 0;
        jump = false;
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (mc.player == null) return;
        if (mode.is("Heypixel Reduce")) {
            heypixelReduce.onMoveInput(event);
            return;
        }

        if (jump) {
            event.setJumping(true);
            jump = false;
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.player == null) return;
        setSuffix(mode.getValue());
        Packet<?> packet = event.getPacket();
        if (event.getType() == PacketEvent.Type.Received) {
            if (mode.is("Heypixel Reduce")) {
                heypixelReduce.onReceivePacket(event);
                return;
            }
            if (mode.is("Grim Full")) {
                GrimFullState.Decision decision = grimFullState.decide(mc.player, mc.player.age, System.nanoTime(),
                        packet instanceof PlayerPositionLookS2CPacket,
                        grimFullPackets.shouldPassVelocity(mc.player, mc.player.age, packet, mc.player.getId()),
                        allowVelocityDuringWait.getValue());
                // Modern Grim uses common ping/pong packets in place of legacy transactions.
                if ((packet instanceof CommonPingS2CPacket && decision.cancelPing())
                        || (packet instanceof EntityVelocityUpdateS2CPacket velocity
                        && velocity.getEntityId() == mc.player.getId() && decision.cancelVelocity())) {
                    event.setCancelled(true);
                }
                return;
            }

            if (packet instanceof EntityVelocityUpdateS2CPacket velocity) {
                if (velocity.getEntityId() == mc.player.getId()) {
                    switch (mode.getValue()) {
                        case "Normal" ->
                                event.setCancelled(true);

                        case "Packet" -> {
                            EntityVelocityUpdateS2CPacketAccessor accessor = (EntityVelocityUpdateS2CPacketAccessor) velocity;
                            double x = velocity.getVelocity().x * (horizontal.getValue() / 100.0);
                            double y = velocity.getVelocity().y * (vertical.getValue() / 100.0);
                            double z = velocity.getVelocity().z * (horizontal.getValue() / 100.0);
                            accessor.setVelocity(new Vec3d(x, y, z));
                        }

                        case "Reduce" -> {
                            if (velocity.getEntityId() == mc.player.getId() && velocity.getVelocity().y > 0) {
                                Entity entity = getEntity();
                                if (entity instanceof PlayerEntity livingEntity) {
                                    reduceTicks = 5;
                                    attackTarget = livingEntity;
                                    jump = true;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null) return;
        if (mode.is("Heypixel Reduce")) {
            heypixelReduce.onPreTick();
            return;
        }
        if (mode.is("Grim Full")) {
            grimFullState.update(mc.player, mc.player.age, System.nanoTime(), false);
        }
        if (mc.interactionManager == null) return;

        if (mode.is("Reduce")) {
            if (resetTicks > 0) {
                resetTicks--;
                if (resetTicks <= 0) {
                    attacking = false;
                }
            }

            if (attackTarget != null && reduceTicks > 0) {
                if (RotationUtil.getDistanceToEntity(attackTarget) >= 3.0) {
                    return;
                }

                if (mc.player.isSprinting()) {
                    mc.player.setSprinting(false);
                    mc.interactionManager.attackEntity(mc.player, attackTarget);
                    mc.player.swingHand(Hand.MAIN_HAND);
                    Vec3d velocity = mc.player.getVelocity();
                    mc.player.setVelocity(velocity.x * 0.6, velocity.y, velocity.z * 0.6);
                    attackTarget = null;
                    reduceTicks--;
                    resetTicks = 3;
                    attacking = true;
                }
            }
        }
    }

    @EventTarget
    public void onRotationRequest(RotationRequestEvent event) {
        if (mode.is("Heypixel Reduce")) heypixelReduce.onRotationRequest(event);
    }

    @Override
    public String getSuffix() {
        return mode.is("Heypixel Reduce") ? heypixelReduce.getSuffix() : super.getSuffix();
    }

    public boolean isAttacking() {
        return mode.is("Heypixel Reduce") ? heypixelReduce.isAttacking() : attacking;
    }

    public int getHitSelectSkips() {
        return mode.is("Heypixel Reduce") ? heypixelReduce.getHitSelectSkips() : 0;
    }

    public boolean consumeHitSelectSkip() {
        return mode.is("Heypixel Reduce") && heypixelReduce.consumeHitSelectSkip();
    }

    private Entity getEntity() {
        Aura aura = getModule(Aura.class);
        HitResult hitResult = mc.crosshairTarget;
        Entity entity = null;

        if (aura.isEnabled() && aura.getTarget() != null) {
            entity = aura.getTarget();
        } else {
            if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY) {
                entity = ((EntityHitResult) hitResult).getEntity();
            }
        }
        return entity;
    }
}
