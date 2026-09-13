package cn.omix.module.impl.player.chest;

import cn.omix.module.impl.player.ChestArua;
import cn.omix.module.impl.player.ChestStealer;
import cn.omix.util.IMinecraft;
import injection.accessor.ClientPlayerEntityAccessor;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.PlayerInput;

/** Pauses real input and waits for vanilla synchronization before container actions. */
public final class ChestScreenGuard implements IMinecraft {
    private static final ChestScreenState state = new ChestScreenState();
    private static GenericContainerScreen screen;
    private static ClientPlayerEntity owner;
    private static ClientWorld world;
    private static boolean resumeSprint;

    private ChestScreenGuard() {}

    public static void screenChanged() {
        if (screen == mc.currentScreen && owner == mc.player && world == mc.world) return;
        boolean resume = resumeSprint && owner == mc.player && world == mc.world;
        clear();
        if (mc.currentScreen instanceof GenericContainerScreen container && modulesActive()
                && mc.player != null && mc.world != null && mc.player.isAlive()) {
            screen = container;
            owner = mc.player;
            world = mc.world;
            resumeSprint = resume || owner.isSprinting();
            state.open(container.getScreenHandler().syncId);
        } else if (resume && mc.currentScreen == null && mc.player != null && mc.player.isAlive()) {
            mc.player.setSprinting(true);
        }
    }

    private static boolean modulesActive() {
        if (instance == null || instance.getModuleManager() == null) return false;
        ChestArua aura = instance.getModuleManager().getModule(ChestArua.class);
        ChestStealer stealer = instance.getModuleManager().getModule(ChestStealer.class);
        return aura != null && aura.isEnabled() || stealer != null && stealer.isEnabled();
    }

    public static boolean suppressInput() {
        return state.active() && owner == mc.player && world == mc.world
                && screen == mc.currentScreen && owner != null && owner.isAlive();
    }

    public static void beforePlayerMovement() {
        // Also supports enabling a module while a chest is already open.
        if (!state.active()) screenChanged();
        if (suppressInput()) owner.setSprinting(false);
    }

    public static void playerTickCompleted() {
        if (!suppressInput()) return;
        ClientPlayerEntityAccessor sent = (ClientPlayerEntityAccessor) owner;
        state.completePlayerTick(owner.currentScreenHandler.syncId,
                PlayerInput.DEFAULT.equals(sent.omix$getLastPlayerInput())
                        && PlayerInput.DEFAULT.equals(owner.input.playerInput),
                !sent.omix$wasSprinting() && !owner.isSprinting());
    }

    public static boolean canUseContainer() {
        if (!state.active()) screenChanged();
        return !suppressInput() || state.ready(owner.currentScreenHandler.syncId);
    }

    public static boolean deferClose() {
        if (!state.active()) screenChanged();
        return suppressInput() && state.deferClose(owner.currentScreenHandler.syncId);
    }

    public static void clientTick() {
        if (state.active() && (owner != mc.player || world != mc.world || !owner.isAlive())) {
            clear();
            return;
        }
        if (suppressInput() && state.shouldClose(owner.currentScreenHandler.syncId)) {
            // Next client tick, before input and physics resume. Vanilla still sends
            // CLOSE before setScreen(null) releases the input/sprint suppression.
            screen.close();
        }
    }

    public static void clear() {
        state.reset();
        screen = null;
        owner = null;
        world = null;
        resumeSprint = false;
    }
}
