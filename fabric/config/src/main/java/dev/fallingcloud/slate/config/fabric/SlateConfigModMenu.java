package dev.fallingcloud.slate.config.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.fallingcloud.slate.core.client.CoreSettingsScreen;

/** ModMenu entry point: opens the unified Slate settings from the mod list. */
public final class SlateConfigModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // Replaced by the Config module's own hub screen once it exists; Core's page is the fallback.
        return CoreSettingsScreen::new;
    }
}
