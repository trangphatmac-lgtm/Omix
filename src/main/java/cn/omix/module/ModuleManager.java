package cn.omix.module;

import cn.omix.Client;
import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.KeyInputEvent;
import cn.omix.module.impl.combat.Aura;
import cn.omix.module.impl.combat.AutoTotem;
import cn.omix.module.impl.combat.Backtrack;
import cn.omix.module.impl.combat.Criticals;
import cn.omix.module.impl.combat.CrossbowExploit;
import cn.omix.module.impl.combat.FastBow;
import cn.omix.module.impl.combat.FastEat;
import cn.omix.module.impl.combat.MaceDamageBooster;
import cn.omix.module.impl.combat.TargetStrafe;
import cn.omix.module.impl.combat.Velocity;
import cn.omix.module.impl.exploits.BrandSpoofer;
import cn.omix.module.impl.exploits.ChannelHider;
import cn.omix.module.impl.exploits.Disabler;
import cn.omix.module.impl.exploits.PathFinder;
import cn.omix.module.impl.exploits.Regen;
import cn.omix.module.impl.exploits.ResourcepackSpoof;
import cn.omix.module.impl.move.*;
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
    private final Map<String, Module> moduleMap = new LinkedHashMap<>();

    public ModuleManager() {
        instance.getEventManager().register(this);

        addModules(
                new HUD(),
                new ClickGui(),
                new AIScreen(),
                new MusicPlayer(),
                new ScaffoldX(),
                new Scaffold(),
                new AutoPlay(),
                new QuickMacro(),
                new WorldTweaks(),
                new GhostHand(),
                new AntiBot(),
                new Aura(),
                new AutoTotem(),
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
                new PathFinder(),
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
                new Spider(),
                new Step(),
                new Strafe(),
                new Fly(),
                new AntiVoid(),
                new NoFall(),
                new Velocity(),
                new ChestStealer(),
                new InventoryManager(),
                new AutoTool(),
                new AutoArmor(),
                new AntiHunger(),
                new AntiLava(),
                new Stuck(),
                new LightningTracker(),
                new Freecam(),
                new LookTP(),
                new Regen(),
                new AntiDebuff(),
                new Brightness(),
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
            moduleMap.put(module.getClass().getSimpleName(), module);
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
                            module.getValues().add((Value) valueObject);
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
            moduleMap.put(module.getClass().getSimpleName(), module);
        }
    }

    public <T extends Module> T getModule(Class<T> clazz) {
        return clazz.cast(moduleMap.get(clazz.getSimpleName()));
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
