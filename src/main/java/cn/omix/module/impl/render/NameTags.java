package cn.omix.module.impl.render;

import cn.omix.Client;
import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.*;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.impl.player.Teams;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.util.render.Render2D;
import cn.omix.util.sigma.*;
import net.minecraft.block.FurnaceBlock;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.*;

public final class NameTags extends Module {
    private final BoolValue magnify = new BoolValue("Magnify", true);
    private final BoolValue furnaces = new BoolValue("Furnaces", true);
    private final BoolValue mobOwners = new BoolValue("Mob Owners", true);
    private final Map<BlockPos, SigmaFurnaceTracker> trackers = new LinkedHashMap<>();
    private final SigmaFurnaceRecipes recipes = new SigmaFurnaceRecipes();
    private final List<EntityLabel> labels = new ArrayList<>();
    private final List<FurnaceLabel> furnaceLabels = new ArrayList<>();
    private BlockPos interactingFurnace;
    private int interactionTick;
    private static final int BACKGROUND = SigmaColors.alpha(SigmaColors.sourceBlend(SigmaColors.WHITE, SigmaColors.BLACK, 75), .5f);
    private static final Map<String, String> BADGES = Map.of("Tomygaims", "mentalfrostbyte/tomy.png", "Andro24", "sigma/andro.png",
            "Gretorm", "sigma/lp.png", "Flyinqq", "user/cody.png", "cxbot", "user/cx.png");

    public NameTags() { super("NameTags", Category.Render); }

    public boolean replaces(Entity entity) {
        return isNativeBehaviorActive() && entity instanceof PlayerEntity && entity != mc.player
                && !entity.isInvisible() && !SigmaEntityFilter.bot(entity);
    }

    public boolean hidesVanillaLabel(Entity entity) {
        return replaces(entity) || isNativeBehaviorActive() && mobOwners.getValue() && entity.isAlive()
                && entity instanceof Tameable tameable && tameable.getOwnerReference() != null
                && SigmaOwnerNames.name(tameable.getOwnerReference().getUuid()) != null;
    }

    @EventTarget
    public void onWorld(WorldEvent event) { clear(); }
    @Override
    public void onDisable() { clear(); }

    private void clear() { trackers.clear(); recipes.clear(); labels.clear(); furnaceLabels.clear(); interactingFurnace = activeFurnace = null; activeSyncId = -1; }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (!furnaces.getValue()) trackers.clear();
        trackers.entrySet().removeIf(entry -> mc.world.isChunkLoaded(entry.getKey().getX() >> 4, entry.getKey().getZ() >> 4)
                && !(mc.world.getBlockState(entry.getKey()).getBlock() instanceof FurnaceBlock));
        if (!trackers.isEmpty()) recipes.refresh();
        for (var entry : trackers.entrySet()) {
            SigmaFurnaceTracker tracker = entry.getValue(); tracker.recipe(recipes);
            tracker.tick(entry.getKey().equals(activeFurnace) && mc.player.currentScreenHandler.syncId == activeSyncId, mc.world.getFuelRegistry());
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!furnaces.getValue() || mc.world == null || mc.player == null) return;
        Packet<?> packet = event.getPacket();
        if (!(packet instanceof PlayerInteractBlockC2SPacket || packet instanceof OpenScreenS2CPacket
                || packet instanceof ScreenHandlerSlotUpdateS2CPacket || packet instanceof ScreenHandlerPropertyUpdateS2CPacket
                || packet instanceof InventoryS2CPacket)) return;
        var world = mc.world;
        // Incoming PacketEvent runs on Netty; all tracking and world reads stay on the client thread.
        mc.execute(() -> { if (isNativeBehaviorActive() && furnaces.getValue() && mc.world == world) handlePacket(packet); });
    }

    private void handlePacket(Packet<?> packet) {
        if (mc.player == null || mc.world == null) return;
        if (packet instanceof PlayerInteractBlockC2SPacket interaction) {
            BlockPos pos = interaction.getBlockHitResult().getBlockPos();
            interactingFurnace = mc.world.getBlockState(pos).getBlock() instanceof FurnaceBlock ? pos.toImmutable() : null;
            interactionTick = mc.player.age;
        } else if (packet instanceof OpenScreenS2CPacket open) {
            if (open.getScreenHandlerType() == ScreenHandlerType.FURNACE && interactingFurnace != null && mc.player.age - interactionTick < 100) {
                // Sync IDs are reusable. Only the most recently opened container owns incoming updates.
                activeSyncId = open.getSyncId();
                activeFurnace = interactingFurnace;
                trackers.put(activeFurnace, new SigmaFurnaceTracker(activeSyncId));
                while (trackers.size() > 256) trackers.remove(trackers.keySet().iterator().next());
            } else { activeFurnace = null; activeSyncId = -1; }
            interactingFurnace = null;
        } else {
            SigmaFurnaceTracker tracker = activeFurnace == null ? null : trackers.get(activeFurnace);
            if (tracker == null) return;
            if (packet instanceof ScreenHandlerSlotUpdateS2CPacket slot && slot.getSyncId() == activeSyncId) tracker.slot(slot.getSlot(), slot.getStack());
            else if (packet instanceof ScreenHandlerPropertyUpdateS2CPacket property && property.getSyncId() == activeSyncId) tracker.property(property.getPropertyId(), property.getValue());
            else if (packet instanceof InventoryS2CPacket inventory && inventory.syncId() == activeSyncId)
                for (int i = 0; i < Math.min(3, inventory.contents().size()); i++) tracker.slot(i, inventory.contents().get(i));
        }
    }

    private BlockPos activeFurnace;
    private int activeSyncId = -1;

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        labels.clear(); furnaceLabels.clear();
        if (mc.player == null || mc.world == null) return;
        Vec3d camera = mc.gameRenderer.getCamera().getCameraPos();
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living) || entity == mc.player || entity == mc.getCameraEntity() || !entity.isAlive()) continue;
            String name;
            if (replaces(entity)) name = entity.getName().getString();
            else if (mobOwners.getValue() && entity instanceof Tameable tameable && tameable.getOwnerReference() != null) {
                var owner = tameable.getOwnerReference();
                name = SigmaOwnerNames.name(owner.getUuid());
                if (name == null) continue;
            } else continue;
            float scale = magnify.getValue() ? SigmaGeometry.magnification(entity.squaredDistanceTo(camera), 1) : 1;
            Vec3d origin = entity.getLerpedPos(event.getTickDelta()).add(0, entity.getHeight() + .6 - (1 - scale) / 3, 0);
            SigmaProjection.Label projection = SigmaProjection.label(event, origin, .009f * scale);
            if (projection != null) labels.add(new EntityLabel(living, name.replaceAll("§.", ""), projection, camera.squaredDistanceTo(origin)));
        }
        labels.sort(Comparator.comparingDouble(EntityLabel::distance).reversed());
        if (furnaces.getValue()) for (var entry : trackers.entrySet()) {
            BlockPos pos = entry.getKey();
            float scale = magnify.getValue() ? SigmaGeometry.magnification(camera.squaredDistanceTo(Vec3d.ofCenter(pos)), .8f) : 1;
            var projection = SigmaProjection.label(event, Vec3d.ofCenter(pos).add(0, 1.1 - (1 - scale) / 3, 0), .008f * scale);
            if (projection != null) furnaceLabels.add(new FurnaceLabel(entry.getValue(), projection));
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.world == null || mc.player == null) return;
        for (EntityLabel label : labels) drawEntity(event.getContext(), label);
        for (FurnaceLabel label : furnaceLabels) drawFurnace(event.getContext(), label);
    }

    private void drawEntity(DrawContext context, EntityLabel label) {
        label.projection.begin(context);
        try {
            var font = SigmaResources.light(25);
            float half = (int) font.getStringWidth(label.name) / 2;
            float height = font.getHeight() + 27;
            String badge = BADGES.get(label.name);
            if (badge != null) {
                int color = java.awt.Color.HSBtoRGB((System.currentTimeMillis() % 10000) / 10000f, .5f, 1);
                SigmaDraw.image(context, badge, -half - 41, -25, height, height, SigmaColors.alpha(color, .7f));
                SigmaDraw.image(context, "jello/shadow_right.png", -half - 41 + height, -25, 14, height, SigmaColors.alpha(SigmaColors.WHITE, .6f));
                SigmaDraw.shadow(context, -half - 41, -25, half * 2 + 78, height, 20, .5f);
                context.getMatrices().translate(27, 0);
            } else SigmaDraw.shadow(context, -half - 10, -25, half * 2 + 20, height, 20, .5f);
            int background = BACKGROUND;
            if (instance.getFriendManager().isFriend(label.entity.getName().getString())) background = SigmaColors.alpha(-16171506, .5f);
            else {
                Teams teams = getModule(Teams.class);
                if (teams.isEnabled() && teams.isTeam(label.entity)) background = SigmaColors.alpha(-6750208, .5f);
            }
            Render2D.drawRect(context, -half - 10, -25, half * 2 + 20, height, background);
            float health = Math.clamp(label.entity.getHealth() / Math.max(1, label.entity.getMaxHealth()), 0, 1);
            float hurt = label.entity.hurtTime / 3f;
            Render2D.drawRect(context, -half - 10, font.getHeight() - 1 - hurt, (half * 2 + 20) * health, 3 + hurt,
                    SigmaColors.alpha(label.entity instanceof PlayerEntity ? label.entity.getTeamColorValue() | 0xff000000 : SigmaColors.WHITE, .5f));
            font.drawString(context, label.name, -half, -20, SigmaColors.WHITE);
            var small = SigmaResources.light(14);
            String prefix = small.getStringWidth("Health: 20.0") > half * 2 ? "H: " : "Health: ";
            small.drawString(context, prefix + Math.round(label.entity.getHealth() * 10) / 10f, -half, 10, SigmaColors.WHITE);
        } finally { label.projection.end(context); }
    }

    private void drawFurnace(DrawContext context, FurnaceLabel label) {
        label.projection.begin(context);
        try {
            var stack = label.tracker.output();
            float width = 79 + (stack.isEmpty() ? 37 : Math.max(50, SigmaResources.light(20).getStringWidth(stack.getName().getString())));
            context.getMatrices().translate(-width / 2, -56.5f);
            Render2D.drawRect(context, 0, 0, width, 113, BACKGROUND);
            SigmaDraw.shadow(context, 0, 0, width, 113, 20, .5f);
            SigmaResources.light(25).drawString(context, "Furnace", 14, 9, SigmaColors.WHITE);
            if (stack.isEmpty()) SigmaResources.light(20).drawString(context, "Empty", 29, 54, SigmaColors.alpha(SigmaColors.WHITE, .6f));
            else {
                context.getMatrices().pushMatrix();
                context.getMatrices().translate(14, 41); context.getMatrices().scale(45 / 16f, 45 / 16f);
                context.drawItem(stack, 0, 0); context.getMatrices().popMatrix();
                SigmaResources.light(20).drawString(context, stack.getName().getString(), 65, 40, SigmaColors.WHITE);
                SigmaResources.light(14).drawString(context, "Count: " + label.tracker.outputCount(), 65, 62, SigmaColors.WHITE);
            }
            Render2D.drawRect(context, 0, 101, width * label.tracker.fuelProgress(), 6, SigmaColors.alpha(-106750, .3f));
            Render2D.drawRect(context, 0, 107, width * label.tracker.cookProgress(), 6, SigmaColors.alpha(SigmaColors.WHITE, .75f));
        } finally { label.projection.end(context); }
    }

    private record EntityLabel(LivingEntity entity, String name, SigmaProjection.Label projection, double distance) {}
    private record FurnaceLabel(SigmaFurnaceTracker tracker, SigmaProjection.Label projection) {}
}
