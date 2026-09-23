package dev.fallingcloud.slate.building.registry;

import dev.fallingcloud.slate.building.platform.BuildingPlatform;
import dev.fallingcloud.slate.building.toolbox.ToolboxCreativeTab;
import dev.fallingcloud.slate.building.variant.VariantCreativeTab;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/**
 * The "Slate Building" creative tab ({@code slate_building:building}). Its contents come from the owners:
 * {@link ToolboxCreativeTab#fill} (toolbox, tools, upgrades; E) then {@link VariantCreativeTab#fill} (showcase
 * shape stacks; A).
 */
public final class BuildingTabs {

    public static final RegistryRef<CreativeModeTab> BUILDING = BuildingRegistry.register(Registries.CREATIVE_MODE_TAB, "building",
        () -> BuildingPlatform.get().creativeTabBuilder()
            .title(Component.translatable("itemGroup.slate_building"))
            .icon(() -> new ItemStack(BuildingItems.TOOLBOX.get()))
            .displayItems((params, output) -> {
                ToolboxCreativeTab.fill(params, output);
                VariantCreativeTab.fill(params, output);
            })
            .build());

    /** Forces class initialisation (queues the entries). Called by {@link BuildingRegistry#bootstrap()}. */
    static void init() {}

    private BuildingTabs() {}
}
