package dev.fallingcloud.slate.core.stage.mesh;

import dev.fallingcloud.slate.core.Slate;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.jetbrains.annotations.Nullable;

/**
 * Loads and bakes JSON block/item models straight from resources at runtime, independent of the model manager: any
 * {@code assets/<ns>/models/<path>.json} (a Blockbench "Java block/item" export, with parents and {@code #texture}
 * references resolved) becomes a {@code BakedModel} whose textures come from the block atlas. That atlas holds
 * every {@code textures/block/**} and {@code textures/item/**} file of every namespace, so a model only has to keep
 * its textures there. {@code builtin/*} parents (generated item sprites) are not supported. Baked models are cached
 * until the next resource reload.
 */
public final class StageModels {

    private static final Map<ResourceLocation, BakedModel> CACHE = new HashMap<>();
    private static final Map<ResourceLocation, BlockModel> UNBAKED = new HashMap<>();
    private static final FaceBakery FACE_BAKERY = new FaceBakery();
    private static final BlockModel EMPTY = new BlockModel(null, List.of(), Map.of(), Boolean.FALSE, BlockModel.GuiLight.FRONT,
        net.minecraft.client.renderer.block.model.ItemTransforms.NO_TRANSFORMS, List.of());

    /** A baked model for {@code assets/<ns>/models/<path>.json}, or null when it cannot be loaded (logged once). */
    @Nullable
    public static synchronized BakedModel get(final ResourceLocation model) {
        if (CACHE.containsKey(model)) return CACHE.get(model);
        BakedModel baked = null;
        try {
            baked = bake(model);
        } catch (final Exception e) {
            Slate.LOGGER.warn("[Slate] stage: cannot bake model {}: {}", model, e.toString());
        }
        CACHE.put(model, baked);
        return baked;
    }

    public static synchronized void clear() {
        CACHE.clear();
        UNBAKED.clear();
    }

    @Nullable
    private static BakedModel bake(final ResourceLocation id) {
        final BlockModel model = unbaked(id);
        if (model == null) return null;
        model.resolveParents(StageModels::parent);
        final Function<Material, TextureAtlasSprite> sprites = m -> Minecraft.getInstance().getTextureAtlas(m.atlasLocation()).apply(m.texture());
        final List<BakedQuad> unculled = new ArrayList<>();
        final Map<Direction, List<BakedQuad>> culled = new EnumMap<>(Direction.class);
        for (final Direction d : Direction.values()) culled.put(d, new ArrayList<>());
        for (final BlockElement element : model.getElements()) {
            for (final Map.Entry<Direction, BlockElementFace> e : element.faces.entrySet()) {
                final BlockElementFace face = e.getValue();
                final TextureAtlasSprite sprite = sprites.apply(model.getMaterial(face.texture()));
                final BakedQuad quad = FACE_BAKERY.bakeQuad(element.from, element.to, face, sprite, e.getKey(), BlockModelRotation.X0_Y0, element.rotation, element.shade);
                if (face.cullForDirection() == null) unculled.add(quad);
                else culled.get(face.cullForDirection()).add(quad);
            }
        }
        final TextureAtlasSprite particle = sprites.apply(model.getMaterial("particle"));
        return new SimpleBakedModel(unculled, culled, model.hasAmbientOcclusion(), model.getGuiLight().lightLikeBlock(), true,
            particle, model.getTransforms(), ItemOverrides.EMPTY);
    }

    private static UnbakedModel parent(final ResourceLocation id) {
        if (id.getPath().startsWith("builtin/")) return EMPTY;
        final BlockModel m = unbaked(id);
        return m == null ? EMPTY : m;
    }

    @Nullable
    private static BlockModel unbaked(final ResourceLocation id) {
        if (UNBAKED.containsKey(id)) return UNBAKED.get(id);
        BlockModel model = null;
        final ResourceLocation file = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "models/" + id.getPath() + ".json");
        try {
            final Resource res = Minecraft.getInstance().getResourceManager().getResource(file).orElse(null);
            if (res != null) {
                try (BufferedReader reader = res.openAsReader()) {
                    model = BlockModel.fromStream(reader);
                    model.name = id.toString();
                }
            } else {
                Slate.LOGGER.warn("[Slate] stage: model {} not found", file);
            }
        } catch (final Exception e) {
            Slate.LOGGER.warn("[Slate] stage: cannot read model {}: {}", file, e.toString());
        }
        UNBAKED.put(id, model);
        return model;
    }

    private StageModels() {}
}
