package dev.fallingcloud.slate.building.mixin.variant;

import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The block a stair is made of ({@code protected final BlockState baseState} on both loaders). Discovery rule (2) of
 * design §2: every {@link StairBlock} is a STAIRS variant of its base block.
 */
@Mixin(StairBlock.class)
public interface StairBlockAccessor {

    @Accessor("baseState")
    BlockState slateBuilding$baseState();
}
