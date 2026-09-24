package dev.fallingcloud.slate.building.registry;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;

/**
 * Slate Building's item data components. Set them on stacks at runtime only, never in {@code Item.Properties}.
 * Owners that need more components (toolbox link, clipboard, ...) declare them in their own classes with
 * {@link BuildingRegistry#register} during init, the same way.
 */
public final class BuildingComponents {

    /**
     * {@code slate_building:material}: the material of a shape item, as the material block's id (not a
     * {@code Holder}, so the value is valid before tags/registries are bound and survives missing mods as a plain
     * id). Persistent and synced.
     */
    public static final RegistryRef<DataComponentType<ResourceLocation>> MATERIAL = BuildingRegistry.register(Registries.DATA_COMPONENT_TYPE, "material",
        () -> DataComponentType.<ResourceLocation>builder()
            .persistent(ResourceLocation.CODEC)
            .networkSynchronized(ResourceLocation.STREAM_CODEC)
            .build());

    /** Forces class initialisation (queues the entries). Called by {@link BuildingRegistry#bootstrap()}. */
    static void init() {}

    private BuildingComponents() {}
}
