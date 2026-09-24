package dev.fallingcloud.slate.building.neoforge.toolbox;

import dev.fallingcloud.slate.building.registry.BuildingMenus;
import dev.fallingcloud.slate.building.toolbox.ToolboxTooltip;
import dev.fallingcloud.slate.building.toolbox.client.ClientToolboxTooltip;
import dev.fallingcloud.slate.building.toolbox.client.ToolboxScreen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * NeoForge glue for the toolbox: the menu screen ({@code RegisterMenuScreensEvent}) and the toolbox tooltip picture
 * ({@code RegisterClientTooltipComponentFactoriesEvent}). Anvil repair and the netherite smithing upgrade need no
 * loader hooks (vanilla {@code isValidRepairItem} and a {@code smithing_transform} recipe).
 */
public final class NeoToolboxGlue {

    /** Both dists, from the mod constructor. Nothing loader-specific on the common side. */
    public static void init(final IEventBus modBus) {
    }

    /** Client dist only, from {@code SlateBuildingNeoForgeClient}. */
    public static void initClient(final IEventBus modBus) {
        Client.init(modBus);
    }

    /** Client classes stay behind this nested class so {@link #init} loads on a dedicated server. */
    private static final class Client {
        static void init(final IEventBus modBus) {
            modBus.addListener(RegisterMenuScreensEvent.class, e -> e.register(BuildingMenus.TOOLBOX.get(), ToolboxScreen::new));
            modBus.addListener(RegisterClientTooltipComponentFactoriesEvent.class, e -> e.register(ToolboxTooltip.class, ClientToolboxTooltip::new));
        }
    }

    private NeoToolboxGlue() {}
}
