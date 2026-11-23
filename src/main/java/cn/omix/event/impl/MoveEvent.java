package cn.omix.event.impl;

import cn.omix.event.base.Event;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.entity.MovementType;

@Getter
@Setter
public class MoveEvent extends Event {
    private double x, y, z;
    private final MovementType type;

    public MoveEvent(double x, double y, double z) {
        this(x, y, z, MovementType.SELF);
    }

    public MoveEvent(double x, double y, double z, MovementType type) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.type = type;
    }
}
