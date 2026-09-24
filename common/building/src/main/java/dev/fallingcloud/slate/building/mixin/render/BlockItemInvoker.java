package dev.fallingcloud.slate.building.mixin.render;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * The placement preview asks a held block item exactly what it would place, through the item's own (protected)
 * placement logic, so subclasses (wall torches, signs, scaffolding, DiagonalBlocks' re-pointed items) preview right.
 * Both methods have the same signature in the NeoForge and Fabric jars.
 */
@Mixin(BlockItem.class)
public interface BlockItemInvoker {

    /** {@code BlockItem.getPlacementState}: the state it would place, or null when it cannot be placed there. */
    @Invoker("getPlacementState")
    @Nullable BlockState slateBuilding$getPlacementState(BlockPlaceContext context);

    /** {@code BlockItem.canPlace}: survival + no entity in the way. */
    @Invoker("canPlace")
    boolean slateBuilding$canPlace(BlockPlaceContext context, BlockState state);
}
