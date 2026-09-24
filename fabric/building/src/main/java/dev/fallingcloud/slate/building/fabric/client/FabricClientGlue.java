package dev.fallingcloud.slate.building.fabric.client;

/**
 * Fabric glue for the in-world UI: HUD and other client events.
 *
 * <p>Owner: C (ui). Skeleton stub; the loader entries call both methods, owners fill them and never edit the entries.
 */
public final class FabricClientGlue {

    /** Both environments, from {@code SlateBuildingFabric.onInitialize} (after the registries were flushed). */
    public static void init() {
    }

    /**
     * Client only, from {@code SlateBuildingFabricClient.onInitializeClient}. Put client-class references in a
     * nested class so {@link #init} stays loadable on a dedicated server.
     */
    public static void initClient() {
    }

    private FabricClientGlue() {}
}
