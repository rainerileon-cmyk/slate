package dev.fallingcloud.slate.building.neoforge;

import dev.fallingcloud.slate.building.client.BuildingClient;
import dev.fallingcloud.slate.building.neoforge.chisel.NeoChiselGlue;
import dev.fallingcloud.slate.building.neoforge.client.NeoClientGlue;
import dev.fallingcloud.slate.building.neoforge.ops.NeoOpsGlue;
import dev.fallingcloud.slate.building.neoforge.render.NeoRenderGlue;
import dev.fallingcloud.slate.building.neoforge.toolbox.NeoToolboxGlue;
import dev.fallingcloud.slate.building.neoforge.variant.NeoVariantGlue;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Client-side NeoForge wiring; only loaded on the client dist. The module's own client init already ran from
 * {@code Modules.register}; this adds each area's loader-specific client glue (model baking, colour handlers,
 * shaders, menu screens, ...) and the mod list "Config" button (when Slate Config is installed).
 */
final class SlateBuildingNeoForgeClient {

    static void init(final IEventBus modBus, final ModContainer container) {
        NeoVariantGlue.initClient(modBus);
        NeoRenderGlue.initClient(modBus);
        NeoClientGlue.initClient(modBus);
        NeoOpsGlue.initClient(modBus);
        NeoToolboxGlue.initClient(modBus);
        NeoChiselGlue.initClient(modBus);

        if (ModList.get().isLoaded("slate_config")) {
            container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> BuildingClient.settingsScreen(parent));
        }
    }

    private SlateBuildingNeoForgeClient() {}
}
