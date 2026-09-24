package dev.fallingcloud.slate.building.fabric.render;

import dev.fallingcloud.slate.building.client.model.ShapeModels;
import dev.fallingcloud.slate.building.client.model.ShapeQuadBaker;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The world model of every shape-block state on Fabric (installed over the "no material" placeholder with
 * {@code ModelLoadingPlugin.modifyModelAfterBake}). Not a vanilla adapter: {@link #emitBlockQuads} reads the block
 * entity's material from the render view and emits the material cut to the shape by {@link ShapeQuadBaker}, each
 * quad with the material's blend mode (glass stairs mesh cutout, stained glass translucent, the placeholder solid)
 * and its cull face, so neighbour culling works per face. Ambient occlusion is off for light-emitting materials, as
 * vanilla does for the block itself. Without a material the placeholder cube is cut to the shape.
 */
final class FabricShapeBlockModel extends ForwardingBakedModel {

    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Map<BlendMode, RenderMaterial[]> MATERIALS = new EnumMap<>(BlendMode.class);

    FabricShapeBlockModel(final BakedModel placeholder) {
        this.wrapped = placeholder;
    }

    @Override
    public boolean isVanillaAdapter() {
        return false;
    }

    @Override
    public void emitBlockQuads(final BlockAndTintGetter view, final BlockState state, final BlockPos pos, final Supplier<RandomSource> randomSupplier,
                               final RenderContext context) {
        final BlockState material = ShapeModels.materialAt(view, pos);
        final BlockState sourceState = material != null ? material : state;
        final BakedModel source = material != null ? ShapeModels.modelOf(material) : wrapped;
        final long seed = sourceState.getSeed(pos);
        final BlendMode blend = material != null ? BlendMode.fromRenderLayer(ItemBlockRenderTypes.getChunkRenderType(material)) : BlendMode.SOLID;
        final boolean ao = material == null || (material.getLightEmission() == 0 && source.useAmbientOcclusion());
        final RenderMaterial renderMaterial = material(blend, ao);
        final Object key = material != null ? material : ShapeModels.UNSET;
        final ShapeQuadBaker.QuadSource quads = d -> source.getQuads(sourceState, d, ShapeModels.random(seed));
        final QuadEmitter emitter = context.getEmitter();
        final int hidden = ShapeModels.hiddenSides(view, pos, material);
        for (int bucket = 0; bucket < 7; bucket++) {
            final Direction side = bucket < 6 ? DIRECTIONS[bucket] : null;
            if (side != null && (ShapeModels.hidden(hidden, side) || context.isFaceCulled(side))) continue;
            for (final BakedQuad q : ShapeQuadBaker.quads(state, side, key, blend, quads)) {
                emitter.fromVanilla(q, renderMaterial, side);
                emitter.emit();
            }
        }
    }

    /** One render material per blend mode and AO flag, found lazily from the active renderer (Indigo, Sodium, ...). */
    private static RenderMaterial material(final BlendMode blend, final boolean ao) {
        synchronized (MATERIALS) {
            final RenderMaterial[] pair = MATERIALS.computeIfAbsent(blend, b -> new RenderMaterial[2]);
            final int i = ao ? 1 : 0;
            if (pair[i] == null) {
                final Renderer renderer = RendererAccess.INSTANCE.getRenderer();
                pair[i] = renderer.materialFinder().clear().blendMode(blend).ambientOcclusion(ao ? TriState.DEFAULT : TriState.FALSE).find();
            }
            return pair[i];
        }
    }

    /** Forget found materials (a renderer reload may replace them). */
    static void reset() {
        synchronized (MATERIALS) {
            MATERIALS.clear();
        }
    }
}
