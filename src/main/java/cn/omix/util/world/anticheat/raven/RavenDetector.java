package cn.omix.util.world.anticheat.raven;

import cn.omix.util.world.anticheat.AntiCheatEnvironment;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.math.BlockPos;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Modern Minecraft adapter. Called only by client tick / post-forceMainThread hooks. */
public final class RavenDetector implements RavenWorld {
    private final MinecraftClient mc;
    private final Map<PlayerEntity, RavenPlayer> snapshots = new IdentityHashMap<>();
    private final Map<BlockPos, Block> blocks = new HashMap<>();
    private final Set<PlayerEntity> consumed = Collections.newSetFromMap(new IdentityHashMap<>());
    private final RavenChecks checks;
    private List<String> scoreboard = List.of();
    private int tick;

    public RavenDetector(MinecraftClient mc, Consumer<RavenChecks.Detection> sink) {
        this.mc = mc;
        this.checks = new RavenChecks(this, sink);
    }

    public void clear() { snapshots.clear(); blocks.clear(); consumed.clear(); checks.clear(); tick = 0; }

    public void tick(RavenChecks.Settings settings, Predicate<PlayerEntity> suspect, Predicate<PlayerEntity> teammate, Predicate<PlayerEntity> bot,
                     double range, int limit, long now) {
        blocks.clear();
        tick++;
        String sidebar = AntiCheatEnvironment.sidebar(mc);
        scoreboard = sidebar.isEmpty() ? List.of() : sidebar.toUpperCase(Locale.ROOT).lines().toList();
        var players = mc.world.getPlayers().stream()
                .filter(p -> p.isAlive() && !p.isRemoved() && p.age >= 40 && !p.isSpectator() && !p.isCreative()
                        && !p.getAbilities().flying && !p.isGliding() && !p.hasVehicle()
                        && mc.player.squaredDistanceTo(p) <= range * range && mc.world.isChunkLoaded(p.getBlockPos()))
                .sorted(Comparator.comparingDouble(p -> mc.player.squaredDistanceTo(p))).limit(limit).toList();
        snapshots.keySet().retainAll(players);
        List<RavenPlayer> observations = new ArrayList<>();
        for (PlayerEntity player : players) {
            RavenPlayer snapshot = snapshots.computeIfAbsent(player,
                    p -> new RavenPlayer(p, p.getUuid(), p.getGameProfile().name()));
            snapshot(player, snapshot);
            snapshot.suspect = suspect.test(player);
            snapshot.teammate = teammate.test(player);
            snapshot.bot = bot.test(player);
            observations.add(snapshot);
        }
        consumed.clear();
        checks.tick(observations, snapshots.get(mc.player), now, settings);
    }

    private void snapshot(PlayerEntity player, RavenPlayer s) {
        RavenVec3 current = new RavenVec3(player.getX(), player.getY(), player.getZ());
        s.discontinuity = s.ticks == 0 || s.position.distanceTo(current) > 8;
        s.lastPosition = s.discontinuity ? current : s.position;
        s.position = current;
        s.prevYaw = s.discontinuity ? player.getYaw() : s.yaw;
        s.prevPitch = s.discontinuity ? player.getPitch() : s.pitch;
        s.yaw = player.getYaw(); s.pitch = player.getPitch(); s.ticks = tick;
        s.swing = player.handSwinging ? player.handSwingTicks : 0;
        s.sneaking = player.isSneaking(); s.sprinting = player.isSprinting(); s.using = player.isUsingItem();
        s.blocking = player.isBlocking(); s.collided = player.horizontalCollision || player.verticalCollision;
        s.dead = !player.isAlive(); s.burning = player.isOnFire(); s.ground = player.isOnGround();
        s.water = player.isTouchingWater(); s.lava = player.isInLava(); s.ladder = player.isClimbing();
        s.creative = player.isCreative(); s.invisible = player.isInvisible();
        s.health = player.getHealth(); s.absorption = player.getAbsorptionAmount();
        s.hurtTime = player.hurtTime; s.maxHurtTime = player.maxHurtTime;
        s.displayName = player.getDisplayName() == null ? s.name : player.getDisplayName().getString();
        s.consumed = consumed.contains(player);
        s.heldItem = item(player.getMainHandStack());
        // Modern offhand use is meaningful to NoSlow/FastEat as well as shields.
        if (player.isUsingItem() && !player.getActiveItem().isEmpty()) s.heldItem = item(player.getActiveItem());
        s.effects = new ArrayList<>();
        for (var effect : player.getStatusEffects()) {
            int id = effect.getEffectType().equals(StatusEffects.SPEED) ? 1
                    : effect.getEffectType().equals(StatusEffects.SLOWNESS) ? 2
                    : effect.getEffectType().equals(StatusEffects.HASTE) ? 3
                    : effect.getEffectType().equals(StatusEffects.JUMP_BOOST) ? 8
                    : effect.getEffectType().equals(StatusEffects.REGENERATION) ? 10 : -1;
            if (id > 0) s.effects.add(new Object[]{id, effect.getDuration(), effect.getAmplifier()});
        }
        s.groundDistance = 64;
        for (int i = 0; i <= 64; i++) {
            BlockPos below = BlockPos.ofFloored(s.position.x, s.position.y - 0.01 - i, s.position.z);
            if (!mc.world.isChunkLoaded(below) || !mc.world.getBlockState(below).getCollisionShape(mc.world, below).isEmpty()) {
                s.groundDistance = Math.max(0, s.position.y - below.getY() - 1); break;
            }
        }
        s.edge = s.ground && mc.world.isSpaceEmpty(player, player.getBoundingBox().offset(0, -0.1, 0).contract(0.1, 0, 0.1));
    }

    private RavenItem item(ItemStack stack) {
        if (stack.isEmpty()) return null;
        String name = Registries.ITEM.getId(stack.getItem()).getPath();
        String type = stack.getItem() instanceof BlockItem ? "BlockItem"
                : stack.isIn(ItemTags.SWORDS) ? "ItemSword" : stack.isIn(ItemTags.AXES) ? "ItemAxe"
                : stack.isIn(ItemTags.PICKAXES) ? "ItemPickaxe" : stack.isIn(ItemTags.SHOVELS) ? "ItemSpade"
                : stack.isIn(ItemTags.HOES) ? "ItemHoe" : stack.contains(DataComponentTypes.FOOD) ? "ItemFood"
                : name.contains("bow") ? "ItemBow" : name.equals("fishing_rod") ? "ItemFishingRod"
                : name.equals("stick") ? "ItemStick" : "Item";
        if (stack.getItem() instanceof BlockItem block) name = legacyBlockName(block.getBlock().getDefaultState());
        var efficiency = mc.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOptional(Enchantments.EFFICIENCY);
        int level = efficiency.map(entry -> EnchantmentHelper.getLevel(entry, stack)).orElse(0);
        var use = stack.get(DataComponentTypes.CONSUMABLE);
        return new RavenItem(name, type, stack.getCount(), use == null ? 0 : use.getConsumeTicks(), level);
    }

    public void block(BlockPos pos, BlockState old, BlockState replacement, long now) {
        blocks.remove(pos);
        if (!old.isAir() && replacement.isAir()) checks.block(vector(pos), true, old.getBlock() instanceof BedBlock, now);
        else if (old.isAir() && !replacement.isAir() && replacement.getFluidState().isEmpty()) checks.block(vector(pos), false, false, now);
    }

    public void packet(Packet<?> packet) {
        if (packet instanceof EntityVelocityUpdateS2CPacket velocity
                && mc.world.getEntityById(velocity.getEntityId()) instanceof PlayerEntity player) {
            RavenPlayer snapshot = snapshots.get(player);
            if (snapshot != null) checks.velocity(snapshot, velocity.getVelocity().horizontalLength());
        } else if (packet instanceof EntityStatusS2CPacket status && status.getStatus() == 9
                && status.getEntity(mc.world) instanceof PlayerEntity player) consumed.add(player);
    }

    private static RavenVec3 vector(BlockPos pos) { return new RavenVec3(pos.getX(), pos.getY(), pos.getZ()); }

    @Override public Block getBlockAt(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        // Treat missing chunks as unknown solid coverage, not evidence of flight/void.
        if (mc.world == null || !mc.world.isChunkLoaded(pos)) return new Block("unknown");
        if (blocks.size() > 16384) blocks.clear();
        return blocks.computeIfAbsent(pos, key -> new Block(legacyBlockName(mc.world.getBlockState(key))));
    }
    @Override public List<String> getScoreboard() { return scoreboard; }
    @Override public int bottomY() { return mc.world == null ? -64 : mc.world.getBottomY(); }

    static String legacyBlockName(BlockState state) {
        if (state.isAir()) return "air";
        String name = Registries.BLOCK.getId(state.getBlock()).getPath();
        if (state.getBlock() instanceof BedBlock) return "bed";
        if (name.endsWith("_wool")) return "wool";
        if (name.endsWith("_planks")) return "planks";
        if (name.endsWith("_log") || name.endsWith("_wood")) return "log";
        if (name.endsWith("_leaves")) return "leaves";
        if (name.endsWith("_carpet")) return "carpet";
        if (name.endsWith("_stained_glass")) return "stained_glass";
        return switch (name) {
            case "cobweb" -> "web"; case "lily_pad" -> "waterlily"; case "grass_block" -> "grass";
            case "short_grass", "tall_grass", "fern" -> "tallgrass"; case "snow" -> "snow_layer";
            case "snow_block" -> "snow"; case "terracotta" -> "hardened_clay";
            case "slime_block" -> "slime";
            default -> name;
        };
    }
}
