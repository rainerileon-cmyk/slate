package dev.fallingcloud.slate.building.mixin.variant;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.fallingcloud.slate.building.block.ShapeBehaviour;
import dev.fallingcloud.slate.building.variant.ShapeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Footsteps on a shape block sound like its material. NeoForge already asks the block per position (the shape
 * blocks implement its {@code getSoundType(state, level, pos, entity)}), so the vanilla call wrapped here only exists
 * on Fabric: {@code require = 0}, a no-op on NeoForge.
 */
@Mixin(Entity.class)
public abstract class EntityStepSoundMixin {

    @Shadow
    public abstract Level level();

    @WrapOperation(method = "playStepSound",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"),
        require = 0)
    private SoundType slateBuilding$materialStepSound(final BlockState state, final Operation<SoundType> original,
                                                      @Local(argsOnly = true) final BlockPos pos) {
        return state.getBlock() instanceof ShapeBlock ? ShapeBehaviour.sound(state, level(), pos) : original.call(state);
    }
}
