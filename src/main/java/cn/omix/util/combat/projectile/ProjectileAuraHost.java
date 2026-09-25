package cn.omix.util.combat.projectile;

import cn.omix.event.impl.RotationRequestEvent;
import cn.omix.management.RotationManager;
import cn.omix.management.rotation.RotationRequest;
import cn.omix.module.Module;
import cn.omix.module.impl.combat.Aura;
import cn.omix.module.impl.combat.ProjectileAura;
import cn.omix.module.impl.exploits.Blink;
import cn.omix.module.impl.move.LongJump;
import cn.omix.module.impl.move.NoSlowDown;
import cn.omix.module.impl.player.AutoBlockIn;
import cn.omix.module.impl.world.Scaffold;
import cn.omix.module.impl.world.ScaffoldX;
import cn.omix.util.IMinecraft;
import cn.omix.util.player.EntityUtil;
import injection.accessor.ClientPlayerInteractionManagerAccessor;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static cn.omix.util.combat.projectile.ProjectileAuraEngine.*;

public final class ProjectileAuraHost implements ProjectileAuraEngine.Host, IMinecraft {
    public static final int ROTATION_PRIORITY = 450;
    private final ProjectileAura module;
    private final Map<net.minecraft.entity.Entity, EntityView> entities = new IdentityHashMap<>();
    private final ProjectileSlotState slots = new ProjectileSlotState();
    private ClientPlayerEntity player;
    private ClientWorld world;
    private ClientPlayNetworkHandler connection;
    private Rotation rotation;
    private RotationRequest request;
    private int update;
    private int rotationExpires;

    public ProjectileAuraHost(ProjectileAura module) { this.module = module; }

    public boolean contextChanged() {
        return player != mc.player || world != mc.world || connection != mc.getNetworkHandler();
    }

    public void discard() {
        RotationManager.release(request);
        rotation = null;
        request = null;
        slots.clear();
        entities.clear();
        player = mc.player;
        world = mc.world;
        connection = mc.getNetworkHandler();
        update = rotationExpires = 0;
    }

    public void beginUpdate() {
        update++;
        if (player != null) {
            flushSlotRelease();
            slots.observe(selectedSlot());
        }
        entities.entrySet().removeIf(entry -> entry.getKey().isRemoved());
        if (update > rotationExpires) finishUpdate(null);
    }

    public void finishUpdate(Rotation owned) {
        if (owned != rotation || owned == null) {
            RotationManager.release(request);
            rotation = null;
            request = null;
        }
    }

    public void submitRotation(RotationRequestEvent event, Rotation owned) {
        if (!contextChanged() && request != null && owned == rotation && update <= rotationExpires) {
            event.submit(request);
        }
    }

    @Override public long nowMs() { return System.currentTimeMillis(); }
    @Override public boolean moduleEnabled() { return module.isNativeBehaviorActive(); }
    @Override public boolean playerPresent() { return mc.player != null; }
    @Override public boolean worldPresent() { return mc.world != null; }
    @Override public boolean networkPresent() { return mc.getNetworkHandler() != null; }
    @Override public boolean interactionManagerPresent() { return mc.interactionManager != null; }
    @Override public Vec3 playerEyePosition() { return vector(mc.player.getEyePos()); }
    @Override public double squaredDistanceToPlayer(Entity entity) { return mc.player.squaredDistanceTo(unwrap(entity)); }
    @Override public float distanceToPlayer(Entity entity) { return mc.player.distanceTo(unwrap(entity)); }
    @Override public List<Entity> crystalsInPlayerBoxExpandedBy(double blocks) {
        return mc.world.getEntitiesByClass(EndCrystalEntity.class, mc.player.getBoundingBox().expand(blocks), e -> true)
                .stream().map(this::entity).toList();
    }
    @Override public List<Entity> worldPlayers() { return mc.world.getPlayers().stream().map(this::entity).toList(); }
    @Override public Entity killAuraTarget() {
        Aura aura = module.getModule(Aura.class);
        return aura == null ? null : entity(aura.getTarget());
    }
    @Override public boolean killAuraEnabled() { return enabled(Aura.class); }
    @Override public boolean killAuraAttacking() {
        Aura aura = module.getModule(Aura.class);
        return aura != null && aura.isAttackingForProjectileAura();
    }
    @Override public boolean excludedByEntityPolicy(Entity entity) {
        return unwrap(entity) == mc.player || entity.isRemoved();
    }
    @Override public boolean combatTargetPredicate(Entity entity) { return EntityUtil.isSelected(unwrap(entity)); }
    @Override public boolean playerCanSee(Entity entity) { return mc.player.canSee(unwrap(entity)); }
    @Override public boolean scaffoldEnabled() { return enabled(Scaffold.class) || enabled(ScaffoldX.class); }
    @Override public boolean blinkEnabled() { return enabled(Blink.class); }
    @Override public boolean playerUsingItem() { return mc.player.isUsingItem(); }
    @Override public boolean externalMovementBusy() {
        LongJump jump = module.getModule(LongJump.class);
        AutoBlockIn blockIn = module.getModule(AutoBlockIn.class);
        return jump != null && jump.isUsingItemThisTick() || blockIn != null && blockIn.isPlacing();
    }
    @Override public boolean noSlowBusy() {
        var grim = NoSlowDown.activeGrim();
        return grim != null && (grim.isGrimActivePhase() || grim.lockSlot());
    }
    @Override public boolean crosshairIsEntityHit() { return mc.crosshairTarget instanceof EntityHitResult; }
    @Override public Entity crosshairEntity() { return entity(((EntityHitResult) mc.crosshairTarget).getEntity()); }
    @Override public Item mainHandItem() { return wrap(mc.player.getMainHandStack()); }
    @Override public Item offHandItem() { return wrap(mc.player.getOffHandStack()); }
    @Override public Item hotbarItem(int slot) { return wrap(mc.player.getInventory().getStack(slot)); }
    @Override public int selectedSlot() { return mc.player.getInventory().getSelectedSlot(); }

    @Override public boolean isAllowedEggOrSnowball(Item item) {
        ItemStack stack = unwrap(item);
        return !stack.isEmpty() && !stack.isOf(Items.WIND_CHARGE)
                && !ProjectileItemPolicy.isWindChargeName(stack.toHoverableText().getString())
                && (stack.isOf(Items.EGG) || stack.isOf(Items.SNOWBALL));
    }

    @Override public float yawBetween(Vec3 from, Vec3 to) {
        return (float) (Math.toDegrees(Math.atan2(to.z() - from.z(), to.x() - from.x())) - 90);
    }
    @Override public boolean worldIsAir(int x, int y, int z) { return mc.world.getBlockState(new BlockPos(x, y, z)).isAir(); }
    @Override public boolean rayHitsBlock(Vec3 start, Vec3 end) {
        return mc.world.raycast(new RaycastContext(vector(start), vector(end), RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, mc.player)).getType() == HitResult.Type.BLOCK;
    }

    @Override public boolean requestRotation(Rotation value, double maxTurn, int ticks, Priority priority) {
        RotationRequest active = RotationManager.getActiveRequest();
        if (active != null && active != request && active.priority() > ROTATION_PRIORITY) return false;
        rotation = value;
        request = RotationRequest.builder(module.getName(), new float[]{value.yaw, value.pitch}, ROTATION_PRIORITY)
                .speed(maxTurn).instant(false).build();
        rotationExpires = update + ticks;
        return true;
    }
    @Override public boolean rotationActive() { return RotationManager.isRotating(); }
    @Override public Rotation currentRotation() {
        float[] current = RotationManager.currentRotations;
        return current == null ? null : new Rotation(current[0], current[1]);
    }
    @Override public Rotation requestedRotation() {
        return request != null && RotationManager.getActiveRequest() == request ? rotation : null;
    }
    @Override public Priority rotationPriority() { return requestedRotation() == null ? null : Priority.HIGH; }
    @Override public void setRotationActive(boolean active) {
        if (!active) RotationManager.release(request);
    }

    @Override public boolean requestSlot(Object owner, int slot, Priority priority) {
        if (contextChanged() || mc.player == null || slot < 0 || slot > 8 || externalMovementBusy() || noSlowBusy()) return false;
        slots.select(selectedSlot(), slot);
        switchSlot(slot);
        return true;
    }
    @Override public void releaseSlot(Object owner) { slots.deferRelease(); }
    @Override public void releaseSlotImmediately(Object owner) {
        if (!contextChanged() && mc.player != null) switchSlot(slots.release(selectedSlot()));
        else slots.clear();
    }
    @Override public int originalSlotFor(Object owner) { return slots.original(); }
    @Override public boolean ownsSlotRequest(Object owner) {
        return !contextChanged() && mc.player != null && slots.owns(selectedSlot());
    }

    public void flushSlotRelease() {
        if (!contextChanged() && mc.player != null) switchSlot(slots.flush(selectedSlot()));
        else slots.clear();
    }

    private void switchSlot(int slot) {
        if (slot < 0 || slot > 8 || mc.player == null) return;
        mc.player.getInventory().setSelectedSlot(slot);
        if (mc.interactionManager != null && mc.getNetworkHandler() != null) {
            ((ClientPlayerInteractionManagerAccessor) mc.interactionManager).omix$syncSelectedSlot();
        }
    }

    @Override public void interactItem(Hand hand) {
        // Vanilla's item-use packet reads player yaw/pitch. Apply the already arbitrated
        // rotation for this call only, so the packet and local projectile agree.
        float yaw = mc.player.getYaw(), pitch = mc.player.getPitch();
        try {
            if (request != null && RotationManager.getActiveRequest() == request && RotationManager.isRotating()) {
                mc.player.setYaw(RotationManager.currentRotations[0]);
                mc.player.setPitch(RotationManager.currentRotations[1]);
            }
            mc.interactionManager.interactItem(mc.player, hand(hand));
        } finally {
            mc.player.setYaw(yaw);
            mc.player.setPitch(pitch);
        }
    }
    @Override public void sendHandSwingPacket(Hand hand) {
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(hand(hand)));
    }

    private boolean enabled(Class<? extends Module> type) {
        Module other = module.getModule(type);
        return other != null && other.isEnabled();
    }

    private Entity entity(net.minecraft.entity.Entity value) {
        return value == null ? null : entities.computeIfAbsent(value, EntityView::new);
    }
    private static net.minecraft.entity.Entity unwrap(Entity value) { return ((EntityView) value).entity; }
    public Item wrap(ItemStack stack) { return new ItemView(stack); }
    public ItemStack unwrap(Item item) { return ((ItemView) item).stack; }
    public static Hand hand(net.minecraft.util.Hand hand) {
        return hand == net.minecraft.util.Hand.MAIN_HAND ? Hand.MAIN_HAND : Hand.OFF_HAND;
    }
    private static net.minecraft.util.Hand hand(Hand hand) {
        return hand == Hand.MAIN_HAND ? net.minecraft.util.Hand.MAIN_HAND : net.minecraft.util.Hand.OFF_HAND;
    }
    private static Vec3 vector(Vec3d value) { return new Vec3(value.x, value.y, value.z); }
    private static Vec3d vector(Vec3 value) { return new Vec3d(value.x(), value.y(), value.z()); }

    private record ItemView(ItemStack stack) implements Item {
        @Override public boolean isEmpty() { return stack.isEmpty(); }
        @Override public boolean isFishingRod() { return stack.isOf(Items.FISHING_ROD); }
    }

    private static final class EntityView implements Entity {
        private final net.minecraft.entity.Entity entity;
        private EntityView(net.minecraft.entity.Entity entity) { this.entity = entity; }
        @Override public Vec3 position() { return new Vec3(entity.getX(), entity.getY(), entity.getZ()); }
        @Override public Vec3 previousPosition() { return new Vec3(entity.lastX, entity.lastY, entity.lastZ); }
        @Override public float height() { return entity.getHeight(); }
        @Override public boolean isEndCrystal() { return entity instanceof EndCrystalEntity; }
        @Override public boolean isLiving() { return entity instanceof LivingEntity; }
        @Override public boolean isRemoved() { return entity.isRemoved(); }
        @Override public int hurtTime() { return entity instanceof LivingEntity living ? living.hurtTime : 0; }
    }
}
