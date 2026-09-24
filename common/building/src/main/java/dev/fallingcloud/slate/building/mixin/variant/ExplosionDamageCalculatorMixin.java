package dev.fallingcloud.slate.building.mixin.variant;

import dev.fallingcloud.slate.building.variant.ShapeBlock;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shape blocks resist explosions like their material. NeoForge already asks the block per position (the shape
 * blocks implement its extension method); vanilla/Fabric only know one value per block, so the calculator is
 * corrected here on both loaders (same result on NeoForge).
 */
@Mixin(ExplosionDamageCalculator.class)
public abstract class ExplosionDamageCalculatorMixin {

    @Inject(method = "getBlockExplosionResistance", at = @At("RETURN"), cancellable = true)
    private void slateBuilding$materialResistance(final Explosion explosion, final BlockGetter level, final BlockPos pos, final BlockState state,
                                                  final FluidState fluid, final CallbackInfoReturnable<Optional<Float>> cir) {
        if (!(state.getBlock() instanceof ShapeBlock)) return;
        final BlockState material = ShapeBlock.material(level, pos);
        if (material == null) return;
        cir.setReturnValue(Optional.of(Math.max(material.getBlock().getExplosionResistance(), fluid.getExplosionResistance())));
    }
}
