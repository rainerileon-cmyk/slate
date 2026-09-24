package dev.fallingcloud.slate.building.mixin.variant;

import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Two {@link BlockBehaviour.Properties} flags that only have a one-way setter ({@code noOcclusion()},
 * {@code dynamicShape()}). Shape blocks turn occlusion back on (decided per state by the material's {@code opaque}
 * property) and clear the dynamic-shape flag: their shapes depend on the state alone, so vanilla may build the
 * per-state shape cache, which also decides solidity for rain, fluids and suffocation.
 */
@Mixin(BlockBehaviour.Properties.class)
public interface BlockPropertiesAccessor {

    @Accessor("canOcclude")
    void slateBuilding$setCanOcclude(boolean canOcclude);

    @Accessor("dynamicShape")
    void slateBuilding$setDynamicShape(boolean dynamicShape);
}
