package dev.fallingcloud.slate.building.neoforge.chisel;

import dev.fallingcloud.slate.building.chisel.ChiselSystem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DataMapHooks;

/**
 * NeoForge glue for chisel groups. Rebuild, reload and sync are common code (Core events + the
 * {@code PlayerList.reloadResources} mixin); the one loader-specific piece is copper: NeoForge keeps modded
 * waxed/unwaxed pairs in its waxables data map, which the vanilla {@code HoneycombItem.WAX_OFF_BY_BLOCK} map does not
 * see, so the copper rule asks {@code DataMapHooks} (oxidation stages already go through NeoForge's patched
 * {@code WeatheringCopper.getPrevious}).
 *
 * <p>Owner: I (chisel).
 */
public final class NeoChiselGlue {

    /** Both dists, from the mod constructor. */
    public static void init(final IEventBus modBus) {
        ChiselSystem.setUnwaxLookup(DataMapHooks::getBlockUnwaxed);
    }

    /** Client dist only: nothing loader-specific on the client (the synced index arrives through Core's network). */
    public static void initClient(final IEventBus modBus) {
    }

    private NeoChiselGlue() {}
}
