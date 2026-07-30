package cn.omix.event.impl;

import cn.omix.event.base.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class SlowEvent extends Event {
    private float sideways;
    private float forward;
}
