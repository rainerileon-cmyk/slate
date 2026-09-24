package dev.fallingcloud.slate.building.mixin.variant;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.fallingcloud.slate.building.block.ShapeBehaviour;
import dev.fallingcloud.slate.building.variant.ShapeBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The "hit" sounds while mining a shape block are the material's. NeoForge asks the block per position already, so
 * the vanilla call wrapped here only exists on Fabric: {@code require = 0}, a no-op on NeoForge.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeHitSoundMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @WrapOperation(method = "continueDestroyBlock",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"),
        require = 0)
    private SoundType slateBuilding$materialHitSound(final BlockState state, final Operation<SoundType> original,
                                                     @Local(argsOnly = true) final BlockPos pos) {
        if (!(state.getBlock() instanceof ShapeBlock) || minecraft.level == null) return original.call(state);
        return ShapeBehaviour.sound(state, minecraft.level, pos);
    }
}
