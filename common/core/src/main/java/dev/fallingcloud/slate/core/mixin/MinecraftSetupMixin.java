package dev.fallingcloud.slate.core.mixin;

import dev.fallingcloud.slate.core.client.setup.SlateSetupScreen;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Puts Slate's first-launch setup at the head of the start-up screen chain while it has not been confirmed: the
 * chain shows its entries in list order, each handing the next a continuation, so the setup appears before
 * vanilla's own first-launch screens and before the title screen. Nothing is added once {@code setupDone} is set.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftSetupMixin {

    @Inject(method = "addInitialScreens", at = @At("HEAD"))
    private void slate$setupScreen(final List<Function<Runnable, Screen>> output, final CallbackInfo ci) {
        if (SlateSetupScreen.wanted()) output.add(next -> new SlateSetupScreen(null, next));
    }
}
