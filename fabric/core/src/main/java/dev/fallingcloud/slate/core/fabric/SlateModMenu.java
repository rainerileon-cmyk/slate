package dev.fallingcloud.slate.core.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.fallingcloud.slate.core.client.CoreSettingsScreen;

/** ModMenu entry point: the Slate settings screen from the mod list. */
public final class SlateModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return CoreSettingsScreen::new;
    }
}
