package cn.remix.module.impl.render;

import cn.remix.Client;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.impl.combat.Aura;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ColorValue;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.player.EntityUtil;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import lombok.Getter;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;

import java.awt.Color;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public final class Chams extends Module {
    private static final Color TARGET_COLOR = new Color(255, 85, 85);
    private static final Color DEFAULT_COLOR = new Color(85, 255, 85);

    private static final RenderPipeline TINT_THROUGH_WALLS_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                    .withLocation(Identifier.of("remix", "pipeline/chams_tint_through_walls"))
                    .withShaderDefine("ALPHA_CUTOUT", 0.1F)
                    .withShaderDefine("PER_FACE_LIGHTING")
                    .withSampler("Sampler1")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .build()
    );
    private static final RenderPipeline FLAT_PIPELINE = createFlatPipeline("chams_flat", false);
    private static final RenderPipeline FLAT_THROUGH_WALLS_PIPELINE = createFlatPipeline("chams_flat_through_walls", true);
    private static final RenderLayer FLAT_LAYER = createFlatLayer("remix_chams_flat", FLAT_PIPELINE);
    private static final RenderLayer FLAT_THROUGH_WALLS_LAYER = createFlatLayer("remix_chams_flat_through_walls", FLAT_THROUGH_WALLS_PIPELINE);
    private static final Map<Identifier, RenderLayer> TINT_THROUGH_WALLS_LAYERS = new ConcurrentHashMap<>();

    private final ModeValue renderMode = new ModeValue("Render Mode", "Tint", "Tint", "Flat");
    private final ModeValue colorMode = new ModeValue("Color Mode", "Aura", "Aura", "Custom");
    private final ColorValue customColor = new ColorValue("Custom Color", Color.WHITE, () -> colorMode.is("Custom"));
    private final BoolValue throughWalls = new BoolValue("Through Walls", true);
    private final NumberValue alpha = new NumberValue("Alpha", 150, 0, 255, 1);

    public Chams() {
        super("Chams", Category.Render);
    }

    public static Chams getActive() {
        Client client = Client.instance;
        if (client == null || client.getModuleManager() == null) return null;

        Chams module = client.getModuleManager().getModule(Chams.class);
        return module != null && module.isEnabled() ? module : null;
    }

    public boolean shouldRender(LivingEntity entity) {
        return entity != null && EntityUtil.isSelected(entity);
    }

    public RenderLayer getRenderLayer(Identifier texture) {
        if (renderMode.is("Flat")) {
            return throughWalls.getValue() ? FLAT_THROUGH_WALLS_LAYER : FLAT_LAYER;
        }

        if (!throughWalls.getValue()) {
            return net.minecraft.client.render.RenderLayers.entityTranslucent(texture, false);
        }

        return TINT_THROUGH_WALLS_LAYERS.computeIfAbsent(texture, Chams::createTintThroughWallsLayer);
    }

    public int getEntityColor(LivingEntity entity) {
        Color color = customColor.getValue();
        if (colorMode.is("Aura")) {
            Aura aura = getModule(Aura.class);
            color = aura.isEnabled() && aura.getTarget() == entity ? TARGET_COLOR : DEFAULT_COLOR;
        }

        int opacity = Math.clamp(alpha.getValue().intValue(), 0, 255);
        return (opacity << 24) | (color.getRGB() & 0x00FFFFFF);
    }

    private static RenderPipeline createFlatPipeline(String name, boolean throughWalls) {
        RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                .withLocation(Identifier.of("remix", "pipeline/" + name))
                .withVertexShader(Identifier.of("remix", "core/chams_flat"))
                .withFragmentShader(Identifier.of("remix", "core/chams_flat"))
                .withBlend(BlendFunction.TRANSLUCENT)
                .withCull(false)
                .withDepthWrite(false)
                .withVertexFormat(VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS);

        if (throughWalls) {
            builder.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST);
        }

        return RenderPipelines.register(builder.build());
    }

    private static RenderLayer createFlatLayer(String name, RenderPipeline pipeline) {
        return RenderLayer.of(name, RenderSetup.builder(pipeline).translucent().build());
    }

    private static RenderLayer createTintThroughWallsLayer(Identifier texture) {
        RenderSetup setup = RenderSetup.builder(TINT_THROUGH_WALLS_PIPELINE)
                .texture("Sampler0", texture)
                .useLightmap()
                .useOverlay()
                .translucent()
                .build();
        return RenderLayer.of("remix_chams_tint_through_walls", setup);
    }
}
