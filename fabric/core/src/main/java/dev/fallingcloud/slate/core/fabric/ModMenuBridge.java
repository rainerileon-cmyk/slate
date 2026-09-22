package dev.fallingcloud.slate.core.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.minecraft.client.gui.screens.Screen;

/** The only class that references ModMenu's API; loaded only when modmenu is present. */
final class ModMenuBridge {

    @SuppressWarnings({ "unchecked", "rawtypes" })
    static Optional<Function<Screen, Screen>> factoryFor(final String modId) {
        for (final EntrypointContainer<ModMenuApi> c : FabricLoader.getInstance().getEntrypointContainers("modmenu", ModMenuApi.class)) {
            try {
                final ModMenuApi api = c.getEntrypoint();
                if (c.getProvider().getMetadata().getId().equals(modId)) {
                    final ConfigScreenFactory<?> f = api.getModConfigScreenFactory();
                    if (f != null) return Optional.of(parent -> (Screen) ((ConfigScreenFactory) f).create(parent));
                }
                final Map<String, ConfigScreenFactory<?>> provided = api.getProvidedConfigScreenFactories();
                if (provided != null && provided.containsKey(modId)) {
                    final ConfigScreenFactory<?> f = provided.get(modId);
                    return Optional.of(parent -> (Screen) ((ConfigScreenFactory) f).create(parent));
                }
            } catch (final Throwable ignored) {}
        }
        return Optional.empty();
    }

    private ModMenuBridge() {}
}
