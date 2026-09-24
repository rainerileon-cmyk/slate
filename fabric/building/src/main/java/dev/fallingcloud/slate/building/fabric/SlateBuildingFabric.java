package dev.fallingcloud.slate.building.fabric;

import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.fabric.chisel.FabricChiselGlue;
import dev.fallingcloud.slate.building.fabric.client.FabricClientGlue;
import dev.fallingcloud.slate.building.fabric.ops.FabricOpsGlue;
import dev.fallingcloud.slate.building.fabric.render.FabricRenderGlue;
import dev.fallingcloud.slate.building.fabric.toolbox.FabricToolboxGlue;
import dev.fallingcloud.slate.building.fabric.variant.FabricVariantGlue;
import dev.fallingcloud.slate.building.registry.BuildingRegistry;
import dev.fallingcloud.slate.core.module.Modules;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;

/**
 * Fabric entry point (both environments) of Slate Building. Registers the module (on a client this also runs its
 * client init, since {@code Modules.register} does), then registers every queued registry entry right away with
 * {@code Registry.register} - Fabric allows it during init - in dependency order, then calls each area's glue.
 */
public final class SlateBuildingFabric implements ModInitializer {

    /** Registries flushed first, in this order; everything else follows in insertion order. */
    private static final List<ResourceKey<? extends Registry<?>>> ORDER = List.of(
        Registries.BLOCK, Registries.ITEM, Registries.BLOCK_ENTITY_TYPE, Registries.DATA_COMPONENT_TYPE,
        Registries.MENU, Registries.CREATIVE_MODE_TAB);

    @Override
    public void onInitialize() {
        Modules.register(SlateBuilding.MODULE);
        flushRegistries();

        FabricVariantGlue.init();
        FabricRenderGlue.init();
        FabricClientGlue.init();
        FabricOpsGlue.init();
        FabricToolboxGlue.init();
        FabricChiselGlue.init();
    }

    private static void flushRegistries() {
        BuildingRegistry.close();
        final Set<BuildingRegistry.Entry<?>> done = Collections.newSetFromMap(new IdentityHashMap<>());
        for (final ResourceKey<? extends Registry<?>> key : ORDER) {
            for (final BuildingRegistry.Entry<?> entry : BuildingRegistry.entries(key)) {
                register(entry);
                done.add(entry);
            }
        }
        for (final BuildingRegistry.Entry<?> entry : BuildingRegistry.entries()) {
            if (done.add(entry)) register(entry);
        }
    }

    @SuppressWarnings("unchecked")
    private static <R> void register(final BuildingRegistry.Entry<R> entry) {
        final Registry<R> registry = (Registry<R>) BuiltInRegistries.REGISTRY.get(entry.registry().location());
        if (registry == null) throw new IllegalStateException("Unknown registry " + entry.registry().location() + " for " + entry.id());
        Registry.register(registry, entry.id(), entry.create());
    }
}
