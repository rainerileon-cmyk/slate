package dev.fallingcloud.slate.building.neoforge;

import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.neoforge.chisel.NeoChiselGlue;
import dev.fallingcloud.slate.building.neoforge.client.NeoClientGlue;
import dev.fallingcloud.slate.building.neoforge.ops.NeoOpsGlue;
import dev.fallingcloud.slate.building.neoforge.render.NeoRenderGlue;
import dev.fallingcloud.slate.building.neoforge.toolbox.NeoToolboxGlue;
import dev.fallingcloud.slate.building.neoforge.variant.NeoVariantGlue;
import dev.fallingcloud.slate.building.registry.BuildingRegistry;
import dev.fallingcloud.slate.core.module.Modules;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * NeoForge entry point of Slate Building (both dists: it registers content). Registers the module (which queues
 * registry entries and payloads), flushes the registry queue from {@code RegisterEvent}, and hands the mod bus to
 * each area's loader glue; client wiring lives in {@link SlateBuildingNeoForgeClient}, only loaded on the client.
 */
@Mod(SlateBuilding.MOD_ID)
public final class SlateBuildingNeoForge {

    public SlateBuildingNeoForge(final IEventBus modBus, final ModContainer container) {
        Modules.register(SlateBuilding.MODULE);
        modBus.addListener(RegisterEvent.class, SlateBuildingNeoForge::register);

        NeoVariantGlue.init(modBus);
        NeoRenderGlue.init(modBus);
        NeoClientGlue.init(modBus);
        NeoOpsGlue.init(modBus);
        NeoToolboxGlue.init(modBus);
        NeoChiselGlue.init(modBus);
        dev.fallingcloud.slate.building.neoforge.compat.NeoCompatGlue.init(modBus);

        if (FMLEnvironment.dist.isClient()) SlateBuildingNeoForgeClient.init(modBus, container);
    }

    /** One event per registry, in NeoForge's order (blocks before items before block entity types). */
    private static void register(final RegisterEvent event) {
        BuildingRegistry.close();
        for (final BuildingRegistry.Entry<?> entry : BuildingRegistry.entries(event.getRegistryKey())) register(event, entry);
    }

    private static <R> void register(final RegisterEvent event, final BuildingRegistry.Entry<R> entry) {
        event.register(entry.registry(), entry.id(), entry::create);
    }
}
