package dev.fallingcloud.slate.building.mixin.variant;

import dev.fallingcloud.slate.building.block.ShapeFenceBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every fence connects to a Slate Building fence. Vanilla only joins fences of the same "wooden-ness" (a tag), but
 * a shape fence's material is per position, so it joins both kinds (and joins them itself, see ShapeFenceBlock).
 */
@Mixin(FenceBlock.class)
public abstract class FenceBlockMixin {

    @Inject(method = "connectsTo", at = @At("HEAD"), cancellable = true)
    private void slateBuilding$connectToShapeFences(final BlockState neighbour, final boolean sturdy, final Direction direction,
                                                   final CallbackInfoReturnable<Boolean> cir) {
        if (neighbour.getBlock() instanceof ShapeFenceBlock) cir.setReturnValue(true);
    }
}
