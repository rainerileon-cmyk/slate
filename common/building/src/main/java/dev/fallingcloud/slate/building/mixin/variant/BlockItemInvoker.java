package dev.fallingcloud.slate.building.mixin.variant;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * {@code BlockItem.getPlacementState} (protected): the state an item would place from a context, including its own
 * {@code canPlace} check. Variant placement goes through the native ITEM so re-pointed items (DiagonalBlocks' twins,
 * any mod overriding the method) place exactly what they would for the player.
 */
@Mixin(BlockItem.class)
public interface BlockItemInvoker {

    @Invoker("getPlacementState")
    @Nullable BlockState slateBuilding$getPlacementState(BlockPlaceContext context);
}
