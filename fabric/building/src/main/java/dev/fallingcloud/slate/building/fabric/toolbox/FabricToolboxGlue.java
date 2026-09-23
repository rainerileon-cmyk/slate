package dev.fallingcloud.slate.building.fabric.toolbox;

/**
 * Fabric glue for the toolbox: {@code MenuScreens.register} (toolbox screen), anvil/smithing hooks.
 *
 * <p>Owner: E (toolbox). Skeleton stub; the loader entries call both methods, owners fill them and never edit the entries.
 */
public final class FabricToolboxGlue {

    /** Both environments, from {@code SlateBuildingFabric.onInitialize} (after the registries were flushed). */
    public static void init() {
    }

    /**
     * Client only, from {@code SlateBuildingFabricClient.onInitializeClient}. Put client-class references in a
     * nested class so {@link #init} stays loadable on a dedicated server.
     */
    public static void initClient() {
    }

    private FabricToolboxGlue() {}
}
