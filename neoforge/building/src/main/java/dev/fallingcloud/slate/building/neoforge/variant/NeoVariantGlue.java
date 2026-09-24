package dev.fallingcloud.slate.building.neoforge.variant;

import dev.fallingcloud.slate.building.variant.VariantSystem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

/**
 * NeoForge glue for variants: the variant registry is rebuilt after tags are bound ({@code TagsUpdatedEvent}, fired
 * on the server after a (re)load and on the client when the server's tags arrive). Drops, recipes and creative tabs
 * are handled by common mixins on both loaders.
 *
 * <p>Owner: A (variants).
 */
public final class NeoVariantGlue {

    /** Both dists, from the mod constructor. */
    public static void init(final IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(TagsUpdatedEvent.class, event -> VariantSystem.onTagsReloaded());
    }

    /** Client dist only: nothing loader-specific on the client side. */
    public static void initClient(final IEventBus modBus) {
    }

    private NeoVariantGlue() {}
}
