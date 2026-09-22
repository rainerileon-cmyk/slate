package dev.fallingcloud.slate.menu.neoforge;

import dev.fallingcloud.slate.menu.SlateMenu;
import dev.fallingcloud.slate.core.module.Modules;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/** NeoForge entry point of Slate Menu. */
@Mod(value = SlateMenu.MOD_ID, dist = Dist.CLIENT)
public final class SlateMenuNeoForge {

    public SlateMenuNeoForge(final IEventBus modBus, final ModContainer container) {
        Modules.register(SlateMenu.MODULE);
    }
}
