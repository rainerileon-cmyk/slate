package dev.fallingcloud.slate.building.mixin.variant;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.fallingcloud.slate.building.block.ShapeBehaviour;
import dev.fallingcloud.slate.building.variant.ShapeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Walking on an ice (or slime) shape slides like the material. NeoForge asks the block per position (the shape blocks
 * implement its {@code getFriction(state, level, pos, entity)}), so the vanilla per-block call wrapped here only
 * exists on Fabric: {@code require = 0}, a no-op on NeoForge.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityFrictionMixin extends Entity {

    private LivingEntityFrictionMixin(final EntityType<?> type, final Level level) {
        super(type, level);
    }

    @WrapOperation(method = "travel",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getFriction()F"),
        require = 0)
    private float slateBuilding$materialFriction(final Block block, final Operation<Float> original) {
        if (!(block instanceof ShapeBlock)) return original.call(block);
        final BlockPos pos = getBlockPosBelowThatAffectsMyMovement();
        return ShapeBehaviour.friction(block, level(), pos);
    }
}
