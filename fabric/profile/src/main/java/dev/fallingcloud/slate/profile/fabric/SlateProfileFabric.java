package dev.fallingcloud.slate.profile.fabric;

import dev.fallingcloud.slate.core.module.Modules;
import dev.fallingcloud.slate.profile.SlateProfile;
import net.fabricmc.api.ModInitializer;

/** Fabric entry point (both sides) of Slate Profile. */
public final class SlateProfileFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        Modules.register(SlateProfile.MODULE);
    }
}
