package cn.omix.module.impl.player;

import cn.omix.event.base.annotation.EventPriority;
import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.LivingUpdateEvent;
import cn.omix.event.impl.MotionEvent;
import cn.omix.event.impl.MoveInputEvent;
import cn.omix.event.impl.MoveMathEvent;
import cn.omix.event.impl.PacketEvent;
import cn.omix.event.impl.StrafeEvent;
import cn.omix.event.impl.TickEvent;
import cn.omix.event.impl.WorldEvent;
import cn.omix.management.RotationManager;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.impl.move.AntiVoid;
import cn.omix.module.impl.world.Scaffold;
import cn.omix.module.impl.world.ScaffoldX;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.module.value.impl.ModeValue;
import cn.omix.module.value.impl.NumberValue;
import cn.omix.util.network.PacketUtil;
import injection.accessor.PlayerMoveC2SPacketAccessor;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Vec3d;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;

public final class Stuck extends Module {
    private final ModeValue implementation = new ModeValue("Implementation", "Classic", "Classic", "Omix");

    private final ModeValue classicMode = new ModeValue(
            "Classic Mode",
            "Delay",
            () -> implementation.is("Classic"),
            "Delay",
            "Packet"
    );

    private final ModeValue omixMode = new ModeValue(
            "Omix Mode",
            "Freeze",
            () -> implementation.is("Omix"),
            "Freeze",
            "Cancel"
    );
    private final NumberValue freezeTick = new NumberValue(
            "Freeze Tick",
            20,
            1,
            20,
            1,
            () -> implementation.is("Omix") && omixMode.is("Freeze")
    );
    private final BoolValue noMove = new BoolValue("No Move", true, () -> implementation.is("Omix"));

    private final Queue<CommonPongC2SPacket> pongQueue = new ConcurrentLinkedQueue<>();
    private int stuckState;
    private Packet<?> capturedPacket;
    private float savedYaw;
    private float savedPitch;
    private boolean pendingDisable;

    private int omixStuckTick;
    private String activeImplementation = "Classic";
    private boolean clutchFreezeOverride;
    private boolean clutchWasEnabled;
    private String clutchPreviousImplementation;
    private String clutchPreviousOmixMode;
    private int clutchPreviousOmixStuckTick;

    public Stuck() {
        super("Stuck", Category.Player);
    }

    @Override
    public void onEnable() {
        activeImplementation = implementation.getValue();
        resetClassicState();
        omixStuckTick = 0;

        if (implementation.is("Classic") && mc.player != null) {
            savedYaw = RotationManager.getAppliedYaw(mc.player.getYaw());
            savedPitch = RotationManager.isRotating()
                    ? RotationManager.currentRotations[1]
                    : mc.player.getPitch();
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (enabled) {
            if (mc.player != null) {
                super.setEnabled(true);
            }
            return;
        }

        if (!isEnabled()) return;

        if (implementation.is("Omix")
                || mc.player == null
                || !classicMode.is("Delay")
                || stuckState == 3) {
            super.setEnabled(false);
        } else {
            pendingDisable = true;
        }
    }

    @Override
    public void onDisable() {
        flushPongs();
        resetClassicState();
        omixStuckTick = 0;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        syncImplementation();
        setSuffix(implementation.is("Classic")
                ? "Classic " + classicMode.getValue()
                : "Omix " + omixMode.getValue());

        if (!implementation.is("Classic") || !classicMode.is("Packet") || mc.player == null) return;
        if (disableScaffold()) return;

        if (!isAntiVoidActive()) {
            sendWithoutEvent(new ClientCommandC2SPacket(
                    mc.player,
                    ClientCommandC2SPacket.Mode.START_FALL_FLYING
            ));
        }
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (!implementation.is("Classic") || mc.player == null || !event.isPost()) return;

        if (!isAntiVoidActive() && disableScaffold()) return;
        mc.player.setVelocity(Vec3d.ZERO);

        if (stuckState == 1) {
            stuckState = 2;
            float currentYaw = event.getYaw();
            float currentPitch = event.getPitch();

            if (capturedPacket != null
                    && shouldSendRotationBefore(capturedPacket)
                    && (savedYaw != currentYaw || savedPitch != currentPitch)) {
                sendWithoutEvent(new PlayerMoveC2SPacket.LookAndOnGround(
                        currentYaw,
                        currentPitch,
                        mc.player.isOnGround(),
                        mc.player.horizontalCollision
                ));
                flushPongs();
                savedYaw = currentYaw;
                savedPitch = currentPitch;
            }

            if (capturedPacket != null) {
                sendWithoutEvent(capturedPacket);
                capturedPacket = null;
            }
        } else if (!isAntiVoidActive() && classicMode.is("Packet") && mc.player.age % 10 == 0) {
            flushPongs();
        }

        if (pendingDisable) {
            if (classicMode.is("Delay")) {
                sendWithoutEvent(new PlayerMoveC2SPacket.PositionAndOnGround(
                        mc.player.getX() + 1337.0,
                        mc.player.getY(),
                        mc.player.getZ() + 1337.0,
                        mc.player.isOnGround(),
                        mc.player.horizontalCollision
                ));
            } else {
                sendWithoutEvent(new ClientCommandC2SPacket(
                        mc.player,
                        ClientCommandC2SPacket.Mode.START_FALL_FLYING
                ));
            }

            flushPongs();
            stuckState = 3;
            pendingDisable = false;
        }
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (!implementation.is("Classic")) return;

        if (mc.player != null) {
            mc.player.setSprinting(false);
        }
        event.setCancelled();
    }

    @EventTarget
    private void onMoveMath(MoveMathEvent event) {
        if (!implementation.is("Omix") || mc.player == null || mc.world == null) return;

        switch (omixMode.getValue()) {
            case "Freeze" -> {
                if (omixStuckTick >= freezeTick.getValue().intValue()) {
                    omixStuckTick = 0;
                    event.setCancelled(false);
                } else {
                    event.setCancelled(true);
                }
            }
            case "Cancel" -> event.setCancelled(true);
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (implementation.is("Omix") && noMove.getValue()) {
            event.setForward(0);
            event.setStrafe(0);
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        syncImplementation();
        if (implementation.is("Omix")) {
            omixStuckTick++;
        }
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        stuckState = 3;
        capturedPacket = null;
        pongQueue.clear();
        omixStuckTick = 0;
        if (clutchFreezeOverride) {
            endClutchFreeze(false);
        } else {
            setEnabled(false);
        }
    }

    @EventTarget
    @EventPriority(1)
    public void onPacket(PacketEvent event) {
        if (!implementation.is("Classic") || mc.player == null) return;

        if (event.getType() == PacketEvent.Type.Received) {
            if (event.getPacket() instanceof PlayerPositionLookS2CPacket && classicMode.is("Delay")) {
                flushPongs();
                stuckState = 3;
                setEnabled(false);
            }
            return;
        }

        if (isAntiVoidActive()) return;

        Packet<?> packet = event.getPacket();
        if (packet instanceof PlayerMoveC2SPacket movePacket) {
            if (stuckState != 1 && classicMode.is("Packet")) {
                PlayerMoveC2SPacketAccessor accessor = (PlayerMoveC2SPacketAccessor) movePacket;
                accessor.setYaw(mc.player.getYaw() + ThreadLocalRandom.current().nextFloat() - 0.5F);
                accessor.setPitch(mc.player.getPitch());
            }
            event.setCancelled();
        } else if (packet instanceof CommonPongC2SPacket pongPacket) {
            pongQueue.offer(pongPacket);
            event.setCancelled();
        } else if (packet instanceof PlayerInteractItemC2SPacket
                || packet instanceof PlayerActionC2SPacket) {
            capturedPacket = packet;
            stuckState = 1;
            event.setCancelled();
        }
    }

    private void syncImplementation() {
        if (activeImplementation.equalsIgnoreCase(implementation.getValue())) return;

        flushPongs();
        resetClassicState();
        omixStuckTick = 0;
        activeImplementation = implementation.getValue();

        if (implementation.is("Classic") && mc.player != null) {
            savedYaw = RotationManager.getAppliedYaw(mc.player.getYaw());
            savedPitch = RotationManager.isRotating()
                    ? RotationManager.currentRotations[1]
                    : mc.player.getPitch();
        }
    }

    public void beginClutchFreeze() {
        if (!clutchFreezeOverride) {
            clutchFreezeOverride = true;
            clutchWasEnabled = isEnabled();
            clutchPreviousImplementation = implementation.getValue();
            clutchPreviousOmixMode = omixMode.getValue();
            clutchPreviousOmixStuckTick = omixStuckTick;
            omixStuckTick = 0;
        }

        implementation.setValue("Omix");
        omixMode.setValue("Freeze");
        if (isEnabled()) {
            syncImplementation();
        } else {
            setEnabled(true);
        }
    }

    public void endClutchFreeze(boolean restoreEnabledState) {
        if (!clutchFreezeOverride) return;

        boolean shouldRemainEnabled = restoreEnabledState && clutchWasEnabled;
        if (!shouldRemainEnabled && isEnabled()) {
            setEnabled(false);
        }

        implementation.setValue(clutchPreviousImplementation);
        omixMode.setValue(clutchPreviousOmixMode);
        if (shouldRemainEnabled && implementation.is("Omix")) {
            omixStuckTick = clutchPreviousOmixStuckTick;
        }
        clutchFreezeOverride = false;

        if (shouldRemainEnabled) {
            if (isEnabled()) {
                syncImplementation();
            } else {
                setEnabled(true);
            }
        }

        clutchWasEnabled = false;
        clutchPreviousImplementation = null;
        clutchPreviousOmixMode = null;
        clutchPreviousOmixStuckTick = 0;
    }

    private void resetClassicState() {
        stuckState = 0;
        capturedPacket = null;
        pendingDisable = false;
        pongQueue.clear();
    }

    private boolean shouldSendRotationBefore(Packet<?> packet) {
        if (packet instanceof PlayerInteractItemC2SPacket interactPacket) {
            ItemStack heldStack = mc.player.getStackInHand(interactPacket.getHand());
            return !(heldStack.getItem() instanceof BowItem) && !isBowlFood(heldStack);
        }

        if (packet instanceof PlayerActionC2SPacket actionPacket) {
            return actionPacket.getAction() == PlayerActionC2SPacket.Action.RELEASE_USE_ITEM
                    && mc.player.getActiveItem().getItem() instanceof BowItem;
        }

        return false;
    }

    private boolean isBowlFood(ItemStack stack) {
        return stack.isOf(Items.MUSHROOM_STEW)
                || stack.isOf(Items.RABBIT_STEW)
                || stack.isOf(Items.BEETROOT_SOUP)
                || stack.isOf(Items.SUSPICIOUS_STEW);
    }

    private boolean disableScaffold() {
        ScaffoldX scaffoldX = getModule(ScaffoldX.class);
        if (scaffoldX != null && scaffoldX.isEnabled()) {
            scaffoldX.setEnabled(false);
            return true;
        }

        Scaffold scaffold = getModule(Scaffold.class);
        if (scaffold != null && scaffold.isEnabled()) {
            scaffold.setEnabled(false);
            return true;
        }

        return false;
    }

    private boolean isAntiVoidActive() {
        if (mc.player == null) return false;

        AntiVoid antiVoid = getModule(AntiVoid.class);
        return antiVoid != null
                && antiVoid.isEnabled()
                && !mc.player.isOnGround()
                && antiVoid.isBufferingPackets();
    }

    private void flushPongs() {
        CommonPongC2SPacket packet;
        while ((packet = pongQueue.poll()) != null) {
            sendWithoutEvent(packet);
        }
    }

    private void sendWithoutEvent(Packet<?> packet) {
        PacketUtil.sendPacketNoEvent(packet);
    }
}
