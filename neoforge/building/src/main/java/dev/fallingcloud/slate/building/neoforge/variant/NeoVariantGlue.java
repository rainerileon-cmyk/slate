package dev.fallingcloud.slate.building.neoforge.variant;

import net.neoforged.bus.api.IEventBus;

/**
 * NeoForge glue for variants: tag reload ({@code TagsUpdatedEvent}), loot/drop hooks, creative-tab removal of native variants.
 *
 * <p>Owner: A (variants). Skeleton stub; the loader entry calls both methods, owners fill them and never edit the entry.
 */
public final class NeoVariantGlue {

    /** Both dists, from the mod constructor (mod bus events; game-bus listeners via {@code NeoForge.EVENT_BUS}). */
    public static void init(final IEventBus modBus) {
    }

    /**
     * Client dist only, from {@code SlateBuildingNeoForgeClient}. Put client-class references in a nested class
     * (e.g. {@code private static final class Client}) so {@link #init} stays loadable on a dedicated server.
     */
    public static void initClient(final IEventBus modBus) {
    }

    private NeoVariantGlue() {}
}
