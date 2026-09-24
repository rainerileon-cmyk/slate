package dev.fallingcloud.slate.building.fabric.toolbox;

import dev.fallingcloud.slate.building.registry.BuildingMenus;
import dev.fallingcloud.slate.building.toolbox.ToolboxTooltip;
import dev.fallingcloud.slate.building.toolbox.client.ClientToolboxTooltip;
import dev.fallingcloud.slate.building.toolbox.client.ToolboxScreen;
import net.fabricmc.fabric.api.client.rendering.v1.TooltipComponentCallback;
import net.minecraft.client.gui.screens.MenuScreens;

/**
 * Fabric glue for the toolbox: the menu screen ({@code MenuScreens.register}, public through Fabric's transitive
 * access wideners) and the toolbox tooltip picture ({@code TooltipComponentCallback}). Anvil repair and the netherite
 * smithing upgrade need no loader hooks.
 */
public final class FabricToolboxGlue {

    /** Both environments, from {@code SlateBuildingFabric.onInitialize}. Nothing loader-specific on the common side. */
    public static void init() {
    }

    /** Client only, from {@code SlateBuildingFabricClient.onInitializeClient} (registries are flushed by then). */
    public static void initClient() {
        Client.init();
    }

    /** Client classes stay behind this nested class so {@link #init} loads on a dedicated server. */
    private static final class Client {
        static void init() {
            MenuScreens.register(BuildingMenus.TOOLBOX.get(), ToolboxScreen::new);
            TooltipComponentCallback.EVENT.register(data -> data instanceof ToolboxTooltip t ? new ClientToolboxTooltip(t) : null);
        }
    }

    private FabricToolboxGlue() {}
}
