package cn.omix.event.impl;

import cn.omix.event.base.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.entity.Entity;

@Getter
@Setter
@AllArgsConstructor
public class RenderRotationEvent extends Event {
   public static Entity currentEntity;
   private float[] rotation;
   private float[] lastRotation;
}
