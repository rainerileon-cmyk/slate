package dev.fallingcloud.slate.core.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The container's image rectangle, for {@code ContainerReskin} to recognise the background blits (vanilla has no getters on Fabric). */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {

    @Accessor("imageWidth")
    int slate$imageWidth();

    @Accessor("imageHeight")
    int slate$imageHeight();

    @Accessor("leftPos")
    int slate$leftPos();

    @Accessor("topPos")
    int slate$topPos();
}
