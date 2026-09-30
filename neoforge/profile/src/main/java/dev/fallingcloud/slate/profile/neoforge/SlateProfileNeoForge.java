package dev.fallingcloud.slate.profile.neoforge;

import dev.fallingcloud.slate.core.module.Modules;
import dev.fallingcloud.slate.profile.SlateProfile;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/** NeoForge entry point of Slate Profile (both sides: a server passes looks on between its players). */
@Mod(value = SlateProfile.MOD_ID)
public final class SlateProfileNeoForge {

    public SlateProfileNeoForge(final IEventBus modBus, final ModContainer container) {
        Modules.register(SlateProfile.MODULE);
    }
}
