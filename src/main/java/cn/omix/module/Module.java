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
    private String suffix = "";
    private boolean enabled;
    private boolean hidden;
    private int key = -1;

    public Module(String name, Category category) {
        this.name = name;
        this.category = category;
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
        instance.getEventManager().register(this);

        try {
            onEnable();
        } catch (Exception e) {
            Client.logger.debug(e.getMessage());
        }
    }

    protected void disable() {
        instance.getEventManager().unregister(this);

        try {
            onDisable();
        } catch (Exception e) {
            Client.logger.debug(e.getMessage());
        }
    }

    public void onEnable() {}
    public void onDisable() {}

    public boolean isHoldToUse() {
        return false;
    }
}
