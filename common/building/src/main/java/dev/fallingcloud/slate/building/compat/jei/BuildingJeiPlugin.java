package dev.fallingcloud.slate.building.compat.jei;

import dev.fallingcloud.slate.building.SlateBuilding;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import net.minecraft.resources.ResourceLocation;

/**
 * JEI integration (optional): hides deleted native variant items when {@code deleteNativeVariants} is on. JEI finds it
 * itself (NeoForge: the {@link JeiPlugin} annotation; Fabric: the {@code jei_mod_plugin} entrypoint in
 * {@code fabric.mod.json}), so nothing else ever loads this class and it is harmless without JEI.
 *
 * <p>Owner: A (variants). Skeleton stub: registered with JEI, does nothing yet.
 */
@JeiPlugin
public final class BuildingJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID = SlateBuilding.id("jei");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }
}
