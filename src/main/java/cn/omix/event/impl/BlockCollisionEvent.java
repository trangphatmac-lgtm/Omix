package cn.omix.event.impl;

import cn.omix.event.base.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;

@Getter
@Setter
@AllArgsConstructor
public final class BlockCollisionEvent extends Event {
    private final BlockState state;
    private final BlockPos pos;
    private VoxelShape shape;
}
