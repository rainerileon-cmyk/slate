package dev.fallingcloud.slate.core.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Lets the screenshot harness put the pointer somewhere without touching the real one: hover states and tooltips
 * can then be captured while the window sits in the background.
 */
@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {

    @Accessor("xpos")
    void slate$setXpos(double x);

    @Accessor("ypos")
    void slate$setYpos(double y);
}
