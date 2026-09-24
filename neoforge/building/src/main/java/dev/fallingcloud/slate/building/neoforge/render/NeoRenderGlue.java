package dev.fallingcloud.slate.building.neoforge.render;

import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.client.model.ShapeModels;
import dev.fallingcloud.slate.building.client.render.BuildingRender;
import dev.fallingcloud.slate.building.client.render.GhostShader;
import dev.fallingcloud.slate.building.registry.BuildingBlocks;
import dev.fallingcloud.slate.building.registry.RegistryRef;
import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * NeoForge glue for rendering (design §6): the shape-block and shape-item model wrappers
 * ({@code ModelEvent.ModifyBakingResult}), cache resets after every model bake ({@code BakingCompleted}), block and
 * item colour handlers delegating to the material ({@code RegisterColorHandlersEvent}) and the ghost core shader
 * ({@code RegisterShadersEvent}).
 *
 * <p>Owner: B (render).
 */
public final class NeoRenderGlue {

    /** Both dists, from the mod constructor. Rendering has no common-side wiring. */
    public static void init(final IEventBus modBus) {
    }

    /** Client dist only, from {@code SlateBuildingNeoForgeClient}. */
    public static void initClient(final IEventBus modBus) {
        Client.init(modBus);
    }

    /** Client-only references live here so {@link #init} stays loadable on a dedicated server. */
    private static final class Client {

        static void init(final IEventBus modBus) {
            ShapeModels.install(Client::translucent, (stack, tint) -> Minecraft.getInstance().getItemColors().getColor(stack, tint));
            modBus.addListener(ModelEvent.ModifyBakingResult.class, Client::wrapModels);
            modBus.addListener(ModelEvent.BakingCompleted.class, e -> BuildingRender.onResourcesReloaded());
            modBus.addListener(RegisterColorHandlersEvent.Block.class, e -> e.register(ShapeModels::blockColor, BuildingBlocks.resolved()));
            modBus.addListener(RegisterColorHandlersEvent.Item.class, e -> e.register(ShapeModels::itemColor, BuildingBlocks.resolved()));
            modBus.addListener(RegisterShadersEvent.class, Client::registerShaders);
        }

        /** Every shape-block state gets the material model; every shape item gets the material-resolving overrides. */
        private static void wrapModels(final ModelEvent.ModifyBakingResult event) {
            final Map<ModelResourceLocation, BakedModel> models = event.getModels();
            final Map<BakedModel, BakedModel> wrapped = new IdentityHashMap<>();
            int states = 0;
            for (final RegistryRef<? extends Block> ref : BuildingBlocks.all()) {
                if (!ref.isBound()) continue;
                for (final BlockState state : ref.get().getStateDefinition().getPossibleStates()) {
                    final ModelResourceLocation key = BlockModelShaper.stateToModelLocation(state);
                    final BakedModel model = models.get(key);
                    if (model == null) continue;
                    models.put(key, wrapped.computeIfAbsent(model, NeoShapeBlockModel::new));
                    states++;
                }
                final ModelResourceLocation item = ModelResourceLocation.inventory(ref.id());
                final BakedModel itemModel = models.get(item);
                if (itemModel != null) models.put(item, new NeoShapeItemModel(itemModel));
            }
            SlateBuilding.LOGGER.debug("[Slate Building] shape models installed for {} block states", states);
        }

        private static void registerShaders(final RegisterShadersEvent event) {
            try {
                event.registerShader(new ShaderInstance(event.getResourceProvider(), GhostShader.ID, GhostShader.FORMAT), GhostShader::set);
            } catch (final IOException e) {
                SlateBuilding.LOGGER.error("[Slate Building] could not load the ghost shader; ghosts use the vanilla fallback", e);
            }
        }

        /** NeoForge knows a block's layers from its model (the JSON {@code render_type}), not from the vanilla table. */
        private static boolean translucent(final BlockState state) {
            return ShapeModels.modelOf(state).getRenderTypes(state, RandomSource.create(42L), ModelData.EMPTY).contains(RenderType.translucent());
        }

        private Client() {}
    }

    private NeoRenderGlue() {}
}
