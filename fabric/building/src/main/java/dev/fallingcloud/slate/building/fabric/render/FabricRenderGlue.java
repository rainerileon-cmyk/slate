package dev.fallingcloud.slate.building.fabric.render;

import dev.fallingcloud.slate.building.client.model.ShapeModels;
import dev.fallingcloud.slate.building.client.render.BuildingRender;
import dev.fallingcloud.slate.building.client.render.GhostShader;
import dev.fallingcloud.slate.building.registry.BuildingBlocks;
import dev.fallingcloud.slate.building.registry.RegistryRef;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

/**
 * Fabric glue for rendering (design §6): the shape-block and shape-item model wrappers
 * ({@code ModelLoadingPlugin.modifyModelAfterBake}, which also resets the caches on every model reload), block and
 * item colour providers delegating to the material ({@code ColorProviderRegistry}), the ghost core shader
 * ({@code CoreShaderRegistrationCallback}) and the translucent render layer for the shape blocks.
 *
 * <p>Why translucent: shape ITEMS take the vanilla item path, which picks the item sheet from the block's render
 * layer; translucent draws opaque, cutout and translucent materials all correctly. The world model sets the
 * material's own blend mode on every quad, so the layer never decides how shapes mesh in the world.
 *
 * <p>Owner: B (render).
 */
public final class FabricRenderGlue {

    /** Both environments, from {@code SlateBuildingFabric.onInitialize}. Rendering has no common-side wiring. */
    public static void init() {
    }

    /** Client only, from {@code SlateBuildingFabricClient.onInitializeClient}. */
    public static void initClient() {
        Client.init();
    }

    /** Client-only references live here so {@link #init} stays loadable on a dedicated server. */
    private static final class Client {

        static void init() {
            ShapeModels.install(state -> ItemBlockRenderTypes.getChunkRenderType(state) == RenderType.translucent(), (stack, tint) -> {
                final ItemColor colour = ColorProviderRegistry.ITEM.get(stack.getItem());
                return colour == null ? -1 : colour.getColor(stack, tint);
            });
            final Block[] blocks = BuildingBlocks.resolved();
            ColorProviderRegistry.BLOCK.register(ShapeModels::blockColor, blocks);
            ColorProviderRegistry.ITEM.register(ShapeModels::itemColor, blocks);
            BlockRenderLayerMap.INSTANCE.putBlocks(RenderType.translucent(), blocks);
            CoreShaderRegistrationCallback.EVENT.register(ctx -> ctx.register(GhostShader.ID, GhostShader.FORMAT, GhostShader::set));
            ModelLoadingPlugin.register(ctx -> {
                // Runs at the start of every model reload: everything cropped from the old atlas is stale.
                BuildingRender.onResourcesReloaded();
                FabricShapeBlockModel.reset();
                final Set<ResourceLocation> ids = new HashSet<>();
                for (final RegistryRef<? extends Block> ref : BuildingBlocks.all()) ids.add(ref.id());
                ctx.modifyModelAfterBake().register(ModelModifier.WRAP_PHASE, (model, context) -> wrap(model, context.topLevelId(), ids));
            });
        }

        private static BakedModel wrap(final BakedModel model, final ModelResourceLocation id, final Set<ResourceLocation> shapes) {
            if (model == null || id == null || !shapes.contains(id.id())) return model;
            return "inventory".equals(id.variant()) ? new FabricShapeItemModel(model) : new FabricShapeBlockModel(model);
        }

        private Client() {}
    }

    private FabricRenderGlue() {}
}
