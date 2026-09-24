package dev.fallingcloud.slate.building.fabric.chisel;

/**
 * Fabric glue for chisel groups: nothing loader-specific is needed. Rebuild, reload and sync are common code (Core
 * events + the {@code PlayerList.reloadResources} mixin), and Fabric API's {@code OxidizableBlocksRegistry} registers
 * modded copper into the vanilla weathering / waxing maps the copper rule already reads.
 *
 * <p>Owner: I (chisel).
 */
public final class FabricChiselGlue {

    /** Both environments, from {@code SlateBuildingFabric.onInitialize}. */
    public static void init() {
    }

    /** Client only, from {@code SlateBuildingFabricClient.onInitializeClient}. */
    public static void initClient() {
    }

    private FabricChiselGlue() {}
}
