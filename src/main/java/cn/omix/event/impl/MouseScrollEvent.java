package cn.omix.event.impl;

import cn.omix.event.base.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MouseScrollEvent extends Event {
    private final double horizontal;
    private final double vertical;
}
