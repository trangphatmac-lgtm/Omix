package cn.remix.module.impl.move;

import cn.remix.event.base.annotation.EventPriority;
import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.PacketEvent;
import cn.remix.event.impl.TickEvent;
import cn.remix.event.impl.WorldEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

public final class AntiVoid extends Module {
    private final ModeValue mode = new ModeValue("Mode", "Blink", "Blink");
    private final NumberValue distance = new NumberValue("Distance", 5.0, 0.0, 16.0, 0.5);

    private boolean inVoid;
    private boolean wasInVoid;
    private boolean wasUsePressed;
    private boolean blinking;
    private Vec3d lastSafePosition;

    public AntiVoid() {
        super("AntiVoid", Category.Move);
    }

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        resetBlink();
        inVoid = false;
        wasInVoid = false;
        wasUsePressed = false;
    }

    @EventTarget
    @EventPriority(1000)
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) {
            resetBlink();
            wasUsePressed = false;
            return;
        }

        setSuffix(mode.getValue());
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
    public void onPacket(PacketEvent event) {
        if (event.getType() == PacketEvent.Type.Received
                && event.getPacket() instanceof PlayerPositionLookS2CPacket) {
            resetBlink();
            inVoid = false;
            wasInVoid = false;
        }
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        resetState();
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
    }

    public boolean isBufferingPackets() {
        return blinking;
    }
}
