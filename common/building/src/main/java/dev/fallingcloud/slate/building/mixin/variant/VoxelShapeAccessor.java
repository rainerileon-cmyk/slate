package dev.fallingcloud.slate.building.mixin.variant;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * {@code VoxelShape.getCoords} is protected: {@code DiagonalVoxelShape} (an outline shape that delegates its geometry
 * to a plain shape) needs the delegate's coordinate lists for the joins vanilla performs on shapes.
 */
@Mixin(VoxelShape.class)
public interface VoxelShapeAccessor {

    @Invoker("getCoords")
    DoubleList slate$getCoords(Direction.Axis axis);
}
