package cn.omix.script.api;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.Block;
import net.minecraft.client.gui.DrawContext;
import java.util.List;

public final class ModeHooks {
    private ModeHooks() {}
    public record PathQuery(Vec3d from, Vec3d to) {}
    public record PathResult(List<Vec3d> points) { public PathResult { points = List.copyOf(points); } }
    public record TargetQuery(LivingEntity entity, boolean checkBot, boolean checkTeams, boolean checkFriend, boolean checkSelf) {}
    public static final ModeHook<TargetQuery, Boolean> SELECT_TARGET = new ModeHook<>("selectTarget", TargetQuery.class, Boolean.class);
    public record BlockTarget(net.minecraft.entity.Entity camera, float tickProgress) {}
    public static final ModeHook<LivingEntity, Boolean> IS_BOT = new ModeHook<>("isBot", LivingEntity.class, Boolean.class);
    public static final ModeHook<Double, Double> BLOCK_REACH = new ModeHook<>("blockReach", Double.class, Double.class);
    public static final ModeHook<BlockTarget, net.minecraft.util.hit.BlockHitResult> BLOCK_TARGET = new ModeHook<>("blockTarget", BlockTarget.class, net.minecraft.util.hit.BlockHitResult.class);
    public static final ModeHook<LivingEntity, Boolean> RENDER_ENTITY = new ModeHook<>("renderEntity", LivingEntity.class, Boolean.class);
    public static final ModeHook<LivingEntity, Integer> ENTITY_COLOR = new ModeHook<>("entityColor", LivingEntity.class, Integer.class);
    public static final ModeHook<net.minecraft.util.Identifier, net.minecraft.client.render.RenderLayer> ENTITY_LAYER = new ModeHook<>("entityLayer", net.minecraft.util.Identifier.class, net.minecraft.client.render.RenderLayer.class);
    public static final ModeHook<Double, Double> TERRAIN_OPACITY = new ModeHook<>("terrainOpacity", Double.class, Double.class);
    public static final ModeHook<LivingEntity, Boolean> IS_TEAM = new ModeHook<>("isTeam", LivingEntity.class, Boolean.class);
    public static final ModeHook<PathQuery, PathResult> COMPUTE_PATH = new ModeHook<>("computePath", PathQuery.class, PathResult.class);
    public static final ModeHook<Double, Double> ENTITY_REACH = new ModeHook<>("entityReach", Double.class, Double.class);
    public static final ModeHook<Block, Boolean> XRAY_BLOCK = new ModeHook<>("xrayBlock", Block.class, Boolean.class);
    public static final ModeHook<DrawContext, Boolean> RENDER_HUD = new ModeHook<>("renderHud", DrawContext.class, Boolean.class);
    public static final ModeHook<String, Boolean> INTERCEPT = new ModeHook<>("intercept", String.class, Boolean.class);
    public static final ModeHook<Double, Double> SLOWDOWN = new ModeHook<>("slowdown", Double.class, Double.class);
}
