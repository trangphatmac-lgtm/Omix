package cn.remix.module;

import cn.remix.Client;
import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.KeyInputEvent;
import cn.remix.module.impl.combat.Aura;
import cn.remix.module.impl.combat.Criticals;
import cn.remix.module.impl.combat.TargetStrafe;
import cn.remix.module.impl.combat.Velocity;
import cn.remix.module.impl.exploits.Disabler;
import cn.remix.module.impl.exploits.Regen;
import cn.remix.module.impl.exploits.ResourcepackSpoof;
import cn.remix.module.impl.move.*;
import cn.remix.module.impl.player.*;
import cn.remix.module.impl.render.*;
import cn.remix.module.impl.world.Scaffold;
import cn.remix.module.impl.world.ScaffoldOld;
import cn.remix.module.impl.world.WorldTweaks;
import cn.remix.module.value.Value;
import cn.remix.util.IMinecraft;
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
                new Scaffold(),
                new ScaffoldOld(),
                new WorldTweaks(),
                new AntiBot(),
                new Aura(),
                new Targets(),
                new Teams(),
                new Disabler(),
                new MCF(),
                new GuiMove(),
                new FastWeb(),
                new ResourcepackSpoof(),
                new TargetStrafe(),
                new DamageTint(),
                new Criticals(),
                new NoJumpDelay(),
                new NoSlowDown(),
                new ModuleList(),
                new Speed(),
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
