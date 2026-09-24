package dev.fallingcloud.slate.menu.mixin;

import dev.fallingcloud.slate.menu.SlateMenu;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips vanilla's first-launch accessibility screen ({@code skipOnboarding} in menu.json). Clearing the flag
 * before the startup chain is built drops just that screen; the rest of the chain (ban notices, quick play)
 * runs as usual. A screen swap cannot do this: the onboarding screen holds the chain's continuation.
 * The flag reaches options.txt with the next options save, as if the player had clicked through.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftOnboardingMixin {

    @Shadow @Final public Options options;

    @Inject(method = "addInitialScreens", at = @At("HEAD"))
    private void slate_menu$skipOnboarding(final List<Function<Runnable, Screen>> output, final CallbackInfo ci) {
        if (SlateMenu.config().skipOnboarding) this.options.onboardAccessibility = false;
    }
}
