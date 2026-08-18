package cn.omix.management.packet.impl;

import cn.omix.event.impl.Render3DEvent;
import cn.omix.management.packet.SubCore;
import cn.omix.util.network.PacketUtil;
import cn.omix.util.render.Render3D;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;

public final class Blink extends SubCore {
    private static final Color REAL_POSITION_COLOR = new Color(0, 255, 0, 80);

    private Vec3d realPosition;

    @Override
    public void start() {
        captureRealPosition();
        super.start();
    }

    @Override
    public void start(Object holder) {
        captureRealPosition();
        super.start(holder);
    }

    @Override
    public void dispatch(boolean releasePackets) {
        super.dispatch(releasePackets);
        clearRealPositionIfInactive();
    }

    @Override
    public void dispatch(Object holder, boolean releasePackets) {
        super.dispatch(holder, releasePackets);
        clearRealPositionIfInactive();
    }

    @Override
    public void clear() {
        super.clear();
        realPosition = null;
    }

    public void renderRealPosition(Render3DEvent event) {
        if (!active || realPosition == null || mc.player == null || mc.world == null) {
            return;
        }

        Vec3d offset = realPosition.subtract(mc.player.getEntityPos());
        Box realPositionBox = mc.player.getBoundingBox().offset(offset.x, offset.y, offset.z);
        Render3D.drawBox(event, realPositionBox, REAL_POSITION_COLOR, true, true);
    }

    @Override
    protected void onRelease(Packet<?> packet) {
        PacketUtil.sendPacketNoEvent(packet);
    }

    @Override
    protected boolean shouldIgnore(Packet<?> packet) {
        return packet instanceof KeepAliveC2SPacket
                || packet instanceof CommonPongC2SPacket
                || packet instanceof ChatMessageC2SPacket
                || packet instanceof CommandExecutionC2SPacket;
    }

    private void captureRealPosition() {
        if (!active && mc.player != null && mc.world != null) {
            realPosition = mc.player.getEntityPos();
        }
    }

    private void clearRealPositionIfInactive() {
        if (!active) {
            realPosition = null;
        }
    }
}
