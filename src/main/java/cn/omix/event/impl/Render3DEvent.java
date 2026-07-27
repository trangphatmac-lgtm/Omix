package cn.omix.event.impl;

import cn.omix.event.base.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

@Getter
@AllArgsConstructor
public class Render3DEvent extends Event {
    private final MatrixStack matrixStack;
    private final VertexConsumerProvider consumers;
    private final float tickDelta;

    private final Matrix4f projectionMatrix;
    private final Matrix4f modelViewMatrix;
}