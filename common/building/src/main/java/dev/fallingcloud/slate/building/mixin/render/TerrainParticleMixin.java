package dev.fallingcloud.slate.building.mixin.render;

import dev.fallingcloud.slate.building.client.model.ShapeModels;
import dev.fallingcloud.slate.building.variant.ShapeBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Breaking, sprinting and landing particles of a shape block show its material: the model's particle sprite would
 * otherwise be the "no material" placeholder (Fabric has no per-position particle hook; NeoForge's
 * {@code updateSprite} reaches our model data too, this keeps both loaders identical). The tint follows vanilla's
 * rule for the material: grass-block dirt particles stay untinted, everything else takes the material's colour.
 *
 * <p>The position is the one the particle was made for; sprint and landing particles are spawned in the cell above
 * the block they come from, so the cell below is tried when the given one holds no material.
 */
@Mixin(TerrainParticle.class)
public abstract class TerrainParticleMixin extends TextureSheetParticle {

    protected TerrainParticleMixin(final ClientLevel level, final double x, final double y, final double z) {
        super(level, x, y, z);
    }

    @Inject(method = "<init>(Lnet/minecraft/client/multiplayer/ClientLevel;DDDDDDLnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)V",
        at = @At("TAIL"))
    private void slateBuilding$material(final ClientLevel level, final double x, final double y, final double z, final double dx, final double dy,
                                        final double dz, final BlockState state, final BlockPos pos, final CallbackInfo ci) {
        if (!(state.getBlock() instanceof ShapeBlock)) return;
        BlockState material = ShapeModels.materialAt(level, pos);
        if (material == null) material = ShapeModels.materialAt(level, pos.below());
        if (material == null) return;
        this.setSprite(ShapeModels.modelOf(material).getParticleIcon());
        this.rCol = 0.6F;
        this.gCol = 0.6F;
        this.bCol = 0.6F;
        if (!material.is(Blocks.GRASS_BLOCK)) {
            final int colour = Minecraft.getInstance().getBlockColors().getColor(material, level, pos, 0);
            if (colour != -1) {
                this.rCol *= ((colour >> 16) & 0xFF) / 255F;
                this.gCol *= ((colour >> 8) & 0xFF) / 255F;
                this.bCol *= (colour & 0xFF) / 255F;
            }
        }
    }
}
