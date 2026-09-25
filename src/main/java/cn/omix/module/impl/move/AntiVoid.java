package cn.omix.module.impl.move;

import cn.omix.event.base.annotation.EventPriority;
import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.MoveEvent;
import cn.omix.event.impl.PacketEvent;
import cn.omix.event.impl.TickEvent;
import cn.omix.event.impl.WorldEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.impl.world.Scaffold;
import cn.omix.module.impl.world.ScaffoldX;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.module.value.impl.ModeValue;
import cn.omix.module.value.impl.NumberValue;
import cn.omix.util.network.GameConnectionContext;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public final class AntiVoid extends Module {
    private final ModeValue mode = new ModeValue("Mode", "Blink", "Blink", "Motion", "TP", "Cubecraft");
    private final NumberValue distance = new NumberValue("Distance", 5.0, 0.0, 16.0, 0.5);
    private final BoolValue disablerWhileScaffold = new BoolValue("Disabler While Scaffold", false);

    private boolean inVoid;
    private boolean wasInVoid;
    private boolean wasUsePressed;
    private boolean blinking;
    private Vec3d lastSafePosition;
    private Vec3d lastGroundedPosition;
    private double fallDistanceAccumulated;

    public AntiVoid() {
        super("AntiVoid", Category.Move);
        mode.onChange((previous, current) -> {
            resetState();
            if (isEnabled()) updateGroundedPosition();
        });
    }

    @Override
    public void onEnable() {
        resetState();
        updateGroundedPosition();
    }

    @Override
    public void onDisable() {
        resetState();
    }

    @EventTarget
    @EventPriority(1000)
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) {
            resetState();
            return;
        }

        setSuffix(mode.getValue());
        if (disablerWhileScaffold.getValue() && isScaffoldActive()) {
            resetState();
            return;
        }

        if (!mode.is("Blink")) return;

        handlePearlUse();

        inVoid = !mc.player.getAbilities().allowFlying && isOverVoid(mc.player.getBoundingBox());
        if (!inVoid) {
            resetBlink();
            wasInVoid = false;
            return;
        }

        if (!wasInVoid) {
            lastSafePosition = new Vec3d(mc.player.lastX, mc.player.lastY, mc.player.lastZ);
        }
        wasInVoid = true;

        if (lastSafePosition == null || isOverVoid(getSafePositionBox())) {
            resetBlink();
            return;
        }

        if (!blinking && !instance.getPacketManager().getBlink().active) {
            instance.getPacketManager().getBlink().start(this);
            blinking = true;
        }

        if (blinking && lastSafePosition.y - distance.getValue() > mc.player.getY()) {
            instance.getPacketManager().getBlink().packets.offerFirst(
                    new PlayerMoveC2SPacket.PositionAndOnGround(
                            lastSafePosition.x,
                            lastSafePosition.y - ThreadLocalRandom.current().nextDouble(10.0, 20.0),
                            lastSafePosition.z,
                            false,
                            mc.player.horizontalCollision
                    )
            );
            resetBlink();
        }
    }

    @EventTarget
    @EventPriority(1000)
    public void onMove(MoveEvent event) {
        if (mode.is("Blink")) return;
        if (mc.player == null || mc.world == null
                || (disablerWhileScaffold.getValue() && isScaffoldActive())) {
            resetState();
            return;
        }
        if (event.isCancelled()) return;

        updateGroundedPosition();
        Fly fly = getModule(Fly.class);
        if (mc.player.getAbilities().allowFlying || (fly != null && fly.isEnabled())) {
            fallDistanceAccumulated = 0.0;
            return;
        }

        double motionY = mc.player.getVelocity().y;
        if (mc.player.isOnGround()) {
            fallDistanceAccumulated = 0.0;
        } else if (motionY < -0.08) {
            fallDistanceAccumulated -= motionY;
        }

        if (fallDistanceAccumulated <= distance.getValue() || !isFallingIntoVoid()) return;
        fallDistanceAccumulated = 0.0;

        String rescueMode = mode.getValue();
        String serverAddress = GameConnectionContext.serverAddress(mc);
        if (mode.is("Cubecraft") && (mc.isInSingleplayer() || serverAddress == null
                || !serverAddress.toLowerCase(Locale.ROOT).contains("cubecraft.net"))) {
            rescueMode = "Motion";
        }

        switch (rescueMode) {
            case "Motion" -> {
                event.setY(0.1);
                Vec3d velocity = mc.player.getVelocity();
                mc.player.setVelocity(velocity.x, event.getY(), velocity.z);
            }
            case "TP" -> {
                if (lastGroundedPosition != null) {
                    mc.player.setPosition(lastGroundedPosition.x, lastGroundedPosition.y, lastGroundedPosition.z);
                    event.setCancelled();
                }
            }
            case "Cubecraft" -> {
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                        mc.player.getX(), 3.2E7, mc.player.getZ(), false, mc.player.horizontalCollision));
                if (fly != null) fly.setEnabled(false);
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() == PacketEvent.Type.Received
                && event.getPacket() instanceof PlayerPositionLookS2CPacket) {
            resetState();
        }
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        resetState();
    }

    private void updateGroundedPosition() {
        if (mc.player == null || mc.world == null || mode.is("Blink")) return;
        Box supportBox = mc.player.getBoundingBox().stretch(0.0, -0.001, 0.0);
        if (mc.player.isOnGround() || mc.world.getBlockCollisions(mc.player, supportBox).iterator().hasNext()) {
            lastGroundedPosition = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        }
    }

    private boolean isFallingIntoVoid() {
        if (mc.player.isOnGround()) return false;
        // Modern worlds can extend below Y=0; scan down to this world's actual floor.
        double depth = mc.player.getY() - mc.world.getBottomY();
        return depth < 0.0 || !mc.world.getBlockCollisions(mc.player,
                mc.player.getBoundingBox().stretch(0.0, -depth, 0.0)).iterator().hasNext();
    }

    private boolean isScaffoldActive() {
        Scaffold scaffold = getModule(Scaffold.class);
        ScaffoldX scaffoldX = getModule(ScaffoldX.class);
        return (scaffold != null && scaffold.isEnabled()) || (scaffoldX != null && scaffoldX.isEnabled());
    }

    private void handlePearlUse() {
        boolean usePressed = mc.options.useKey.isPressed();
        if (usePressed
                && !wasUsePressed
                && mc.player.getMainHandStack().isOf(Items.ENDER_PEARL)) {
            resetBlink();
        }
        wasUsePressed = usePressed;
    }

    private Box getSafePositionBox() {
        float halfWidth = mc.player.getWidth() / 2.0F;
        return new Box(
                lastSafePosition.x - halfWidth,
                lastSafePosition.y,
                lastSafePosition.z - halfWidth,
                lastSafePosition.x + halfWidth,
                lastSafePosition.y + mc.player.getHeight(),
                lastSafePosition.z + halfWidth
        );
    }

    private boolean isOverVoid(Box box) {
        if (mc.world == null) return true;

        int minX = MathHelper.floor(box.minX);
        int maxX = MathHelper.floor(box.maxX + 1.0E-6);
        int minZ = MathHelper.floor(box.minZ);
        int maxZ = MathHelper.floor(box.maxZ + 1.0E-6);
        int startY = MathHelper.floor(box.minY) - 1;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = startY; y >= mc.world.getBottomY(); y--) {
                    if (!mc.world.getBlockState(new BlockPos(x, y, z)).isReplaceable()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private void resetBlink() {
        if (blinking && instance.getPacketManager() != null) {
            instance.getPacketManager().getBlink().dispatch(this);
        }
        blinking = false;
        lastSafePosition = null;
    }

    private void resetState() {
        resetBlink();
        inVoid = false;
        wasInVoid = false;
        wasUsePressed = false;
        lastGroundedPosition = null;
        fallDistanceAccumulated = 0.0;
    }

    public boolean isBufferingPackets() {
        return blinking;
    }
}
