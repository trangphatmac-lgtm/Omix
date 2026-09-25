package cn.omix.module.impl.move;

import cn.omix.event.base.annotation.EventPriority;
import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.*;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.module.value.impl.ModeValue;
import cn.omix.module.impl.player.AutoBlockIn;
import cn.omix.module.impl.world.Scaffold;
import cn.omix.util.network.PacketUtil;
import cn.omix.util.player.noslow.GrimNoSlowState;
import cn.omix.util.player.noslow.GrimNoSlowPackets;
import injection.accessor.LivingEntityAccessor;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.*;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.common.*;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.util.Hand;

import static cn.omix.util.player.noslow.GrimNoSlowPackets.describe;

public class NoSlowDown extends Module implements GrimNoSlowState.Host {
    private final ModeValue mode = new ModeValue("Mode", "Vanilla", "Vanilla", "Grim Full");
    private final BoolValue keepSprint = new BoolValue("Keep Sprint", true);
    private static final int MAX_BUFFERED_PACKETS = 4096;
    private final GrimNoSlowState flow = new GrimNoSlowState(this);
    private final GrimNoSlowPackets receivePolicy = new GrimNoSlowPackets();
    private ClientPlayNetworkHandler connection;
    private ClientWorld world;
    private ClientPlayerEntity player;
    private boolean cancelled;
    private boolean slowdownDisabled;
    private boolean replaying;
    private Packet<?> receiving;

    public NoSlowDown() {
        super("NoSlowDown", Category.Move);
    }

    /** Called by client-thread mixins only; also restores state on a live mode change. */
    public static NoSlowDown activeGrim() {
        if (instance == null || instance.getModuleManager() == null) return null;
        NoSlowDown module = instance.getModuleManager().getModule(NoSlowDown.class);
        if (module == null || !module.isNativeBehaviorActive()) return null;
        if (module.mode.is("Grim Full")) return module;
        module.reset();
        return null;
    }

    public boolean allowGrimSprint() {
        return isNativeBehaviorActive() && mode.is("Grim Full") && keepSprint.getValue() && cancelSlowdown();
    }

    /** Native-protocol equivalent of the supplied NoFall reference's NoSlow phase query. */
    public boolean isGrimActivePhase() {
        return isNativeBehaviorActive() && mode.is("Grim Full") && context() && flow.isActivePhase();
    }

    @Override
    public void onEnable() {
        discard();
        setSuffix(mode.getValue());
    }

    @Override
    public void onDisable() {
        reset();
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        discard();
    }

    @EventTarget
    @EventPriority(0)
    public void onTick(TickEvent event) {
        setSuffix(mode.getValue());
        if (mode.is("Grim Full")) tick();
        else reset();
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mode.is("Grim Full")) update();
    }

    @EventTarget
    @EventPriority(1000)
    public void onPacket(PacketEvent event) {
        if (event.getType() != PacketEvent.Type.Send || !mc.isOnThread() || event.isCancelled()) return;
        if (!mode.is("Grim Full")) {
            reset();
            return;
        }
        if (outgoing(event.getPacket())) event.setCancelled(true);
    }

    @EventTarget
    public void onScroll(MouseScrollEvent event) {
        if (mc.currentScreen == null && mode.is("Grim Full") && lockSlot()) event.setCancelled(true);
    }

    @EventTarget
    public void onSlow(SlowEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (mc.player.isUsingItem() && !mc.player.getActiveItem().isEmpty()
                && (mode.is("Vanilla") || cancelSlowdown())) {
            event.setCancelled(true);
            if (keepSprint.getValue()) mc.player.setSprinting(true);
        }
    }

    private boolean context() {
        if (connection != mc.getNetworkHandler() || world != mc.world || player != mc.player) {
            discard();
            connection = mc.getNetworkHandler();
            world = mc.world;
            player = mc.player;
        }
        return connection != null && world != null && player != null;
    }

    private void tick() {
        if (context()) flow.onClientTick(true);
    }

    private void update() {
        if (context()) flow.onPlayerUpdate();
    }

    public void abort() {
        if (context()) flow.abortActive();
    }

    private void reset() {
        if (context()) flow.onDisable();
        else discard();
    }

    /** World changes must never replay old inventory/entity updates into the new world. */
    private void discard() {
        connection = null;
        world = null;
        player = null;
        flow.discardSession();
    }

    public boolean beforeUse(Hand hand) {
        if (!context() || blocked()) return false;
        if (flow.shouldBlockUseAfterInventoryClick()) return true;
        cancelled = false;
        flow.onUseItem(toHand(hand), item(mc.player.getStackInHand(hand)));
        return cancelled;
    }

    public boolean blockUseThisTick() {
        return context() && !blocked() && flow.shouldBlockUseAfterInventoryClick();
    }

    public boolean lockSlot() {
        if (!context() || !flow.shouldLockHotbar()) return false;
        flow.claimLockedSlot();
        return true;
    }

    private boolean cancelSlowdown() {
        if (!context() || !receivePolicy.allowsNoSlow()) return false;
        slowdownDisabled = false;
        flow.onSlowdown();
        return slowdownDisabled;
    }

    private boolean outgoing(Packet<?> packet) {
        if (!context() || replaying) return false;
        cancelled = false;
        // Finish pending use/inventory updates before an explicit inventory operation.
        if (packet instanceof ClickSlotC2SPacket
                || packet instanceof PlayerActionC2SPacket action
                && action.getAction() != PlayerActionC2SPacket.Action.RELEASE_USE_ITEM) {
            flow.abortActive();
        }
        flow.onPacket(GrimNoSlowState.Direction.SEND, describe(packet));
        return cancelled;
    }

    public boolean receive(Packet<?> packet, PacketListener listener) {
        if (replaying || !context() || listener != connection) return false;
        // Connection/lifecycle control cannot wait for the player to finish eating.
        if (packet.transitionsNetworkState() || packet instanceof DisconnectS2CPacket
                || packet instanceof GameJoinS2CPacket || packet instanceof PlayerRespawnS2CPacket) {
            discard();
            return false;
        }
        // Match the original shared queue's exclusions as well as NoSlow's policy.
        // Pings after the use-metadata boundary must wait with that metadata.
        if (packet instanceof PlayerPositionLookS2CPacket) {
            flow.abortActive();
            return false;
        }
        if (!flow.isBuffering()) return false;
        if (receivePolicy.bypassesBuffer(packet, player.getId(), LivingEntityAccessor.omix$getLivingFlags().id(), flow.getUseHand())) return false;
        if (receivePolicy.size() >= MAX_BUFFERED_PACKETS) {
            flow.onDisable();
            return false;
        }
        cancelled = false;
        receiving = packet;
        try {
            flow.onQueueEvent(GrimNoSlowState.Direction.RECEIVE, describe(packet));
            return cancelled;
        } finally {
            receiving = null;
        }
    }

    private static GrimNoSlowState.Hand toHand(Hand hand) {
        return hand == Hand.MAIN_HAND ? GrimNoSlowState.Hand.MAIN_HAND : GrimNoSlowState.Hand.OFF_HAND;
    }

    private static Hand toHand(GrimNoSlowState.Hand hand) {
        return hand == GrimNoSlowState.Hand.MAIN_HAND ? Hand.MAIN_HAND : Hand.OFF_HAND;
    }

    private static GrimNoSlowState.Item item(ItemStack stack) {
        var type = stack.getItem() instanceof BowItem ? GrimNoSlowState.ItemType.BOW
                : stack.getItem() instanceof CrossbowItem ? GrimNoSlowState.ItemType.CROSSBOW
                : stack.getItem() instanceof PotionItem ? GrimNoSlowState.ItemType.POTION
                : GrimNoSlowState.ItemType.OTHER;
        var action = switch (stack.getUseAction()) {
            case BOW -> GrimNoSlowState.Action.BOW;
            case CROSSBOW -> GrimNoSlowState.Action.CROSSBOW;
            case DRINK -> GrimNoSlowState.Action.DRINK;
            default -> GrimNoSlowState.Action.OTHER;
        };
        return new GrimNoSlowState.Item(stack.isEmpty(), type, action, stack.contains(DataComponentTypes.FOOD),
                CrossbowItem.isCharged(stack), stack.isOf(Items.FIRE_CHARGE), stack.isOf(Items.WATER_BUCKET),
                stack.isOf(Items.ENDER_PEARL));
    }

    @Override public boolean playerPresent() { return mc.player != null; }
    @Override public boolean worldPresent() { return mc.world != null; }
    @Override public int age() { return mc.player.age; }
    @Override public int playerId() { return mc.player.getId(); }
    @Override public int selectedSlot() { return mc.player.getInventory().getSelectedSlot(); }
    @Override public boolean isUsingItem() { return mc.player.isUsingItem(); }
    @Override public GrimNoSlowState.Item mainHand() { return item(mc.player.getMainHandStack()); }
    @Override public GrimNoSlowState.Item offHand() { return item(mc.player.getOffHandStack()); }
    @Override public GrimNoSlowState.Item activeItem() { return item(mc.player.getActiveItem()); }
    @Override public GrimNoSlowState.Hit crosshair() {
        return mc.crosshairTarget == null ? null : switch (mc.crosshairTarget.getType()) {
            case MISS -> GrimNoSlowState.Hit.MISS;
            case BLOCK -> GrimNoSlowState.Hit.BLOCK;
            case ENTITY -> GrimNoSlowState.Hit.ENTITY;
        };
    }
    @Override public double fallDistance() { return mc.player.fallDistance; }
    @Override public boolean blocked() {
        NoFall noFall = instance.getModuleManager().getModule(NoFall.class);
        return mc.player == null || !mc.player.isAlive() || mc.player.isSpectator() || mc.player.hasVehicle()
                || AutoBlockIn.blocksMouseInput() || playerUpdateBlocked()
                || noFall != null && noFall.isGrimSilentRotationActive();
    }
    @Override public boolean playerUpdateBlocked() {
        Scaffold scaffold = instance.getModuleManager().getModule(Scaffold.class);
        return scaffold != null && scaffold.isEnabled();
    }
    @Override public boolean entityIsEnderPearl(int id) {
        return mc.world != null && mc.world.getEntityById(id) instanceof EnderPearlEntity;
    }
    @Override public boolean requestSlot(Object owner, int slot) {
        if (player != null && player == mc.player && world == mc.world) {
            player.getInventory().setSelectedSlot(slot);
            return true;
        }
        return false;
    }
    @Override public void releaseSlot(Object owner) { /* Vanilla slot sync resumes when flow clears its lock. */ }
    @Override public void clearReceiveQueue(Object owner) {
        // A clear must not apply packets or emit pongs. Explicit flush owns replay.
        receivePolicy.clear();
    }
    @Override public void flushReceiveQueue(Object owner) {
        replaying = true;
        try {
            receivePolicy.flush(() -> connection != null && connection == mc.getNetworkHandler()
                    && world == mc.world && player == mc.player, PacketUtil::receivePacket);
        } finally {
            replaying = false;
        }
    }
    @Override public void queueIncoming(Object owner) { receivePolicy.enqueue(receiving); cancelled = true; }
    @Override public boolean useCopy(GrimNoSlowState.Item ignored, GrimNoSlowState.Hand hand) {
        return mc.player.getStackInHand(toHand(hand)).copy().use(mc.world, mc.player, toHand(hand)).isAccepted();
    }
    @Override public void cancelEvent() { cancelled = true; }
    @Override public void disableSlowdown() { slowdownDisabled = true; }

}
