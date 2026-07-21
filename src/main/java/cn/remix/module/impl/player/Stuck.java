package cn.remix.module.impl.player;

import cn.remix.event.base.annotation.EventPriority;
import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.LivingUpdateEvent;
import cn.remix.event.impl.MotionEvent;
import cn.remix.event.impl.MoveInputEvent;
import cn.remix.event.impl.MoveMathEvent;
import cn.remix.event.impl.PacketEvent;
import cn.remix.event.impl.StrafeEvent;
import cn.remix.event.impl.TickEvent;
import cn.remix.event.impl.WorldEvent;
import cn.remix.management.RotationManager;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.impl.move.AntiVoid;
import cn.remix.module.impl.world.Scaffold;
import cn.remix.module.impl.world.ScaffoldX;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.network.PacketUtil;
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
    private final ModeValue implementation = new ModeValue("Implementation", "Classic", "Classic", "Remix");

    private final ModeValue classicMode = new ModeValue(
            "Classic Mode",
            "Delay",
            () -> implementation.is("Classic"),
            "Delay",
            "Packet"
    );

    private final ModeValue remixMode = new ModeValue(
            "Remix Mode",
            "Freeze",
            () -> implementation.is("Remix"),
            "Freeze",
            "Cancel"
    );
    private final NumberValue freezeTick = new NumberValue(
            "Freeze Tick",
            20,
            1,
            20,
            1,
            () -> implementation.is("Remix") && remixMode.is("Freeze")
    );
    private final BoolValue noMove = new BoolValue("No Move", true, () -> implementation.is("Remix"));

    private final Queue<CommonPongC2SPacket> pongQueue = new ConcurrentLinkedQueue<>();
    private int stuckState;
    private Packet<?> capturedPacket;
    private float savedYaw;
    private float savedPitch;
    private boolean pendingDisable;

    private int remixStuckTick;
    private String activeImplementation = "Classic";

    public Stuck() {
        super("Stuck", Category.Player);
    }

    @Override
    public void onEnable() {
        activeImplementation = implementation.getValue();
        resetClassicState();
        remixStuckTick = 0;

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

        if (implementation.is("Remix")
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
        remixStuckTick = 0;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        syncImplementation();
        setSuffix(implementation.is("Classic")
                ? "Classic " + classicMode.getValue()
                : "Remix " + remixMode.getValue());

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
        if (!implementation.is("Remix") || mc.player == null || mc.world == null) return;

        switch (remixMode.getValue()) {
            case "Freeze" -> {
                if (remixStuckTick >= freezeTick.getValue().intValue()) {
                    remixStuckTick = 0;
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
        if (implementation.is("Remix") && noMove.getValue()) {
            event.setForward(0);
            event.setStrafe(0);
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        syncImplementation();
        if (implementation.is("Remix")) {
            remixStuckTick++;
        }
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        stuckState = 3;
        capturedPacket = null;
        pongQueue.clear();
        remixStuckTick = 0;
        setEnabled(false);
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
        remixStuckTick = 0;
        activeImplementation = implementation.getValue();

        if (implementation.is("Classic") && mc.player != null) {
            savedYaw = RotationManager.getAppliedYaw(mc.player.getYaw());
            savedPitch = RotationManager.isRotating()
                    ? RotationManager.currentRotations[1]
                    : mc.player.getPitch();
        }
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
