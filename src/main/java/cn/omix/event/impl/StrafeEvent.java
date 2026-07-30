package cn.omix.event.impl;

import cn.omix.event.base.Event;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StrafeEvent extends Event {
    private float yaw;
    private float friction;

    public StrafeEvent(float yaw, float friction) {
        this.yaw = yaw;
        this.friction = friction;
    }
}
