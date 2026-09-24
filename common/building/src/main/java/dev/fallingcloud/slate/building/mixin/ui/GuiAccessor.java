package dev.fallingcloud.slate.building.mixin.ui;

import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Lets the quick-swap wheel hide vanilla's "selected item name" above the hotbar while it is open (its label pill
 * sits in the same place). Setting the timer to 0 only ends the current display; vanilla starts it again when the
 * held stack changes, so the name of the swapped stack still shows once the wheel closes. The field is private
 * with the same name on NeoForge and Fabric (javap-checked).
 */
@Mixin(Gui.class)
public interface GuiAccessor {

    @Accessor("toolHighlightTimer")
    void slateBuilding$setToolHighlightTimer(int ticks);
}
