package dev.fallingcloud.slate.menu.mixin;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.world.level.WorldDataConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Slate's world creation screens are another face of vanilla's: the state, the data pack handling and the creating
 * itself stay vanilla's own, reached through here, so everything vanilla checks on the way is still checked.
 */
@Mixin(CreateWorldScreen.class)
public interface CreateWorldScreenAccess {

    @Accessor("lastScreen")
    Screen slate$lastScreen();

    @Invoker("onCreate")
    void slate$create();

    @Invoker("openExperimentsScreen")
    void slate$openExperiments(WorldDataConfiguration configuration);

    @Invoker("openDataPackSelectionScreen")
    void slate$openDataPacks(WorldDataConfiguration configuration);
}
