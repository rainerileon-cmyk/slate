package dev.fallingcloud.slate.building.neoforge.compat;

import dev.fallingcloud.slate.building.client.BuildingHarness;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;

/**
 * NeoForge-only glue for mods of the DF pack that need more than a tag or a reflective check: each piece is only
 * wired when its mod is loaded, and each class below is only loaded after that check.
 * <ul>
 *   <li>{@link KleeSlabsCompat}: KleeSlabs' half-slab break drops the material, never a free half.</li>
 *   <li>{@link CreateToolbeltCompat}: Create's Left-Alt toolbelt radial stays shut while our swap key holds Alt.</li>
 *   <li>{@link DfCompatHarness}: the {@code compat-*} dev-harness scenarios (only registered in harness runs).</li>
 * </ul>
 */
public final class NeoCompatGlue {

    public static void init(final IEventBus modBus) {
        if (ModList.get().isLoaded("kleeslabs")) KleeSlabsCompat.register();
    }

    public static void initClient(final IEventBus modBus) {
        if (ModList.get().isLoaded("create")) CreateToolbeltCompat.register();
        if (System.getProperty(BuildingHarness.PROPERTY) != null) DfCompatHarness.register();
    }

    private NeoCompatGlue() {}
}
