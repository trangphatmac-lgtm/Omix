package cn.omix.management;

import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.UpdateEvent;
import cn.omix.util.IMinecraft;
import lombok.Getter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

@Getter
public class TargetManager implements IMinecraft {
    private final List<LivingEntity> targets = new ArrayList<>();

    public TargetManager() {
        instance.getEventManager().register(this);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.world == null) return;

        targets.clear();
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof LivingEntity livingEntity) {
                targets.add(livingEntity);
            }
        }
    }
}