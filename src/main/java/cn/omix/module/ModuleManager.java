package cn.omix.module;

import cn.omix.Client;
import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.KeyInputEvent;
import cn.omix.module.impl.combat.AntiAim;
import cn.omix.module.impl.combat.Aura;
import cn.omix.module.impl.combat.ProjectileAura;
import cn.omix.module.impl.combat.Reach;
import cn.omix.module.impl.combat.AutoTotem;
import cn.omix.module.impl.combat.Offhand;
import cn.omix.module.impl.combat.AutoWeapon;
import cn.omix.module.impl.combat.Backtrack;
import cn.omix.module.impl.combat.Criticals;
import cn.omix.module.impl.combat.CrossbowExploit;
import cn.omix.module.impl.combat.FastBow;
import cn.omix.module.impl.combat.FastEat;
import cn.omix.module.impl.combat.MaceDamageBooster;
import cn.omix.module.impl.combat.TargetStrafe;
import cn.omix.module.impl.combat.TPAura;
import cn.omix.module.impl.combat.Velocity;
import cn.omix.module.impl.exploits.Blink;
import cn.omix.module.impl.exploits.BrandSpoofer;
import cn.omix.module.impl.exploits.ChannelHider;
import cn.omix.module.impl.exploits.Disabler;
import cn.omix.module.impl.exploits.NoBan;
import cn.omix.module.impl.exploits.PathFinder;
import cn.omix.module.impl.exploits.PacketsLogger;
import cn.omix.module.impl.exploits.Regen;
import cn.omix.module.impl.exploits.ResourcepackSpoof;
import cn.omix.module.impl.move.*;
import cn.omix.module.impl.move.Timer;
import cn.omix.module.impl.player.*;
import cn.omix.module.impl.render.*;
import cn.omix.module.impl.world.*;
import cn.omix.module.impl.world.AutoPlay;
import cn.omix.module.value.Value;
import cn.omix.util.IMinecraft;
import lombok.Getter;

import java.lang.reflect.Field;
import java.util.*;

@Getter
public class ModuleManager implements IMinecraft {
    private final Map<String, Module> moduleMap = new java.util.concurrent.ConcurrentSkipListMap<>();

    public ModuleManager() {
        instance.getEventManager().register(this);

        addModules(
                new HUD(),
                new ClickGui(),
                new AIScreen(),
                new Scripts(),
                new MusicPlayer(),
                new ScaffoldX(),
                new Scaffold(),
                new AutoPlay(),
                new AutoGG(),
                new AutoL(),
                new AutoScreenshot(),
                new QuickMacro(),
                new WorldTweaks(),
                new GhostHand(),
                new AntiBot(),
                new AntiAim(),
                new Aura(),
                new ProjectileAura(),
                new Reach(),
                new TPAura(),
                new AutoTotem(),
                new Offhand(),
                new AutoWeapon(),
                new Backtrack(),
                new CrossbowExploit(),
                new FastBow(),
                new FastEat(),
                new MaceDamageBooster(),
                new Targets(),
                new Teams(),
                new BrandSpoofer(),
                new ChannelHider(),
                new Disabler(),
                new NoBan(),
                new PathFinder(),
                new PacketsLogger(),
                new MCF(),
                new GuiMove(),
                new Jesus(),
                new FastWeb(),
                new ResourcepackSpoof(),
                new TargetStrafe(),
                new DamageTint(),
                new NickHider(),
                new Criticals(),
                new NoJumpDelay(),
                new NoSlowDown(),
                new Parkour(),
                new Derp(),
                new ModuleList(),
                new Speed(),
                new LongJump(),
                new Timer(),
                new Spider(),
                new Step(),
                new Strafe(),
                new Fly(),
                new AntiVoid(),
                new NoFall(),
                new Velocity(),
                new ChestArua(),
                new ChestStealer(),
                new InventoryManager(),
                new AutoTool(),
                new AutoBlockIn(),
                new AutoArmor(),
                new AntiHunger(),
                new AntiLava(),
                new Stuck(),
                new Phase(),
                new LightningTracker(),
                new Freecam(),
                new LookTP(),
                new Regen(),
                new AntiDebuff(),
                new Brightness(),
                new Blink(),
                new Chams(),
                new NoFog(),
                new NoHurtCam(),
                new Zoom(),
                new ViewClip(),
                new ItemPhysics(),
                new Notify(),
                new KeepSprint(),
                new Animation(),
                new ESP(),
                new NameTags(),
                new Waypoint(),
                new Maps(),
                new BedESP(),
                new ChestESP(),
                new Xray(),
                new TargetHUD(),
                new Tracers(),
                new Trajectories(),
                new MoreParticles(),
                new KillEffect(),
                new Sprint()
        );

        sortModules();
    }

    public void addModules(Module... modulesArray) {
        for (Module module : modulesArray) {
            reflectModuleValues(module);
            moduleMap.put(module.getId(), module);
        }
    }

    private void reflectModuleValues(Module module) {
        try {
            Class<?> clazz = module.getClass();
            while (clazz != null && clazz != Object.class) {
                for (Field field : clazz.getDeclaredFields()) {
                    if (Value.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        Object valueObject = field.get(module);
                        if (valueObject != null) {
                            if (!module.getValues().contains(valueObject)) module.getValues().add((Value) valueObject);
                        }
                    }
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Exception e) {
            Client.logger.debug(e.getMessage());
        }
    }

    private void sortModules() {
        List<Module> moduleList = new ArrayList<>(moduleMap.values());
        moduleList.sort(Comparator.comparing(Module::getName));
        moduleMap.clear();
        for (Module module : moduleList) {
            moduleMap.put(module.getId(), module);
        }
    }

    public <T extends Module> T getModule(Class<T> clazz) {
        return clazz.cast(moduleMap.get(clazz.getSimpleName()));
    }

    public Module find(String idOrName) {
        return moduleMap.values().stream().filter(module -> module.getId().equalsIgnoreCase(idOrName)
                || module.getName().equalsIgnoreCase(idOrName)).findFirst().orElse(null);
    }

    public void validateRegistration(Module module, java.util.Set<Module> replacing) {
        if (moduleMap.values().stream().anyMatch(old -> !replacing.contains(old)
                && (old.getId().equalsIgnoreCase(module.getId()) || cn.omix.util.script.ScriptFailures.commandName(old.getName()).equals(cn.omix.util.script.ScriptFailures.commandName(module.getName())))))
            throw new IllegalArgumentException("Module id or name already exists: " + module.getName());
    }

    public cn.omix.script.api.Registration register(Module module) {
        validateRegistration(module, java.util.Set.of());
        reflectModuleValues(module);
        moduleMap.put(module.getId(), module);
        return () -> { if (moduleMap.remove(module.getId(), module)) module.setEnabled(false); };
    }

    @EventTarget
    private void onKeyInput(KeyInputEvent event) {
        if (event.getKey() <= 0) return;

        for (Module module : moduleMap.values()) {
            if (module.getKey() == event.getKey()) {
                if (module.isHoldToUse()) {
                    if (event.getAction() == 0 || mc.currentScreen == null) {
                        module.setEnabled(event.getAction() == 1);
                    }
                } else if (event.getAction() == 1 && mc.currentScreen == null) {
                    module.toggle();
                }
            }
        }
    }
}
