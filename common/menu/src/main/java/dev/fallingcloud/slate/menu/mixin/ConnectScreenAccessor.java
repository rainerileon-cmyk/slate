package dev.fallingcloud.slate.menu.mixin;

import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The private constructor, for the dev harness's loading-screen preview (a connect screen that connects nowhere). */
@Mixin(ConnectScreen.class)
public interface ConnectScreenAccessor {

    @Invoker("<init>")
    static ConnectScreen slate$create(final Screen parent, final Component connectFailedTitle) {
        throw new AssertionError();
    }
}
