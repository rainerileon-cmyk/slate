package dev.fallingcloud.slate.building.fabric;

import dev.fallingcloud.slate.building.fabric.chisel.FabricChiselGlue;
import dev.fallingcloud.slate.building.fabric.client.FabricClientGlue;
import dev.fallingcloud.slate.building.fabric.ops.FabricOpsGlue;
import dev.fallingcloud.slate.building.fabric.render.FabricRenderGlue;
import dev.fallingcloud.slate.building.fabric.toolbox.FabricToolboxGlue;
import dev.fallingcloud.slate.building.fabric.variant.FabricVariantGlue;
import net.fabricmc.api.ClientModInitializer;

/**
 * Fabric client entry point of Slate Building. The module's client init already ran from the main entrypoint
 * ({@code Modules.register}); this only adds each area's Fabric-specific client glue (models, colours, shaders,
 * screens). All main entrypoints, Core's included, have run by now, but Core's CLIENT entry may not have.
 */
public final class SlateBuildingFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        FabricVariantGlue.initClient();
        FabricRenderGlue.initClient();
        FabricClientGlue.initClient();
        FabricOpsGlue.initClient();
        FabricToolboxGlue.initClient();
        FabricChiselGlue.initClient();
    }
}
