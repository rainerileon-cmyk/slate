package dev.fallingcloud.slate.building.mixin.variant;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Clearing {@code CreativeModeTabs.CACHED_PARAMETERS} makes the next {@code tryRebuildTabContents} (run whenever the
 * creative inventory opens) rebuild every tab, so a changed {@code deleteNativeVariants} applies without relogging.
 */
@Mixin(CreativeModeTabs.class)
public interface CreativeModeTabsAccessor {

    @Accessor("CACHED_PARAMETERS")
    static void slateBuilding$setCachedParameters(final @Nullable CreativeModeTab.ItemDisplayParameters parameters) {
        throw new AssertionError("mixin accessor");
    }
}
