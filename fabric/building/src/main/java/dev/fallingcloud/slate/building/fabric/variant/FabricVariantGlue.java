package dev.fallingcloud.slate.building.fabric.variant;

import dev.fallingcloud.slate.building.variant.VariantSystem;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;

/**
 * Fabric glue for variants: the variant registry is rebuilt after tags are bound
 * ({@code CommonLifecycleEvents.TAGS_LOADED}, on the server after a (re)load and on the client when the server's tags
 * arrive). Drops, recipes and creative tabs are handled by common mixins on both loaders.
 *
 * <p>Owner: A (variants).
 */
public final class FabricVariantGlue {

    /** Both environments, from {@code SlateBuildingFabric.onInitialize}. */
    public static void init() {
        CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> VariantSystem.onTagsReloaded());
    }

    /** Client only: nothing loader-specific on the client side. */
    public static void initClient() {
    }

    private FabricVariantGlue() {}
}
