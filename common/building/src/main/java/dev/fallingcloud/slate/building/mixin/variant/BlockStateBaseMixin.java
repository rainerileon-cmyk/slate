package dev.fallingcloud.slate.building.mixin.variant;

import dev.fallingcloud.slate.building.variant.VariantDrops;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Unify hook: a NATIVE variant (oak_stairs, a double stone_slab, a DiagonalFences twin, ...) drops its material
 * instead of itself, one material item per variant item the loot table produced (design §1). Every drop path ends
 * here ({@code Block.getDrops} statics, explosions; NeoForge loot modifiers have already run).
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin {

    @Inject(method = "getDrops", at = @At("RETURN"), cancellable = true)
    private void slateBuilding$unifyNativeDrops(final LootParams.Builder builder, final CallbackInfoReturnable<List<ItemStack>> cir) {
        final List<ItemStack> rewritten = VariantDrops.rewriteNative((BlockState) (Object) this, cir.getReturnValue());
        if (rewritten != null) cir.setReturnValue(rewritten);
    }
}
