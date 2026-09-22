package cn.omix.script.api;

import cn.omix.Client;
import cn.omix.event.base.Event;
import cn.omix.event.impl.*;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.management.rotation.RotationRequest;
import cn.omix.util.script.*;
import cn.omix.util.network.PacketUtil;
import cn.omix.util.misc.TimerSpeedUtil;
import com.google.gson.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.math.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Version 1 API automatically available to Java source fragments. Native Java remains available. */
public abstract class ScriptApi {
    public static final int API_VERSION = 1;
    public final MinecraftClient mc = MinecraftClient.getInstance();
    public final Client client = Client.instance;
    public final ScriptContext script;
    public final Game game = new Game();
    public final Movement movement = new Movement();
    public final Inventory inventory = new Inventory();
    public final Modules modules = new Modules();
    public final Modes modes = new Modes();
    public final Commands commands = new Commands();
    public final Events events = new Events();
    public final Tasks tasks = new Tasks();
    public final Packets packets = new Packets();
    public final Render render = new Render();
    public final Ui ui = new Ui();
    public final Storage storage = new Storage();
    public final Managers managers = new Managers();
    public final cn.omix.fisproxy.FisProxyManager fisproxy = Client.instance.getFisProxyManager();
    public final NativeAccess nativeAccess = new NativeAccess();
    protected ScriptApi(ScriptContext context) { script = context; }
    public void log(Object message) { script.log(message); }
    public boolean inWorld() { return mc.player != null && mc.world != null; }
    public void requireClientThread() { script.requireActive(); if (!mc.isOnThread()) throw new IllegalStateException("Use tasks.client for this operation."); }
    public final class Game {
        public net.minecraft.client.network.ClientPlayerEntity player() { requireClientThread(); return mc.player; }
        public net.minecraft.client.world.ClientWorld world() { requireClientThread(); return mc.world; }
        public List<net.minecraft.entity.Entity> entities(double range) {
            requireWorld(); if (!Double.isFinite(range) || range < 0) throw new IllegalArgumentException("Invalid entity range");
            var found = new ArrayList<net.minecraft.entity.Entity>();
            for (var entity : mc.world.getEntities()) if (entity != mc.player && mc.player.squaredDistanceTo(entity) <= range * range) found.add(entity);
            found.sort(Comparator.comparingDouble(mc.player::squaredDistanceTo)); return List.copyOf(found);
        }
        public net.minecraft.block.BlockState block(BlockPos position) { requireWorld(); return mc.world.getBlockState(position); }
        public net.minecraft.util.hit.HitResult crosshair() { requireWorld(); return mc.crosshairTarget; }
        public List<net.minecraft.entity.LivingEntity> targets() { requireWorld(); return List.copyOf(managers.targets().getTargets()); }
        public boolean friend(String name) { requireClientThread(); return managers.friends().isFriend(name); }
        public float[] rotationTo(Vec3d position) { requireWorld(); return cn.omix.util.player.RotationUtil.getRotations(position); }
        public List<Vec3d> path(Vec3d from, Vec3d to) { requireWorld(); return List.copyOf(cn.omix.util.player.pathfinder.MainPathFinder.computePath(from, to)); }
        public net.minecraft.util.ActionResult useItem(net.minecraft.util.Hand hand) { requireWorld(); return mc.interactionManager.interactItem(mc.player, hand); }
        public net.minecraft.util.ActionResult useBlock(net.minecraft.util.Hand hand, net.minecraft.util.hit.BlockHitResult hit) { requireWorld(); return mc.interactionManager.interactBlock(mc.player, hand, hit); }
        public void attack(net.minecraft.entity.Entity entity) { requireWorld(); mc.interactionManager.attackEntity(mc.player, entity); }
    }
    public final class Movement {
        public boolean moving() { requireWorld(); return cn.omix.util.player.MovementUtil.isMoving(); }
        public double speed() { requireWorld(); return cn.omix.util.player.MovementUtil.getSpeed(); }
        public double blocksPerSecond() { requireWorld(); return cn.omix.util.player.MovementUtil.getBPS(); }
        public void strafe(double speed) { requireWorld(); if (!Double.isFinite(speed) || speed < 0) throw new IllegalArgumentException("Invalid speed"); cn.omix.util.player.MovementUtil.strafe(speed); }
        public void stop() { requireWorld(); cn.omix.util.player.MovementUtil.stop(); }
        public void velocity(Vec3d velocity) { requireWorld(); if (!Double.isFinite(velocity.x + velocity.y + velocity.z)) throw new IllegalArgumentException("Invalid velocity"); mc.player.setVelocity(velocity); }
    }
    public final class Inventory {
        /** Copies stacks so callers cannot accidentally mutate the live inventory through an observation. */
        public List<net.minecraft.item.ItemStack> snapshot() {
            requireWorld(); var inventory = mc.player.getInventory(); var stacks = new ArrayList<net.minecraft.item.ItemStack>();
            for (int slot = 0; slot < inventory.size(); slot++) stacks.add(inventory.getStack(slot).copy()); return List.copyOf(stacks);
        }
        public int find(net.minecraft.item.Item item, boolean hotbarOnly) {
            requireWorld(); var inventory = mc.player.getInventory();
            for (int slot = 0; slot < (hotbarOnly ? 9 : inventory.size()); slot++) if (inventory.getStack(slot).isOf(item)) return slot;
            return -1;
        }
        public void select(int slot) { requireWorld(); if (slot < 0 || slot > 8) throw new IllegalArgumentException("Hotbar slot must be 0–8"); mc.player.getInventory().setSelectedSlot(slot); }
        public void click(int slot, int button, net.minecraft.screen.slot.SlotActionType action) {
            requireWorld(); var handler = mc.player.currentScreenHandler;
            if (slot != -999 && (slot < 0 || slot >= handler.slots.size())) throw new IllegalArgumentException("Slot outside current container");
            mc.interactionManager.clickSlot(handler.syncId, slot, button, action, mc.player);
        }
    }
    private void requireWorld() { requireClientThread(); if (!inWorld() || mc.interactionManager == null) throw new IllegalStateException("Enter a world before using this operation"); }
    public final class Modules {
        public ModuleHandle register(String id, String name, Category category) {
            script.ensurePreparing(); var handle = new ModuleHandle(script, id, name, category); script.add(handle); return handle;
        }
        public Module get(String idOrName) { return client.getModuleManager().find(idOrName); }
        public List<Module> list() { return List.copyOf(client.getModuleManager().getModuleMap().values()); }
    }
    public final class Modes {
        public ModeHandle register(String id, String module, String name) {
            script.ensurePreparing(); Module host = modules.get(module);
            if (host == null) throw new IllegalArgumentException("Unknown host module: " + module);
            var handle = new ModeHandle(script, id, host, name); script.add(handle); return handle;
        }
    }
    public final class Commands {
        public CommandHandle register(String usage, Consumer<String[]> action, String... aliases) {
            script.ensurePreparing(); var handle = new CommandHandle(script, usage, action, aliases); script.add(handle); return handle;
        }
        public void run(String command) { requireClientThread(); client.getCommandManager().executeClientCommand(command); }
        public List<String> complete(String prefix) { return client.getCommandManager().getCompletions(prefix); }
    }
    public final class Events {
        public <E extends Event> Registration on(Class<E> type, Consumer<E> callback) { return on(type, 10, callback); }
        public <E extends Event> Registration on(Class<E> type, int priority, Consumer<E> callback) {
            script.ensurePreparing();
            final var faulted = new java.util.concurrent.atomic.AtomicBoolean();
            return script.own(client.getEventManager().subscribe(this, type, priority, event -> {
                if (!script.active() || faulted.get()) return;
                try { callback.accept(event); }
                catch (Throwable error) { cn.omix.util.script.ScriptFailures.rethrowFatal(error); faulted.set(true); script.error(type.getSimpleName(), error); }
            }));
        }
        /** Capture an immutable value on the emitting thread, then observe it on the client thread. No deferred cancellation. */
        public <E extends Event, T> Registration observe(Class<E> type, Function<E, T> snapshot, Consumer<T> observer) {
            return on(type, event -> { T value = snapshot.apply(event); tasks.client(() -> observer.accept(value)); });
        }
    }
    public final class Tasks {
        public void client(Runnable action) {
            script.requireActive(); Object world = mc.world;
            mc.execute(() -> { if (script.active() && world == mc.world) script.invoke("task", action); });
        }
        public Registration afterTicks(int ticks, Runnable action) {
            script.requireActive(); if (ticks < 1) throw new IllegalArgumentException("ticks must be positive");
            int[] remaining = {ticks}; Object world = mc.world; Registration[] handle = {null};
            handle[0] = script.own(client.getEventManager().subscribe(this, TickEvent.class, 10, event -> {
                if (!script.active() || world != mc.world) { handle[0].close(); script.release(handle[0]); return; }
                if (--remaining[0] == 0) { handle[0].close(); script.release(handle[0]); script.invoke("afterTicks", action); }
            })); return handle[0];
        }
        public <T> CompletableFuture<T> async(Callable<T> action) {
            script.requireActive(); Object world = mc.world; Object player = mc.player; var future = new CompletableFuture<T>();
            Thread thread = Thread.ofVirtual().name("Omix-Script-" + script.id()).unstarted(() -> {
                if (!script.active() || future.isCancelled()) { future.cancel(false); return; }
                try { T value = action.call(); if (script.active() && mc.world == world && mc.player == player) future.complete(value); else future.cancel(false); }
                catch (Throwable error) { ScriptFailures.rethrowFatal(error); future.completeExceptionally(error); if (script.active()) script.error("async", error); }
            });
            Registration owned = script.own((Registration) () -> { future.cancel(false); thread.interrupt(); });
            future.whenComplete((value, error) -> script.release(owned)); thread.start(); return future;
        }
    }
    public final class Packets {
        public void send(Packet<?> packet) { requireClientThread(); PacketUtil.sendPacket(packet); }
        public void sendWithoutEvents(Packet<?> packet) { requireClientThread(); PacketUtil.runWithoutEvents(() -> { PacketUtil.sendPacket(packet); return null; }); }
        public Registration blink(FeatureHandle owner) { requireOwner(owner); var core = client.getPacketManager().getBlink(); Object token = new Object(); core.start(token); return owner.own((Registration) () -> core.dispatch(token)); }
        public Registration delay(FeatureHandle owner) { requireOwner(owner); var core = client.getPacketManager().getDelay(); Object token = new Object(); core.start(token); return owner.own((Registration) () -> core.dispatch(token)); }
    }
    private void requireOwner(FeatureHandle owner) { requireClientThread(); if (owner.context != script || !owner.active()) throw new IllegalStateException("Resource owner is not an active feature of this generation"); }
    public Registration timer(FeatureHandle owner, float speed) {
        requireOwner(owner); if (!Float.isFinite(speed) || speed <= 0) throw new IllegalArgumentException("Invalid speed");
        Object token = new Object(); TimerSpeedUtil.setTemporaryOverride(token, speed);
        return owner.own((Registration) () -> TimerSpeedUtil.clearTemporaryOverride(token));
    }
    public void rotate(RotationRequestEvent event, RotationRequest request) { requireClientThread(); event.submit(request); }
    public final class Render {
        public void text(DrawContext context, String text, int x, int y, int argb) { requireClientThread(); ScriptRenderPhase.require2D(context); context.drawText(mc.textRenderer, text, x, y, argb, true); }
        public void rect(DrawContext context, int x, int y, int width, int height, int argb) { requireClientThread(); ScriptRenderPhase.require2D(context); context.fill(x, y, x + width, y + height, argb); }
        public void box(Render3DEvent event, Box box, java.awt.Color color, boolean fill, boolean outline) { requireClientThread(); ScriptRenderPhase.require3D(event); cn.omix.util.render.Render3D.drawBox(event, box, color, fill, outline); }
    }
    public final class Ui {
        public HudHandle hud(String id, String name, BiConsumer<DrawContext, HudHandle> draw) {
            script.ensurePreparing(); var handle = new HudHandle(script, id, name, draw); script.add(handle); return handle;
        }
        public WebPageHandle page(String id, String html, Function<JsonElement, JsonElement> messages) {
            script.ensurePreparing(); return new WebPageHandle(script, id, html, messages);
        }
        public void screen(Screen screen) {
            requireClientThread(); mc.setScreen(screen);
            script.own((Registration) () -> { if (mc.currentScreen == screen) mc.setScreen(null); });
        }
        public void notify(String message) { requireClientThread(); cn.omix.util.Util.log(message); }
        public void open(String route) { requireClientThread(); im.webui.WebUiRuntime.getInstance().openScreen(route); }
    }
    public final class Storage {
        private Path file(String key) {
            if (!key.matches("[A-Za-z0-9_-]{1,80}")) throw new IllegalArgumentException("Invalid data key");
            Path path = script.dataDirectory().resolve(key + ".json");
            if (Files.isSymbolicLink(path)) throw new IllegalArgumentException("Symbolic data link"); return path;
        }
        public JsonElement read(String key) throws java.io.IOException { Path path = file(key); return Files.exists(path) ? JsonParser.parseString(Files.readString(path)) : JsonNull.INSTANCE; }
        public void write(String key, JsonElement value) throws java.io.IOException { script.requireActive(); ScriptFiles.atomicWrite(file(key), value.toString()); }
    }
    public final class Managers {
        public cn.omix.management.RotationManager rotation() { return client.getRotationManager(); }
        public cn.omix.management.PacketManager packets() { return client.getPacketManager(); }
        public cn.omix.management.TargetManager targets() { return client.getTargetManager(); }
        public cn.omix.management.FriendManager friends() { return client.getFriendManager(); }
        public cn.omix.config.ConfigManager config() { return client.getConfigManager(); }
        public cn.omix.command.CommandManager commands() { return client.getCommandManager(); }
        public cn.omix.ui.font.FontManager fonts() { return client.getFontManager(); }
    }
    public final class NativeAccess {
        public String method(String owner, String name, String descriptor) { return ScriptMappings.current().methodName(owner, name, descriptor); }
        public String field(String owner, String name, String descriptor) { return ScriptMappings.current().fieldName(owner, name, descriptor); }
        public String descriptor(String descriptor) { return ScriptMappings.current().descriptor(descriptor); }
        public Class<?> type(String yarnName) throws ClassNotFoundException {
            return Class.forName(ScriptMappings.current().className(yarnName), false, ScriptApi.class.getClassLoader());
        }
    }
}
