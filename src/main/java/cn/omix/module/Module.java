package cn.omix.module;

import cn.omix.Client;
import cn.omix.module.impl.render.Notify;
import cn.omix.module.value.Value;
import cn.omix.util.IMinecraft;
import cn.omix.util.Util;
import cn.omix.util.animation.Easing;
import cn.omix.util.animation.EasingAnimation;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Getter
@Setter
public abstract class Module implements IMinecraft {
    private final EasingAnimation animation = new EasingAnimation(Easing.EASE_OUT_QUART, 600);
    private final List<Value> values = new CopyOnWriteArrayList<>();
    private final String name;
    private final Category category;
    private String id;
    private volatile cn.omix.script.api.ModeHandle scriptMode;
    private String suffix = "";
    private volatile boolean enabled;
    private boolean hidden;
    private int key = -1;

    public Module(String name, Category category) {
        this.name = name;
        this.category = category;
        this.id = getClass().getSimpleName();
    }

    public void toggle() {
        setEnabled(!isEnabled());
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            if (enabled) {
                enable();
            } else {
                disable();
            }

            if (this.enabled == enabled
                    && !(this instanceof Notify)
                    && Client.instance != null
                    && Client.instance.getModuleManager() != null
                    && Client.instance.getModuleManager().getModuleMap().containsValue(this)) {
                Util.log(getName() + ": " + (isEnabled() ? "&a&lON" : "&c&lOFF"));
            }
        }
    }

    public <T extends Module> T getModule(Class<T> clazz) {
        return instance.getModuleManager().getModule(clazz);
    }

    protected void enable() {
        if (scriptMode != null) { scriptMode.activate(); return; }
        instance.getEventManager().register(this);

        try {
            onEnable();
        } catch (Exception e) {
            Client.logger.debug(e.getMessage());
        }
    }

    protected void disable() {
        if (scriptMode != null) { scriptMode.deactivate(); return; }
        instance.getEventManager().unregister(this);

        try {
            onDisable();
        } catch (Exception e) {
            Client.logger.debug(e.getMessage());
        }
    }

    public void onEnable() {}
    public void onDisable() {}

    /** UI enabled state is independent from which implementation owns behavior. */
    public boolean isNativeBehaviorActive() { return enabled && scriptMode == null; }

    public void setScriptMode(cn.omix.script.api.ModeHandle next) {
        if (scriptMode == next) return;
        if (enabled) disable();
        scriptMode = next;
        if (enabled) enable();
    }

    public boolean isHoldToUse() {
        return false;
    }
}
