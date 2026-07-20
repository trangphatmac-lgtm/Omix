package cn.remix.event.impl;

import cn.remix.event.base.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.util.math.Vec3d;

@Getter
@AllArgsConstructor
public final class PlayerPositionLookEvent extends Event {
    private final Vec3d position;
}
