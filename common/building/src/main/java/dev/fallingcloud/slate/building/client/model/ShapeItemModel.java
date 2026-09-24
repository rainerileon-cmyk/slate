package dev.fallingcloud.slate.building.client.model;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The baked item model of one shape item in one material: the shape's display state cut from the material's model
 * by {@link ShapeQuadBaker}, with the display transforms of the shape's item JSON. Plain vanilla model (both
 * loaders render it through the vanilla item path); built once per (shape, material) by {@link ShapeItemModels}.
 */
public final class ShapeItemModel implements BakedModel {

    private final List<BakedQuad>[] quads;
    private final BakedModel display;
    private final BakedModel source;
    private final boolean translucent;

    @SuppressWarnings("unchecked")
    ShapeItemModel(final BlockState shape, final Object materialKey, final @Nullable BlockState material, final BakedModel display,
                   final BakedModel source) {
        this.display = display;
        this.source = source;
        this.translucent = material != null && ShapeModels.translucent(material);
        final BlockState sourceState = material != null ? material : shape;
        this.quads = new List[7];
        for (int i = 0; i < 7; i++) {
            final Direction side = i < 6 ? Direction.values()[i] : null;
            this.quads[i] = ShapeQuadBaker.quads(shape, side, materialKey, null,
                d -> source.getQuads(material != null ? sourceState : null, d, RandomSource.create(42L)));
        }
    }

    @Override
    public List<BakedQuad> getQuads(final @Nullable BlockState state, final @Nullable Direction side, final RandomSource random) {
        return quads[side == null ? 6 : side.ordinal()];
    }

    @Override
    public boolean useAmbientOcclusion() {
        return source.useAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return true;
    }

    @Override
    public boolean usesBlockLight() {
        return true;
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return source.getParticleIcon();
    }

    @Override
    public ItemTransforms getTransforms() {
        return display.getTransforms();
    }

    @Override
    public ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }

    /**
     * NeoForge {@code IBakedModelExtension.getRenderTypes(ItemStack, boolean)} (declared without {@code @Override}:
     * a plain method on Fabric, where the shape blocks' translucent render layer covers items instead). Translucent
     * materials (stained glass stairs) render with the translucent item sheet, everything else cut out, the same
     * choice NeoForge makes for a block item of that material.
     */
    public List<RenderType> getRenderTypes(final ItemStack stack, final boolean cull) {
        if (!translucent) return List.of(Sheets.cutoutBlockSheet());
        return List.of(cull || !Minecraft.useShaderTransparency() ? Sheets.translucentCullBlockSheet() : Sheets.translucentItemSheet());
    }
}
