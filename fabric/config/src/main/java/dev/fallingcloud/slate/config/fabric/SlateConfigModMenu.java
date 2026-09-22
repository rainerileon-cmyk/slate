package dev.fallingcloud.slate.config.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.fallingcloud.slate.config.SlateConfigApi;

/** ModMenu entry point: the unified Slate settings hub from the mod list. */
public final class SlateConfigModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> SlateConfigApi.hub(parent, null);
    }
}
