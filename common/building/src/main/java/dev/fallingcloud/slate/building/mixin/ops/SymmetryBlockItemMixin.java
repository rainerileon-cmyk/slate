package dev.fallingcloud.slate.building.mixin.ops;

import dev.fallingcloud.slate.building.ops.server.Symmetry;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mirror / radial symmetry for normal placing: HEAD remembers what was at the target, RETURN hands a successful
 * server-side placement to {@link Symmetry}, which replays it at the mirrored positions. {@code place} has the same
 * signature in both loader jars (javap-checked); client-side calls return immediately.
 */
@Mixin(BlockItem.class)
public abstract class SymmetryBlockItemMixin {

    @Inject(method = "place", at = @At("HEAD"))
    private void slateBuilding$beforePlace(final BlockPlaceContext ctx, final CallbackInfoReturnable<InteractionResult> cir) {
        Symmetry.beforePlace(ctx);
    }

    @Inject(method = "place", at = @At("RETURN"))
    private void slateBuilding$afterPlace(final BlockPlaceContext ctx, final CallbackInfoReturnable<InteractionResult> cir) {
        Symmetry.afterPlace(ctx, cir.getReturnValue());
    }
}
