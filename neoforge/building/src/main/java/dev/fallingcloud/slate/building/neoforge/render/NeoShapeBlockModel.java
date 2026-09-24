package dev.fallingcloud.slate.building.neoforge.render;

import dev.fallingcloud.slate.building.client.model.ShapeModels;
import dev.fallingcloud.slate.building.client.model.ShapeQuadBaker;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.common.util.TriState;
import org.jetbrains.annotations.Nullable;

/**
 * The world model of every shape-block state on NeoForge (installed over the "no material" placeholder in
 * {@code ModelEvent.ModifyBakingResult}). {@link #getModelData} reads the block entity's material (plus the
 * material model's own model data, so connected-texture style models keep working); {@link #getQuads} returns the
 * material cut to the shape by {@link ShapeQuadBaker} for the render type being meshed; {@link #getRenderTypes} is
 * the material's own chunk render type set (glass stairs mesh as cutout, stained glass as translucent). Particles,
 * ambient occlusion (off for light-emitting materials, like vanilla) follow the material. Without a material the
 * placeholder cube is cut to the shape instead, so an unset block still shows its shape.
 */
final class NeoShapeBlockModel extends BakedModelWrapper<BakedModel> {

    static final ModelProperty<BlockState> MATERIAL = new ModelProperty<>();
    static final ModelProperty<ModelData> MATERIAL_DATA = new ModelProperty<>();
    static final ModelProperty<Long> SEED = new ModelProperty<>();

    NeoShapeBlockModel(final BakedModel placeholder) {
        super(placeholder);
    }

    @Override
    public ModelData getModelData(final BlockAndTintGetter level, final BlockPos pos, final BlockState state, final ModelData data) {
        final BlockState material = ShapeModels.materialAt(level, pos);
        if (material == null) return ModelData.builder().with(SEED, state.getSeed(pos)).build();
        ModelData materialData = ModelData.EMPTY;
        try {
            materialData = ShapeModels.modelOf(material).getModelData(level, pos, material, ModelData.EMPTY);
        } catch (final RuntimeException ignored) {
            // A material model that cannot compute its data away from its own block renders with none.
        }
        return ModelData.builder().with(MATERIAL, material).with(MATERIAL_DATA, materialData).with(SEED, material.getSeed(pos)).build();
    }

    @Override
    public List<BakedQuad> getQuads(final @Nullable BlockState state, final @Nullable Direction side, final RandomSource rand,
                                    final ModelData data, final @Nullable RenderType renderType) {
        if (state == null) return super.getQuads(null, side, rand, data, renderType);
        final Long seedBox = data.get(SEED);
        final long seed = seedBox != null ? seedBox : 42L;
        final BlockState material = data.get(MATERIAL);
        if (material == null) {
            return ShapeQuadBaker.quads(state, side, ShapeModels.UNSET, renderType,
                d -> originalModel.getQuads(state, d, ShapeModels.random(seed), ModelData.EMPTY, renderType));
        }
        final BakedModel model = ShapeModels.modelOf(material);
        final ModelData materialData = data.has(MATERIAL_DATA) ? data.get(MATERIAL_DATA) : ModelData.EMPTY;
        return ShapeQuadBaker.quads(state, side, material, renderType,
            d -> model.getQuads(material, d, ShapeModels.random(seed), materialData == null ? ModelData.EMPTY : materialData, renderType));
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(final BlockState state, final RandomSource rand, final ModelData data) {
        final BlockState material = data.get(MATERIAL);
        if (material == null) return super.getRenderTypes(state, rand, data);
        return ShapeModels.modelOf(material).getRenderTypes(material, rand, materialData(data));
    }

    @Override
    public TextureAtlasSprite getParticleIcon(final ModelData data) {
        final BlockState material = data.get(MATERIAL);
        if (material == null) return super.getParticleIcon(data);
        return ShapeModels.modelOf(material).getParticleIcon(materialData(data));
    }

    @Override
    public TriState useAmbientOcclusion(final BlockState state, final ModelData data, final RenderType renderType) {
        final BlockState material = data.get(MATERIAL);
        if (material == null) return super.useAmbientOcclusion(state, data, renderType);
        if (material.getLightEmission() > 0) return TriState.FALSE;
        return ShapeModels.modelOf(material).useAmbientOcclusion(material, materialData(data), renderType);
    }

    private static ModelData materialData(final ModelData data) {
        final ModelData d = data.get(MATERIAL_DATA);
        return d == null ? ModelData.EMPTY : d;
    }
}
