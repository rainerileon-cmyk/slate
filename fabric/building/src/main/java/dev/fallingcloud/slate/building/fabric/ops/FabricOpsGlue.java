package dev.fallingcloud.slate.building.fabric.ops;

/**
 * Fabric glue for building operations: {@code CommandRegistrationCallback} ({@code /slatebuild}), server hooks not covered by Core.
 *
 * <p>Owner: D1 (ops server). Skeleton stub; the loader entries call both methods, owners fill them and never edit the entries.
 */
public final class FabricOpsGlue {

    /** Both environments, from {@code SlateBuildingFabric.onInitialize} (after the registries were flushed). */
    public static void init() {
    }

    /**
     * Client only, from {@code SlateBuildingFabricClient.onInitializeClient}. Put client-class references in a
     * nested class so {@link #init} stays loadable on a dedicated server.
     */
    public static void initClient() {
    }

    private FabricOpsGlue() {}
}
