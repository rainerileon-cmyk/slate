package dev.fallingcloud.slate.building.registry;

import dev.fallingcloud.slate.building.toolbox.ToolboxMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

/**
 * Menu types. {@link #TOOLBOX} uses the vanilla {@code MenuType} constructor (public on both loaders); its screen is
 * registered in each loader's toolbox glue (NeoForge {@code RegisterMenuScreensEvent}, Fabric {@code MenuScreens}).
 */
public final class BuildingMenus {

    public static final RegistryRef<MenuType<ToolboxMenu>> TOOLBOX = BuildingRegistry.register(Registries.MENU, "toolbox",
        () -> new MenuType<>(ToolboxMenu::new, FeatureFlags.VANILLA_SET));

    /** Forces class initialisation (queues the entries). Called by {@link BuildingRegistry#bootstrap()}. */
    static void init() {}

    private BuildingMenus() {}
}
