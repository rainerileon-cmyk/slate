package dev.fallingcloud.slate.building.mixin.ops;

import dev.fallingcloud.slate.building.ops.server.Symmetry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
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
 * For placing: {@code useItemOn} RETURN replays the placements of the click ({@link Symmetry#flush()}).
 * {@code destroyBlock(BlockPos)}, {@code useItemOn(ServerPlayer, Level, ItemStack, InteractionHand, BlockHitResult)} and
 * the {@code level} / {@code player} fields are identical in both loader jars (javap-checked).
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

    /**
     * The end of a right click on a block: placements it made have had their place event (on NeoForge that runs inside
     * a block-snapshot capture of the whole click), so their mirrored copies can be made now, outside it.
     */
    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void slateBuilding$afterUseItemOn(final ServerPlayer user, final Level world, final ItemStack stack, final InteractionHand hand,
                                             final BlockHitResult hit, final CallbackInfoReturnable<InteractionResult> cir) {
        Symmetry.flush();
    }

    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void slateBuilding$afterDestroy(final BlockPos pos, final CallbackInfoReturnable<Boolean> cir) {
        final BlockState broken = this.slateBuilding$broken;
        this.slateBuilding$broken = Blocks.AIR.defaultBlockState();
        if (Boolean.TRUE.equals(cir.getReturnValue())) Symmetry.afterBreak(this.player, this.level, pos, broken);
    }
}
