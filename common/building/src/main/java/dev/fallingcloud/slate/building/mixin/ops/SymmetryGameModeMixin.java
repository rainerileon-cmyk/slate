package dev.fallingcloud.slate.building.mixin.ops;

import dev.fallingcloud.slate.building.ops.server.Symmetry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mirror / radial symmetry for breaking: HEAD remembers the block, RETURN (when the break happened) hands it to
 * {@link Symmetry}, which breaks the matching mirrored blocks through this same method (guarded against recursion).
 * {@code destroyBlock(BlockPos)} and the {@code level} / {@code player} fields are identical in both loader jars.
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class SymmetryGameModeMixin {

    @Shadow protected ServerLevel level;
    @Shadow @Final protected ServerPlayer player;

    @Unique private BlockState slateBuilding$broken = Blocks.AIR.defaultBlockState();

    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void slateBuilding$beforeDestroy(final BlockPos pos, final CallbackInfoReturnable<Boolean> cir) {
        this.slateBuilding$broken = this.level.getBlockState(pos);
    }

    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void slateBuilding$afterDestroy(final BlockPos pos, final CallbackInfoReturnable<Boolean> cir) {
        final BlockState broken = this.slateBuilding$broken;
        this.slateBuilding$broken = Blocks.AIR.defaultBlockState();
        if (Boolean.TRUE.equals(cir.getReturnValue())) Symmetry.afterBreak(this.player, this.level, pos, broken);
    }
}
