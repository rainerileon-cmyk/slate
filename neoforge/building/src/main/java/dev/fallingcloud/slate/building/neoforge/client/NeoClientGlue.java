package dev.fallingcloud.slate.building.neoforge.client;

import net.neoforged.bus.api.IEventBus;

/**
 * NeoForge glue for the in-world UI: GUI layers, pick-block and other client events.
 *
 * <p>Owner: C (ui). Skeleton stub; the loader entry calls both methods, owners fill them and never edit the entry.
 */
public final class NeoClientGlue {

    /** Both dists, from the mod constructor (mod bus events; game-bus listeners via {@code NeoForge.EVENT_BUS}). */
    public static void init(final IEventBus modBus) {
    }

    /**
     * Client dist only, from {@code SlateBuildingNeoForgeClient}. Put client-class references in a nested class
     * (e.g. {@code private static final class Client}) so {@link #init} stays loadable on a dedicated server.
     */
    public static void initClient(final IEventBus modBus) {
    }

    private NeoClientGlue() {}
}
