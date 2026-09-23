package dev.fallingcloud.slate.building.fabric.variant;

/**
 * Fabric glue for variants: tag reload ({@code CommonLifecycleEvents.TAGS_LOADED}), loot hooks, {@code ItemGroupEvents} removal of native variants.
 *
 * <p>Owner: A (variants). Skeleton stub; the loader entries call both methods, owners fill them and never edit the entries.
 */
public final class FabricVariantGlue {

    /** Both environments, from {@code SlateBuildingFabric.onInitialize} (after the registries were flushed). */
    public static void init() {
    }

    /**
     * Client only, from {@code SlateBuildingFabricClient.onInitializeClient}. Put client-class references in a
     * nested class so {@link #init} stays loadable on a dedicated server.
     */
    public static void initClient() {
    }

    private FabricVariantGlue() {}
}
